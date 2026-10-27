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
| 代码 | ISSUE-003 数据层已落地（5 张业务表 + Mapper/Service + 三个 CRUD Controller + 启动自检）；ISSUE-004 基金全集与代码消歧已落地（第 6 张表 `fund_catalog` + 4 个 `/fund` 接口）；ISSUE-005 历史净值与重仓股已落地（第 7 张表 `fund_net_worth` + `GET /fund/snapshot`）；ISSUE-006 盘中行情已落地（腾讯/新浪多源回退 + Redis 缓存 + 4 个 `/quote` 接口） |

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
| 前端 | Vue 3.5 + Vite 8 + Pinia + Vue Router | 前后端分离，后端只出 JSON |
| 工具库 | Hutool 5.8、FastJSON 1.2 | |
| 部署 | 宝塔面板托管 jar + Nginx 反代 + HTTPS | 单机单应用，不引入容器 |

> 技术选型的完整理由与取舍见 `docs/02-tech/TECH-PRD.md` 第二部分与 `docs/02-tech/decisions.md`。

## 目录结构

```
fund/
├── backend/                      # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/lilac/
│       │   ├── adapter/          # 外部数据源适配器（基金全集、身份回显、净值与持仓、盘中行情、HTTP 封装）
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
│           ├── db/schema.sql     # 七张业务表结构，启动时自动执行
│           └── application.yml   # application-dev.yml 为本地连接配置（不进 Git）
├── frontend/                     # Vue 3 前端
│   ├── src/
│   │   ├── api/                  # 接口层（由 Apifox 生成，尚未生成，禁止手改）
│   │   ├── router/               # 路由
│   │   ├── stores/               # Pinia 状态
│   │   └── views/                # 页面
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

七张业务表由 `backend/src/main/resources/db/schema.sql` 在启动时自动创建（`spring.sql.init.mode=always`，脚本全部 `IF NOT EXISTS`），无需手工建表。其中 `fund_catalog` 是基金全集缓存，首次录入前调 `POST /api/fund/catalog/sync` 拉取（约 2.8 万条），也可等首次消歧时自动触发；`fund_net_worth` 是历史净值序列，调 `GET /api/fund/snapshot?code=` 时按需落库。这两张表都可从上游重建。应用启动后 `backend/data/` 下会生成 `report/`、`op-log/`、`mail/` 三个子目录。

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
| 规范 | [`docs/05-conventions/README.md`](docs/05-conventions/README.md) |
| 功能实现 | [`docs/06-features/domain/ISSUE-003-数据层落库与实体建模.md`](docs/06-features/domain/ISSUE-003-数据层落库与实体建模.md)、[`docs/06-features/adapter/ISSUE-004-基金全集与消歧.md`](docs/06-features/adapter/ISSUE-004-基金全集与消歧.md)、[`docs/06-features/adapter/ISSUE-005-历史净值与重仓股.md`](docs/06-features/adapter/ISSUE-005-历史净值与重仓股.md)、[`docs/06-features/adapter/ISSUE-006-盘中行情与多源回退.md`](docs/06-features/adapter/ISSUE-006-盘中行情与多源回退.md) |

## 与 TECH-PRD 的偏差（待裁定）

TECH-PRD v0.4 定案的是 Java 25 + Spring Boot 4.1.1 + Spring Data JPA，但仓库实际是 **Java 21 + Spring Boot 3.5.14 + MyBatis-Flex 1.11.1**。ISSUE-003 按「以现有代码为准」实现：实体用 `@Table` / `@Column` / `@Id`，Mapper 继承 `BaseMapper`，未引入任何 JPA 注解。该偏差待使用者裁定后统一回写 TECH-PRD。
