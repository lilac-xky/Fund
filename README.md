# 基策罗盘（fund）

> 一个部署在服务器上、每天收盘前 30 分钟用邮件告诉你持仓基金当天预估涨跌与仓位参考的个人工具。

服务对象只有使用者本人：持有 10 只以内的场外基金与场内 ETF，盘中看不到预估净值，持仓一多就无暇盯盘，红利、网格、债券这几类持仓又缺少明确的加减仓依据。本项目把这套判断固化为每天四个时点的自动产出，买卖动作仍由使用者在支付宝或券商自行完成。

## 项目状态

| 项 | 状态 |
|----|------|
| 需求 | 已确认（`docs/01-requirements/PRD.md` v0.2） |
| 技术方案 | 已定案（`docs/02-tech/TECH-PRD.md` v0.4） |
| 排期 | 30 条 issue / 184 小时（`docs/03-plan/`） |
| 探针 | ISSUE-001 已完成，结论见 `docs/04-research/` |
| 代码 | ISSUE-003 数据层已落地（5 张业务表 + Mapper/Service + 三个 CRUD Controller + 启动自检）；ISSUE-004 基金全集与代码消歧已落地（第 6 张表 `fund_catalog` + 4 个 `/fund` 接口）；ISSUE-005 历史净值与重仓股已落地（第 7 张表 `fund_net_worth` + `GET /fund/snapshot`）；ISSUE-006 盘中行情已落地（腾讯/新浪多源回退 + Redis 缓存 + 4 个 `/quote` 接口）；ISSUE-007 预估引擎已落地（4 条分支 + A/B/C 档位 + 不可预估分支 + 2 个 `/estimate` 接口）；ISSUE-008 晚间校准已落地（第 8、9 张表 `estimate_snapshot` / `estimate_gold_beta` + 误差历史扩列 + 滚动档位 + 5 个 `/estimate/calibration` 接口）；ISSUE-009 中证股息率已落地（第 10 张表 `index_dividend` + HSSF 解析老式 `.xls` + 手工覆盖 + 3 个 `/dividend` 接口）；ISSUE-010 红利建议已落地（不新增表，参数进 `strategy_param` 的 `DIVIDEND` 分组 + 保底分红约束 + 4 个 `/dividend/advice` 接口）；ISSUE-011 四时点调度已落地（不新增表，复用 `send_record` + 启动补跑 + 时钟跳变 + 4 个 `/task` 接口）；ISSUE-012 收盘前邮件已落地（新增 `spring-boot-starter-mail` + HTML/纯文本双版 + 失败留存 `data/mail/` + 3 个 `/mail` 接口），M9 里程碑达成；**ISSUE-013 参数设置页已落地**（前端从零补齐：双模式主题 token + 三栏布局壳 + 6 个基础组件 + 参数设置页；后端新增 `SettingService` 字段级校验 + `SecretsWriter` 只写不读 + 5 个 `/setting` 接口）；**ISSUE-014 持仓录入页已落地**（新增第 11 张表 `holding_change` + 买入校验/卖出先进先出/品种聚合总览 + 6 个 `/holding` 接口 + 消歧候选带注册态回显 + 前端持仓录入页），M10 进行中 |

## 这个工具做什么

一条端到端闭环，缺一不成立：

```
录入持仓（只输代码，自动带出名称与净值）
  → 盘中打开网页看到每只持仓的当日预估涨跌与整体市场冷暖
  → 收盘前 30 分钟手机收到邮件：预估涨跌 + 可信度 + 策略提醒 + 建议金额
  → 在支付宝/券商自行下单
  → 晚间打开网页看到预估与实际净值的逐只误差，超阈值的被标出
```

核心难点不在页面，而在预估值本身：支付宝取消基金当日预估值后，只能由本工具按**季度披露的重仓股 + 指数**自行推算。这条假设已由 ISSUE-001 探针用 15 只真实基金 × 60 个交易日实测验证，结论**按类型分档**——股票型/场内 ETF/红利/债券型可达标，黄金需 β 校正，混合型放弃点估计、改为「方向 + 区间」承诺。

## 技术栈

以下为**仓库当前实际使用**的版本（以 `backend/pom.xml`、`frontend/package.json` 为准）：

| 层 | 选型 | 说明 |
|----|------|------|
| 后端 | Java 21 + Spring Boot 3.5.14 | 内嵌 Tomcat，`@Scheduled` 定时，统一 `Result` 返回 |
| 数据访问 | MyBatis-Flex 1.11.1 + HikariCP | 业务表结构简单，不引入 JPA |
| 存储 | MySQL / MariaDB + Redis | MySQL 存业务数据，Redis 存当日行情缓存与断网降级快照 |
| 会话 | Spring Session Data Redis + Redisson 3.50 | 登录态共享，分布式锁 |
| 前端 | Vue 3.5 + Vite 8 + Pinia + Vue Router + Tailwind 4 | 前后端分离，后端只出 JSON；主题为双模式三栏骨架，见 `docs/05-conventions/前端主题规范.md` |
| 工具库 | Hutool 5.8、FastJSON 1.2、Apache POI 5.5（仅 `poi`，解析中证老式 `.xls`） | |
| 部署 | 宝塔面板托管 jar + Nginx 反代 + HTTPS | 单机单应用，不引入容器 |

> 技术选型的完整理由与取舍见 `docs/02-tech/TECH-PRD.md` 第二部分与 `docs/02-tech/decisions.md`。

## 目录结构

```
fund/
├── backend/                      # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lilac/
│       │   ├── adapter/          # 外部数据源适配器（基金全集、身份回显、净值与持仓、盘中行情、中证股息率、HTTP 封装）
│       │   ├── common/           # 通用请求对象（分页、删除）
│       │   ├── config/           # MyBatis-Flex 全局配置、Redis key 规范、data 目录配置
│       │   ├── domain/
│       │   │   ├── entity/       # 品种、持仓、策略参数、误差历史、发送留痕
│       │   │   ├── enums/        # 品种类型、基金类型、持仓状态等枚举
│       │   │   ├── listener/     # 创建时间与更新时间的自动填充
│       │   │   ├── mapper/       # MyBatis-Flex BaseMapper
│       │   │   ├── repository/   # 仓储接口与 impl
│       │   │   └── result/       # 统一返回 Result
│       │   ├── enums/            # 状态码枚举 HttpsCodeEnum
│       │   ├── exception/        # BusinessException 与全局异常处理
│       │   ├── init/             # 启动自检与 data 目录初始化
│       │   ├── utils/            # 工具类（ThrowUtils 等）
│       │   └── BackendApplication.java
│       └── resources/
│           ├── db/schema.sql     # 十张业务表结构，启动时自动执行
│           └── application.yml   # application-dev.yml 为本地连接配置（不进 Git）
├── frontend/                     # Vue 3 前端
│   ├── src/
│   │   ├── api/                  # 接口层（由 Apifox 生成，禁止手改）
│   │   ├── composables/          # 组合式状态（主题与侧栏折叠）
│   │   ├── layout/               # 布局壳（顶栏 / 行情条 / 侧栏 / 保存条 / 右栏块）
│   │   ├── router/               # 路由
│   │   ├── services/             # 页面级数据编排（不碰 src/api/）
│   │   ├── styles/               # 主题 token（双模式 CSS 变量）
│   │   ├── ui/                   # 基础组件与全局消息
│   │   ├── views/                # 页面
│   │   ├── request.ts            # 统一请求封装
│   │   └── main.ts
│   ├── index.html                # 含首屏定主题脚本，避免深色闪烁
│   ├── vite.config.mjs           # 构建产物落 frontend/dist，不写入后端 static
│   └── openapi.config.js         # 从 Apifox 拉取 schema 生成接口代码
├── docs/                         # 全部项目文档，按编号分层
│   ├── 01-requirements/          # 需求 PRD 与访谈记录
│   ├── 02-tech/                  # 技术 PRD、决策记录
│   ├── 03-plan/                  # ROADMAP 与 ISSUES
│   ├── 04-research/              # 探针研究结论与实测数据
│   ├── 05-conventions/           # 开发规范细则（注释、接口、红线、文档）
│   └── 06-features/              # 功能实现文档，按模块分目录
├── AGENTS.md                     # 协作规范约束（AI 与人均适用）
└── README.md
```

## 本地运行

### 环境要求

- JDK 21+（`pom.xml` 中 `java.version` 为 21）
- Maven 3.9+
- Node.js 22.18+ 或 24.12+（见 `frontend/package.json` 的 `engines`）
- MySQL / MariaDB（库名 `fund`）、Redis

### 后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

开发模式直接运行 `BackendApplication` 即可。默认激活 `dev` 配置，服务端口 `9090`，统一上下文路径 `/api`，即接口地址形如 `http://127.0.0.1:9090/api/xxx`。

数据库与 Redis 连接信息在 `backend/src/main/resources/application-dev.yml`，该文件**已被 `.gitignore` 排除**，请勿提交。

十张业务表由 `backend/src/main/resources/db/schema.sql` 在启动时自动创建（`spring.sql.init.mode=always`，脚本全部 `IF NOT EXISTS`），无需手工建表。其中 `fund_catalog` 是基金全集缓存，首次录入前调 `POST /api/fund/catalog/sync` 拉取（约 2.8 万条），也可等首次消歧时自动触发；`fund_net_worth` 是历史净值序列，调 `GET /api/fund/snapshot?code=` 时按需落库。三张表都可从上游重建。`estimate_snapshot` 与 `estimate_gold_beta` 是 ISSUE-008 新增的两张表，分别存当日预估留存与黄金 β 记录，**不可从上游重建，须优先备份**；`estimate_error` 在旧库上会自动补列（`SchemaColumnUpgrader`）。`index_dividend` 是 ISSUE-009 新增的指数股息率表，调 `POST /api/dividend/refresh` 从中证官网抓取，国证系列（`399321`）与深证红利（`399324`）官网不提供，走 `POST /api/dividend/manual` 手工录入。ISSUE-010 未新增表：红利参数写入 `strategy_param` 的 `DIVIDEND` 分组（`baseAmount`/`floorAmount`/`addYield`/`reduceYield`），可通过 `POST /api/dividend/advice/plan/save` 保存，`POST /api/dividend/advice/list` 返回逐只加减仓建议。ISSUE-011 同样未新增表：复用 `send_record` 记录四个时点的发送留痕，`GET /api/task/overview` 查当日状态，`POST /api/task/trigger` 手动补发；启动补跑与每小时巡检共同兜住重启漏跑。ISSUE-012 新增邮件链路：`spring-boot-starter-mail` + HTML/纯文本双版正文，失败正文留在 `data/mail/`，`GET /api/mail/status` 查通道是否就绪、`POST /api/mail/resend` 触发补发。应用启动后 `backend/data/` 下会生成 `report/`、`op-log/`、`mail/` 三个子目录。

邮件的主机、账号、收件人与授权码**全部只写在 `backend/config/secrets.env`**，该文件已被 `.gitignore` 排除，模板见 `backend/config/secrets.env.example`（键名 `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_RECIPIENTS` / `SMTP_PASSWORD`）。`application.yml` 只保留开关、密钥文件路径、端口兜底与超时，**不含任何邮箱地址**，因此可以直接提交；任何接口都不会回显这些值，`GET /api/mail/status` 只回「是否就绪 + 缺哪项」。foxmail / QQ 邮箱用 `smtp.qq.com` + `587`（STARTTLS）。

### 前端

```bash
cd frontend
npm install
npm run dev        # 开发服务器
npm run build      # 类型检查 + 构建
npm run type-check # 仅类型检查
```

## 接口与前端代码生成

前端接口层**不由手写**。生成链路如下：

```
后端 Controller
  → Apifox Helper 插件按 backend/.apifox-helper.properties 导入 Apifox
  → Apifox 导出 OpenAPI schema
  → frontend/openapi.config.js 拉取 schema
  → 自动生成 frontend/src/api/
```

因此 `frontend/src/api/` 目录由工具生成，**任何人（包括 AI）不得手工新增或修改**，改动会在下次生成时被覆盖，且会与 Apifox 定义脱节。需要新接口时，先写后端 Controller，再由使用者本人执行导入与生成。

生成命令（由使用者执行）：

```bash
cd frontend
npm run openapi
```

## 开发规范

完整约束见 [AGENTS.md](AGENTS.md)，要点：

- 注释只写单行，后端每个函数都要有注释；每个类必须有注释介绍；禁止大段注释块
- Controller 统一返回 `Result<T>`，不直接返回实体或 `ResponseEntity`
- 参数校验用 `ThrowUtils.throwIf` + `HttpsCodeEnum`，错误信息统一由 `GlobalExceptionHandler` 兜底
- **严禁执行任何 git 写操作**，提交只能由使用者本人完成
- **严禁修改或生成 `frontend/src/api/`**
- 功能完成后在 `docs/` 下沉淀文档，按内容自建层级

## 新建功能文档

每个功能做完后，在 `docs/06-features/<模块>/` 下写一份实现文档，模块名与后端包结构对齐（`adapter` / `domain` / `strategy` / `indicator` / `task` / `web` / `report`）：

```
docs/06-features/web/ISSUE-015-早午盘总览页.md
```

必含六节：功能目标、实现位置、接口与数据结构、关键决策与取舍、已知限制与风险、验证方式。模板见 `docs/05-conventions/文档沉淀规范.md`。

## 文档索引

| 阶段 | 入口 |
|------|------|
| 需求 | [`docs/01-requirements/PRD.md`](docs/01-requirements/PRD.md) |
| 技术方案 | [`docs/02-tech/TECH-PRD.md`](docs/02-tech/TECH-PRD.md)、[`docs/02-tech/decisions.md`](docs/02-tech/decisions.md) |
| 排期 | [`docs/03-plan/ROADMAP.md`](docs/03-plan/ROADMAP.md)、[`docs/03-plan/ISSUES.md`](docs/03-plan/ISSUES.md) |
| 研究 | [`docs/04-research/`](docs/04-research/) |
| 规范 | [`docs/05-conventions/README.md`](docs/05-conventions/README.md)、[`前端主题规范.md`](docs/05-conventions/前端主题规范.md) |
| 功能实现 | [`docs/06-features/domain/ISSUE-003-数据层落库与实体建模.md`](docs/06-features/domain/ISSUE-003-数据层落库与实体建模.md)、[`docs/06-features/adapter/ISSUE-004-基金全集与消歧.md`](docs/06-features/adapter/ISSUE-004-基金全集与消歧.md)、[`docs/06-features/adapter/ISSUE-005-历史净值与重仓股.md`](docs/06-features/adapter/ISSUE-005-历史净值与重仓股.md)、[`docs/06-features/adapter/ISSUE-006-盘中行情与多源回退.md`](docs/06-features/adapter/ISSUE-006-盘中行情与多源回退.md)、[`docs/06-features/engine/ISSUE-007-预估引擎.md`](docs/06-features/engine/ISSUE-007-预估引擎.md)、[`docs/06-features/report/ISSUE-008-晚间净值校准与档位判定.md`](docs/06-features/report/ISSUE-008-晚间净值校准与档位判定.md)、[`docs/06-features/strategy/ISSUE-009-中证指数股息率抓取与手工覆盖.md`](docs/06-features/strategy/ISSUE-009-中证指数股息率抓取与手工覆盖.md)、[`docs/06-features/strategy/ISSUE-010-红利加减仓建议.md`](docs/06-features/strategy/ISSUE-010-红利加减仓建议.md)、[`docs/06-features/task/ISSUE-011-四时点调度与补跑.md`](docs/06-features/task/ISSUE-011-四时点调度与补跑.md)、[`docs/06-features/task/ISSUE-012-收盘前提醒邮件.md`](docs/06-features/task/ISSUE-012-收盘前提醒邮件.md)、[`docs/06-features/web/ISSUE-013-参数设置页.md`](docs/06-features/web/ISSUE-013-参数设置页.md)、[`docs/06-features/web/ISSUE-014-持仓录入页.md`](docs/06-features/web/ISSUE-014-持仓录入页.md)、[`docs/06-features/holding/FIX-026-持仓录入三问题修复.md`](docs/06-features/holding/FIX-026-持仓录入三问题修复.md) |

## 与 TECH-PRD 的偏差（待裁定）

TECH-PRD v0.4 定案的是 Java 25 + Spring Boot 4.1.1 + Spring Data JPA，但仓库实际是 **Java 21 + Spring Boot 3.5.14 + MyBatis-Flex 1.11.1**。ISSUE-003 按「以现有代码为准」实现：实体用 `@Table` / `@Column` / `@Id`，Mapper 继承 `BaseMapper`，未引入任何 JPA 注解。该偏差待使用者裁定后统一回写 TECH-PRD。
