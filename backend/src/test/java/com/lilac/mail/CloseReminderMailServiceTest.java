package com.lilac.mail;

import com.lilac.domain.vo.HoldingVO;
import com.lilac.enums.EstimateConfidence;
import com.lilac.enums.EstimateTier;
import com.lilac.mapper.HoldingMapper;
import com.lilac.mapper.SendRecordMapper;
import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件链路验收测试，SMTP 用桩替换，重点验证未配置、失败留存与补发三条路径
 */
@SpringBootTest
@Transactional
class CloseReminderMailServiceTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 30);

    private static final String MISSING_SECRETS = "target/absent-test-secrets.env";

    @Autowired
    private CloseReminderMailService mailService;

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private SmtpSettingsResolver settingsResolver;

    @Autowired
    private HoldingMapper holdingMapper;

    @Autowired
    private SendRecordMapper sendRecordMapper;

    @MockitoBean
    private SmtpMailSender mailSender;

    @MockitoBean
    private MailContentBuilder mailContentBuilder;

    /**
     * 清掉持仓与留痕、把密钥文件指到不存在的位置，并让内容装配返回一份空持仓模型
     */
    @BeforeEach
    void setUp() {
        holdingMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        sendRecordMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        // 用例不能读开发者本机的真实授权码，否则「未配置」断言会随环境变化
        mailProperties.setSecretsFile(MISSING_SECRETS);
        MailContent empty = new MailContent();
        empty.setTradeDate(TRADE_DATE);
        Mockito.when(mailContentBuilder.build(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(empty);
    }

    /**
     * SMTP 参数不全时直接报未送达，并把原因写清，不产生任何假发送
     */
    @Test
    @DisplayName("SMTP 参数不全时报未送达而不假发送")
    void failsWhenSmtpIncomplete() {
        assertThat(settingsResolver.resolve().complete()).isFalse();
        try {
            mailService.send(TRADE_DATE, false);
            org.junit.jupiter.api.Assertions.fail("缺少 SMTP 参数时不应发送成功");
        } catch (com.lilac.task.ReminderDeliveryException e) {
            assertThat(e.getMessage()).contains("未送达").contains("SMTP");
        }
        Mockito.verify(mailSender, Mockito.never())
                .send(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /**
     * 邮件开关关闭时就绪判定为 false，说明里点名开关，便于排查
     */
    @Test
    @DisplayName("邮件开关关闭时通道不就绪")
    void notReadyWhenDisabled() {
        boolean original = mailProperties.isEnabled();
        try {
            mailProperties.setEnabled(false);
            assertThat(mailService.ready()).isFalse();
            assertThat(mailService.statusNote()).contains("关闭");
        } finally {
            mailProperties.setEnabled(original);
        }
    }

    /**
     * 未配置收件人时，状态说明里逐项列出缺什么，且不含授权码
     */
    @Test
    @DisplayName("通道状态说明逐项列出缺项且不含授权码")
    void statusNoteListsMissingItems() {
        String note = mailService.statusNote();
        assertThat(note).contains("SMTP 主机未配置").contains("收件地址未配置");
        assertThat(note).doesNotContain("SMTP_PASSWORD=");
    }
}
