# 会议室预约系统 — 工程代码与运行指南

> 归属环节：AI Coding 全流程实操笔试 · 环节 4（编码实现）  
> 交付物目录：`D:\exam\code\`  
> 技术架构：Spring Boot 3 (Java 17) + MySQL 8 + Redis 7 + Vue 3 + Element Plus + ECharts

---

## 一、目录结构

```
D:\exam\code\
├── backend/                  # 后端工程 (Java 17 + Spring Boot 3)
│   ├── src/main/java/        # 后端核心业务源码
│   │   └── com/exam/
│   │       ├── annotation/   # 注解定义 (@RequireAdmin)
│   │       ├── common/       # 统一响应 (ApiResponse)、全局异常、用户上下文
│   │       ├── config/       # Redis、Security、WebMvc 拦截器配置
│   │       ├── controller/   # 认证、会议室、部门、预约、看板 REST 控制器
│   │       ├── dto/          # 请求体、响应体与视图对象
│   │       ├── entity/       # 数据实体类 (User, Department, Room, Reservation)
│   │       ├── interceptor/  # JWT 鉴权与登出黑名单拦截器
│   │       ├── mapper/       # MyBatis Mapper 接口
│   │       ├── service/      # 预约冲突检测、状态机、缓存聚合核心服务
│   │       └── util/         # JWT HS256 工具类
│   ├── src/main/resources/   # 配置文件与 MyBatis XML、初始化 DDL
│   │   ├── application.yml   # 主配置文件
│   │   ├── schema.sql        # MySQL 初始化建表与测试数据脚本
│   │   └── mapper/           # MyBatis XML 映射文件
│   └── pom.xml               # Maven 依赖配置
│
├── frontend/                 # 前端工程 (Vue 3 + Vite + Element Plus)
│   ├── src/
│   │   ├── api/              # Axios 请求封装与 REST 接口模块
│   │   ├── components/       # 顶部导航、时钟与用户卡片组件
│   │   ├── router/           # Vue Router 路由守卫与鉴权跳转
│   │   ├── store/            # Pinia 用户登录状态与持久化
│   │   ├── views/            # 登录页、预约列表页、预约表单页、数据看板页
│   │   ├── App.vue           # 根组件
│   │   └── main.js           # 前端入口
│   ├── package.json          # npm 依赖配置
│   ├── vite.config.js        # Vite 构建与 /api 反向代理配置
│   └── index.html            # 单页 HTML 模板
│
└── README.md                 # 本运行指南文档
```

---

## 二、本地启动指南

### 1. 后端启动 (Backend)

确保本机安装有 JDK 17 与 Maven。

```bash
cd D:\exam\code\backend

# 方式一：Maven 直接运行
mvn spring-boot:run

# 方式二：打包后运行 Jar 包
mvn clean package -DskipTests
java -jar target/meeting-room-backend-1.0.0.jar
```

* 后端服务监听端口：`8080`
* 统一接口前缀：`/api/**`

### 2. 前端启动 (Frontend)

确保本机安装有 Node.js (v18+) 与 npm。

```bash
cd D:\exam\code\frontend

# 安装依赖
npm install

# 启动开发服务器（已配置自动反向代理到 8080 端口）
npm run dev
```

* 本地开发访问地址：`http://localhost:3000`
* 构建生产静态资源包：`npm run build`（输出至 `dist/` 目录）

---

## 三、系统预置测试账号

| 账号用户名 (username) | 密码 | 真实姓名 | 归属部门 | 角色权限 | 具备能力说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `admin` | `123456` | 李强 | 行政部 | `ADMIN` (系统管理员) | 管理会议室启用停用；取消任意未开始预约；提前结束任意已开始预约；查看看板 |
| `emp001` | `123456` | 张伟 | 研发部 | `EMPLOYEE` (普通员工) | 发起预约；取消**本人未开始**预约；提前结束**本人已开始**预约；查看看板 |
| `emp002` | `123456` | 李雷 | 产品部 | `EMPLOYEE` (普通员工) | 发起预约；管理本人预约；验证跨用户只读与越权拦截 |
| `emp003` | `123456` | 韩梅梅 | 市场部 | `EMPLOYEE` (普通员工) | 发起预约；管理本人预约 |

---

## 四、核心特性与技术验证

1. **半开区间冲突算法**：数学公式 $\max(s_1, s_2) < \min(e_1, e_2)$，相邻时段（如 09:00~10:00 与 10:00~11:00）允许预约，重叠时段严格拦截并返回 409。
2. **并发与防重机制**：数据库事务级 `SELECT ... FOR UPDATE` 行排他锁保证高并发抢订下严格仅一人成功；Redis `reservation:submit_lock` 防范 5 秒内双击重复提交。
3. **二元状态与即时态计算**：列表与详情展示采用 `computeStatus` 根据系统权威时钟即时判定状态（`PENDING` $\to$ `ACTIVE` $\to$ `ENDED`），彻底消弭状态延迟。
4. **多级缓存与主动失效**：数据看板按日/月聚合查询结果缓存至 Redis（TTL 10 分钟），新增、取消、提前结束预约时主动淘汰对应键，实现毫秒级查询与数据强一致。
