-- 业务表结构，启动时由 spring.sql.init 执行，全部使用 IF NOT EXISTS 保证可重复执行
-- 字段名统一使用驼峰，与实体属性一一对应（实体上不写 @Column，靠 @Table(camelToUnderline = false) 关闭下划线转换）
-- id 用 AUTO_INCREMENT 兜底手工插入，应用侧仍由雪花算法生成；时间列用数据库默认值兜底，应用侧由监听器填充

CREATE TABLE IF NOT EXISTS variety (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    varietyKey     VARCHAR(32)  NOT NULL COMMENT '业务主键，品种类型:代码',
    varietyType    VARCHAR(8)   NOT NULL COMMENT '品种类型 OF / ETF / IDX',
    varietyCode    VARCHAR(16)  NOT NULL COMMENT '6 位代码',
    varietyName    VARCHAR(128) NOT NULL COMMENT '标的名称',
    category       VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '基金类型，六类之一',
    trackIndexCode VARCHAR(16)           DEFAULT NULL COMMENT '跟踪指数代码',
    confirmed      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '类型是否已人工确认',
    createTime     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukVarietyKey (varietyKey),
    KEY idxVarietyCode (varietyCode)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='品种注册表，以品种类型:代码唯一标识一个标的';

CREATE TABLE IF NOT EXISTS holding (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    varietyKey  VARCHAR(32)    NOT NULL COMMENT '所持品种的业务主键',
    shares      DECIMAL(20, 4) NOT NULL COMMENT '持有份额',
    costPrice   DECIMAL(20, 4) NOT NULL COMMENT '成本价',
    openDate    DATE           NOT NULL COMMENT '建仓日期',
    status      VARCHAR(16)    NOT NULL COMMENT '持仓状态 HOLDING / CLOSED',
    deleted     INT            NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0 正常 1 已删除',
    createTime  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idxHoldingVarietyKey (varietyKey),
    KEY idxHoldingStatus (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='持仓记录，一条记录对应一个品种的一次建仓';

CREATE TABLE IF NOT EXISTS strategy_param (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    paramGroup  VARCHAR(16)  NOT NULL COMMENT '参数分组 DIVIDEND / GRID / INDICATOR / ALERT',
    paramKey    VARCHAR(64)  NOT NULL COMMENT '参数名，组内唯一',
    paramValue  VARCHAR(512) NOT NULL COMMENT '参数值，统一按字符串存放',
    paramDesc   VARCHAR(255)          DEFAULT NULL COMMENT '参数说明',
    createTime  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukParamGroupKey (paramGroup, paramKey)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='策略参数，按分组加参数名存放，值统一为字符串';

CREATE TABLE IF NOT EXISTS estimate_error (
    id                    BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    varietyKey            VARCHAR(32)    NOT NULL COMMENT '对应品种的业务主键',
    tradeDate             DATE           NOT NULL COMMENT '交易日',
    estimatedChange       DECIMAL(12, 6)          DEFAULT NULL COMMENT '预估涨跌幅，单位百分点；混合型只给方向与区间时为 null',
    actualChange          DECIMAL(12, 6) NOT NULL COMMENT '实际涨跌幅，单位百分点',
    errorValue            DECIMAL(12, 6)          DEFAULT NULL COMMENT '误差，预估减实际，单位百分点；无点估计时为 null',
    overThreshold         TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否超过该类型阈值，混合型为方向未命中或落在区间外',
    thresholdValue        DECIMAL(12, 6)          DEFAULT NULL COMMENT '本次判定所用阈值，百分点；混合型为 null',
    directionHit          TINYINT(1)              DEFAULT NULL COMMENT '方向是否命中，混合型统计用',
    inInterval            TINYINT(1)              DEFAULT NULL COMMENT '实际值是否落在所给区间内，混合型统计用',
    intervalWidth         DECIMAL(12, 6)          DEFAULT NULL COMMENT '所给区间宽度，百分点，混合型统计用',
    disclosurePeriod      VARCHAR(16)             DEFAULT NULL COMMENT '重仓股披露期，如 2026Q2',
    holdingCutoffDate     DATE                    DEFAULT NULL COMMENT '当时持仓截止日',
    holdingDisclosureDate DATE                    DEFAULT NULL COMMENT '当时持仓披露日',
    outputTier            VARCHAR(4)              DEFAULT NULL COMMENT '当时输出档位 A / B / C',
    estimateSource        VARCHAR(16)             DEFAULT NULL COMMENT '预估来源 SNAPSHOT / RECOMPUTED',
    createTime            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukErrorVarietyDate (varietyKey, tradeDate),
    KEY idxErrorTradeDate (tradeDate)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='误差历史，每个交易日预估与实际净值的对照结果';

CREATE TABLE IF NOT EXISTS estimate_snapshot (
    id                    BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    varietyKey            VARCHAR(32)    NOT NULL COMMENT '对应品种的业务主键',
    tradeDate             DATE           NOT NULL COMMENT '预估所属交易日',
    category              VARCHAR(16)             DEFAULT NULL COMMENT '当时基金类型',
    branch                VARCHAR(24)             DEFAULT NULL COMMENT '当时走的预估分支',
    outputTier            VARCHAR(4)              DEFAULT NULL COMMENT '当时输出档位 A / B / C',
    estimatedChange       DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时输出的点估计，百分点；B、C 档为 null',
    direction             VARCHAR(8)              DEFAULT NULL COMMENT '当时输出的方向 UP / DOWN / FLAT',
    intervalLow           DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时区间下限，百分点',
    intervalHigh          DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时区间上限，百分点',
    coverage              DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时持仓覆盖率，百分点',
    evaluable             TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '当时是否给出了可对照输出',
    unavailableReason     VARCHAR(255)            DEFAULT NULL COMMENT '当时不可预估的原因',
    holdingPeriod         VARCHAR(16)             DEFAULT NULL COMMENT '当时持仓报告期',
    holdingCutoffDate     DATE                    DEFAULT NULL COMMENT '当时持仓截止日',
    holdingDisclosureDate DATE                    DEFAULT NULL COMMENT '当时持仓披露日',
    beta                  DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时黄金 β',
    rho                   DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时黄金 AR(1) 系数',
    residualP90           DECIMAL(12, 6)          DEFAULT NULL COMMENT '当时黄金残差 P90，百分点',
    quoteDate             DATE                    DEFAULT NULL COMMENT '当时行情数据日期',
    dataCutoffDate        DATE                    DEFAULT NULL COMMENT '当时数据截止日期',
    createTime            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukSnapshotVarietyDate (varietyKey, tradeDate),
    KEY idxSnapshotTradeDate (tradeDate)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='当日预估留存，供晚间校准与误差追溯，不可从上游重建';

CREATE TABLE IF NOT EXISTS estimate_gold_beta (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    varietyKey  VARCHAR(32)    NOT NULL COMMENT '对应品种的业务主键',
    tradeDate   DATE           NOT NULL COMMENT '记录日，与校准日一致',
    beta        DECIMAL(12, 6) NOT NULL COMMENT '对黄金 ETF 的滚动 β',
    alpha       DECIMAL(12, 6)          DEFAULT NULL COMMENT '回归截距，百分点',
    rho         DECIMAL(12, 6)          DEFAULT NULL COMMENT 'AR(1) 误差系数 ρ',
    residualP90 DECIMAL(12, 6)          DEFAULT NULL COMMENT '回归残差绝对值的 P90，百分点',
    samples     INT                     DEFAULT NULL COMMENT '参与估计的配对样本数',
    fallback    TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否因样本不足退化为代理直取',
    createTime  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukGoldBetaVarietyDate (varietyKey, tradeDate),
    KEY idxGoldBetaTradeDate (tradeDate)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='黄金 β 记录，供 β+AR(1) 估计复用与交叉校验';

CREATE TABLE IF NOT EXISTS send_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    sendDate    DATE         NOT NULL COMMENT '发送日期',
    sendPoint   VARCHAR(16)  NOT NULL COMMENT '发送时点 MORNING / NOON / PRE_CLOSE / EVENING',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '发送状态 PENDING / SENT / FAILED / RESENT',
    sentTime    DATETIME              DEFAULT NULL COMMENT '实际发送时间',
    failReason  VARCHAR(512)          DEFAULT NULL COMMENT '失败原因',
    contentPath VARCHAR(255)          DEFAULT NULL COMMENT '邮件内容留存路径，相对 data 目录',
    createTime  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukSendDatePoint (sendDate, sendPoint)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='发送留痕，每个交易日每个时点一条记录';

CREATE TABLE IF NOT EXISTS fund_catalog (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    fundCode   VARCHAR(16)  NOT NULL COMMENT '6 位基金代码',
    fundName   VARCHAR(255) NOT NULL COMMENT '基金简称',
    fundType   VARCHAR(32)           DEFAULT NULL COMMENT '天天基金原始类型，如 混合型-灵活',
    createTime DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukFundCode (fundCode),
    KEY idxFundName (fundName)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='基金全集缓存，供代码消歧查表，可随时重建';

CREATE TABLE IF NOT EXISTS fund_net_worth (
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键，应用侧雪花生成，未提供时由数据库自增',
    fundCode      VARCHAR(16)    NOT NULL COMMENT '6 位基金代码',
    netWorthDate  DATE           NOT NULL COMMENT '净值日期',
    unitNetWorth  DECIMAL(16, 6) NOT NULL COMMENT '单位净值',
    equityReturn  DECIMAL(12, 6)          DEFAULT NULL COMMENT '当日净值回报，单位百分点',
    createTime    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ukNetWorthFundDate (fundCode, netWorthDate),
    KEY idxNetWorthDate (netWorthDate)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='基金历史净值序列，供指标计算与晚间校准复用，可从上游重建';
