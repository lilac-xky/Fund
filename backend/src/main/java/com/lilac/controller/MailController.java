package com.lilac.controller;

import com.lilac.domain.result.Result;
import com.lilac.domain.vo.MailPreviewVO;
import com.lilac.domain.vo.MailStatusVO;
import com.lilac.mail.SmtpSettingsResolver;
import com.lilac.mail.CloseReminderMailService;
import com.lilac.mail.MailContent;
import com.lilac.mail.MailContentBuilder;
import com.lilac.mail.MailRenderer;
import com.lilac.service.EstimateService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 邮件接口，提供通道状态、内容预览与手动补发，任何返回都不含授权码
 */
@RestController
@RequestMapping("/mail")
public class MailController {

    @Resource
    private CloseReminderMailService closeReminderMailService;

    @Resource
    private SmtpSettingsResolver smtpSettingsResolver;

    @Resource
    private MailContentBuilder mailContentBuilder;

    @Resource
    private MailRenderer mailRenderer;

    @Resource
    private EstimateService estimateService;

    /**
     * 查询邮件通道是否就绪，只回「已配置 / 未配置」，不回显授权码
     */
    @GetMapping("/status")
    public Result<MailStatusVO> status() {
        MailStatusVO vo = new MailStatusVO();
        SmtpSettingsResolver.SmtpSettings settings = smtpSettingsResolver.resolve();
        vo.setReady(settings.complete());
        vo.setNote(settings.complete() ? "当前配置完整，可以测试连接" : String.join("；", settings.getMissing()));
        vo.setTestable(settings.complete());
        return Result.success(vo);
    }

    /**
     * 预览邮件正文，便于在发信前确认排版与内容
     */
    @GetMapping("/preview")
    public Result<MailPreviewVO> preview(
            @RequestParam(value = "tradeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        MailContent content = mailContentBuilder.build(date, estimateService.estimateBatch(null));
        MailPreviewVO vo = new MailPreviewVO();
        vo.setSubject(mailRenderer.subject(content, false));
        vo.setHtml(mailRenderer.html(content));
        vo.setText(mailRenderer.text(content));
        return Result.success(vo);
    }

    /**
     * 手动补发收盘前提醒，用于改好 SMTP 口令后把未送达内容补出去
     */
    @PostMapping("/resend")
    public Result<String> resend(
            @RequestParam(value = "tradeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        return Result.success(closeReminderMailService.send(date, true));
    }
}
