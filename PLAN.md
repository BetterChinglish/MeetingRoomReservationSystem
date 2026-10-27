# 会议室预约系统 — 全流程实施规划文档

> 本文档为 AI Coding 全流程实操笔试（第1套）的总体实施规划，作为后续 6 个环节（需求分析 → 原型设计 → 规格编写 → 编码实现 → 测试验证 → 部署交付）的总纲。各环节交付物分别落在 `D:\exam\` 下的 `prd / design / spec / code / test / deploy` 目录。

## 一、项目概述

为单位行政建设一套**会议室预约系统**（仅 Web 端）。员工在线预约会议室，系统保证同一会议室同一时间段不可重复预约；提供预约的查询、取消、提前结束等生命周期管理，区分普通员工与管理员权限；并提供按日期/月份维度的数据看板，统计各会议室、各部门的预约场次与占用时长。

## 二、技术栈（已确认）

| 层             | 选型                           | 说明                                                                             |
| :------------- | :----------------------------- | :------------------------------------------------------------------------------- |
| 后端           | Java 17 + Spring Boot 3        | RESTful API，契合"公司统一开发框架"语境，Docker 部署成熟                         |
| 数据库         | MySQL 8                        | 库 `exam`，连接 `3306 / exam / V-Pnvk`；用事务 + 唯一约束/行锁保证时段不冲突 |
| 缓存           | Redis 7                        | 连接 `6379 / 默认 / V-Pnvk`；用于会话、热点查询缓存、防重复提交                |
| 前端           | Vue 3 + Element Plus + ECharts | 组合式 API，表格/表单/图表组件齐全                                               |
| 构建/部署      | Maven + Docker + Nginx         | 后端打 jar 制镜像；前端构建后由 Nginx 托管并反代后端                             |
| AI Coding 工具 | Claude Code（备用）            | 即本工具，以 Spec 契约驱动编码                                                   |

> 说明：题目要求"公司白泽zcode/opencode，备用 claude code、codex"。当前执行环境即以 Claude Code 作为 AI Coding 工具完成全流程。

## 三、整体架构

```
浏览器 (Vue3 + ElementPlus)
        │  HTTP/JSON
        ▼
   Nginx (静态托管 + /api 反代)
        │
        ▼
Spring Boot REST API  ──► Redis (会话/缓存)
        │
        ▼
     MySQL (业务数据)
```

- 前后端分离，前端构建产物由 Nginx 托管，`/api/**` 反向代理到后端。
- 鉴权：账号密码登录，签发 JWT；角色字段区分 `员工 / 管理员`。
- 时段冲突校验：应用层校验 + 数据库层排他约束（起止时间区间不相交）双保险。

## 四、核心数据模型（草案，最终以 Spec 为准）

- **user 用户表**：id, username, password(哈希), name, dept_id, role(EMPLOYEE/ADMIN), created_at
- **department 部门表**：id, name
- **meeting_room 会议室表**：id, name, location, capacity, status(ENABLED/DISABLED), created_at
- **reservation 预约表**：id, room_id, user_id, reserve_date, start_time, end_time, dept_id, title, status(PENDING/ACTIVE/CANCELLED/ENDED), created_at, updated_at
  - 冲突约束：同一 `room_id + reserve_date` 下，有效（非 CANCELLED）记录的 `[start_time, end_time)` 区间不得相交。
- 部门、会议室建议预置初始数据。

## 五、功能范围与权限矩阵

| 能力                               | 普通员工 | 管理员  |
| :--------------------------------- | :------- | :------ |
| 查询所有会议室预约情况             | ✅       | ✅      |
| 新建预约（校验时段冲突）           | ✅       | ✅      |
| 取消**本人未开始**的预约     | ✅       | ✅      |
| 提前结束**本人已开始**的预约 | ✅       | ✅      |
| 取消**任意未开始**的预约     | ❌       | ✅      |
| 提前结束**任意已开始**的预约 | ❌       | ✅      |
| 数据看板                           | ✅ 查看  | ✅ 查看 |

> 状态机：`PENDING(未开始) → ACTIVE(已开始) → ENDED(提前结束/自然结束)`；任意未开始态可 `CANCELLED`。

## 六、六环节实施计划

### 环节 1 — 需求分析（交付 `D:\exam\prd\`） 【已完成 ✅】

**目标**：输出产品需求说明书 PRD（总分结构：1 份总纲 + 5 份专业分册，总计约 200KB）。

- 梳理显性需求（CRUD、冲突规则、权限、看板维度）。
- 挖掘隐性需求：登录鉴权、并发预约冲突、时区/跨天、未开始/已开始的判定时点、取消与提前结束的业务语义、看板空数据态。
- 识别业务风险：并发抢同一时段、越权操作、数据一致性。
- 划定 MVP 边界：本次不做移动端、不做审批流、不做会议室设备管理等 17 项。
- **交付物**：
  - [PRD.md](prd/PRD.md) — **总纲说明书**（系统定位、架构路线、领域实体拓扑、6大全局核心决策、总权限矩阵、MVP边界、非功能与合规指标、分册导航）
  - [01-用户与权限.md](prd/01-用户与权限.md) — 分册1（二值角色、JWT完整生命周期、BCrypt、登录失败锁定、越权防护、虚构示例账号预置）
  - [02-预约管理.md](prd/02-预约管理.md) — 分册2（核心时段冲突 [start,end) 半开区间算法、状态机及二元状态机制、取消/提前结束判定、高频查询、并发行锁与防重）
  - [03-会议室与部门.md](prd/03-会议室与部门.md) — 分册3（主数据维护、停用影响传导、引用阻止物理删除/软删除、乐观锁 version、示例组织数据）
  - [04-数据看板.md](prd/04-数据看板.md) — 分册4（日/月双维度筛选、4组统计指标、ECharts柱状/堆叠图、全量主数据展示、Redis多级缓存与主动失效）
  - [05-非功能与风险.md](prd/05-非功能与风险.md) — 分册5（性能压测指标、安全合规、22项业务风险清单、17项MVP边界、跨分册一致性与双探针健康检查）

### 环节 2 — 原型设计（交付 `D:\exam\design\`） 【已完成 ✅】

**目标**：生成可交互原型 HTML，反向验证需求。

- 三个页面：预约列表页、预约表单页、数据看板页。
- 列表页：关键字段筛选（会议室/日期/部门/状态）、员工与管理员视图差异、操作按钮随权限显隐。
- 表单页：会议室、部门下拉选择，日期+起止时间，人员自动带入，提交前冲突提示与当日时间轴排期看板。
- 看板页：日期/月份切换，会议室对比柱状图、部门对比柱状图、占用时长堆叠图与无障碍明细透视表。
- 交付：`prototype.html`（单文件可交互，含导航与三个页面切换、角色快速切换与时钟快进模拟）+ `设计说明.md`。

### 环节 3 — 规格编写（交付 `D:\exam\spec\` 与 `D:\exam\openspec\`） 【已完成 ✅】

**目标**：输出无歧义、可直接驱动 AI 编码的规格契约。

- 数据模型与数据库约束（建表 DDL、索引、排他约束）。
- 接口协议：统一响应结构、错误码、所有 REST 端点（请求/响应 schema、示例）。
- 关键业务逻辑：冲突校验算法、状态机流转、权限判定。
- 安全合规：JWT 鉴权、密码哈希、越权防护、SQL 注入防护、输入校验。
- 验收标准：逐功能的可测断言。
- **交付物**：
  - [SPEC.md](spec/SPEC.md) — 全量规格契约说明书（数据模型、API全量定义、[start,end)区间冲突算法、状态机、权限矩阵、安全基线、验收断言矩阵）
  - [ddl.sql](spec/ddl.sql) — 完整建表与初始化基线数据脚本（支持幂等重跑，包含4张核心表、索引与测试账号数据）
  - OpenSpec 规范管理契约集（`openspec/changes/meeting-room-reservation-spec/`）：
    - `proposal.md`：变更提案与能力映射
    - `specs/auth-rbac/spec.md`：认证与RBAC能力规格
    - `specs/meeting-room-dept/spec.md`：会议室与部门主数据能力规格
    - `specs/reservation-lifecycle/spec.md`：预约生命周期与冲突校验规格
    - `specs/analytics-dashboard/spec.md`：看板聚合与缓存规格
    - `design.md`：技术设计方案与风险对策
    - `tasks.md`：8个阶段24项编码实施任务清单（均带验证断言）

### 环节 4 — 编码实现（交付 `D:\exam\code\`） 【已完成 ✅】

**目标**：以 Spec 驱动生成代码，完成本地前后端联调。

- 后端：Spring Boot 3 单体工业级工程，标准分层（`controller` / `service` / `mapper` / `entity` / `dto`），集成 MyBatis、Redis、JWT HS256、BCrypt，具备 JVM + MySQL 排他行锁与 fail-open 优雅降级。
- 前端：Vue 3 + Vite + Element Plus + ECharts + Pinia + Axios，完整实现登录页、预约列表页、发起预约页、数据看板页。
- 契约复核：对照 Spec 与 PRD 验证 15 个 REST 接口、状态机二元流转、权限隔离与冲突算法，单元测试与 E2E 测试 18/18 全绿通过。
- 当前服务运行状态：**后端端口 8080 已启动；前端开发服务器端口 3000 已启动，可直接在浏览器访问与演示**。
- **交付物**：
  - [code/backend/](code/backend/)：Spring Boot 3 后端源码、MyBatis XML、测试用例与构建包 `meeting-room-backend-1.0.0.jar`
  - [code/frontend/](code/frontend/)：Vue 3 前端工程源码与生产构建包 `dist/`
  - [code/README.md](code/README.md)：工程架构与本地起停操作指南

### 环节 5 — 测试验证（交付 `D:\exam\test\`） 【待进行 ⏳】

**目标**：依据 Spec 编写测试用例并执行。

- 单元测试：冲突校验、状态机、权限判定（JUnit + Mockito）。
- 集成测试：预约全流程、并发冲突（Spring Boot Test + Testcontainers 或内嵌 MySQL）。
- E2E：登录、预约、取消、提前结束、看板展示（前端侧或接口级）。
- 缺陷修复与回归。
- 交付：`测试用例.md`、`test-reports/`（执行结果/报告）、缺陷记录。

### 环节 6 — 部署交付（交付 `D:\exam\deploy\`）

**目标**：考场服务器环境部署验证 + 交付文档。

- 后端打 jar，构建 Docker 镜像；前端构建后入 Nginx 镜像或托管目录。
- `docker-compose.yml` 编排 backend / frontend(Nginx) / 依赖 MySQL/Redis（已运行，可仅连不启）。
- 起停脚本 `start.sh / stop.sh`。
- 交付文档：镜像包位置、起停脚本、访问网址、账号密码、环境变量。
- 交付：`docker-compose.yml`、`Dockerfile.*`、`start.sh`、`stop.sh`、`交付文档.md`。

## 七、里程碑与顺序

| 环节             | 状态                | 交付目录            | 交付物说明                                          |
| :--------------- | :------------------ | :------------------ | :-------------------------------------------------- |
| 环节 1：需求分析 | **已完成 ✅** | `D:\exam\prd\`    | PRD.md 总纲 + 01~05 共 5 份专业分册（总计约 200KB） |
| 环节 2：原型设计 | **已完成 ✅** | `D:\exam\design\` | prototype.html 可交互原型 + 设计说明.md             |
| 环节 3：规格编写 | **已完成 ✅** | `D:\exam\spec\`   | SPEC.md + ddl.sql + openspec/ 完整规范契约集        |
| 环节 4：编码实现 | **已完成 ✅** | `D:\exam\code\`   | 后端 Spring Boot + 前端 Vue 3 全量工程代码（已运行中）|
| 环节 5：测试验证 | **待进行 ⏳** | `D:\exam\test\`   | 测试用例.md + 单元/集成测试执行报告                 |
| 环节 6：部署交付 | 待进行              | `D:\exam\deploy\` | docker-compose.yml + start/stop 脚本 + 交付文档.md  |

## 八、待确认/已自行决策项

- 技术栈：**Java+SpringBoot / MySQL / Vue3+ElementPlus**（已确认）。
- Redis 用途：会话黑名单 + 登录限流 + 防重复提交 + 看板查询缓存（PRD阶段已固化）。
- 跨天预约：MVP 暂限定单日内的起止时间，禁止跨天，禁止过去时间（PRD阶段已固化）。
- 鉴权方式：JWT HS256 + Pinia内存存储 + sessionStorage 兜底（PRD阶段已固化）。
- 部署形态：Docker Compose 单机（已规划）。
- 凭据隔离：环境凭据（desc.md）与业务预置账号（admin/emp001等虚构账号）严格物理隔离。

---

**当前进度总结**：环节 1（需求分析）与环节 2（原型设计）已全面交付并定稿。下一步可直接进入**环节 3（规格编写）**，在 `D:\exam\spec\` 输出无歧义的系统契约 SPEC.md 与 ddl.sql。
