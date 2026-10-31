# job-tool-platform

Job Tool 后端服务，基于 Spring Boot 的分层架构 Maven 项目。

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 21 | 本机已安装 JDK 21 |
| Spring Boot | 4.1.1 | 当前稳定版（Spring Framework 7） |
| Maven | 3.10.0 | 本机安装于 `/Users/pengfei/Documents/work/coding/maven/apache-maven-3.10.0` |
| MyBatis | mybatis-spring-boot-starter 4.1.0 | 持久化层 |
| H2 | 2.x | 内存数据库，初始化阶段零外部依赖 |
| springdoc-openapi | 3.1.1 | Swagger UI 接口文档 |
| Jackson | 由 Spring Boot 管理 | JSON 序列化 |

> 依赖全部来自开源公共仓库，未使用任何私有包。

## 快速开始

```bash
# 启动服务（默认 8081 端口）
./mvnw spring-boot:run

# 运行测试
./mvnw test

# 打包
./mvnw clean package
java -jar target/job-tool-platform-0.0.1-SNAPSHOT.jar
```

> 统一使用项目自带的 `./mvnw`（Windows 下用 Git Bash 执行 `sh mvnw`），
> 它会固定使用 Maven 3.10.0 与项目专属配置，**不读取也不修改全局 Maven 配置**，
> 因此不受 PATH 里旧版 Maven 或其它项目配置的影响。

启动后：

| 接口 | 方式 | 说明 |
| --- | --- | --- |
| `/api/hello` | POST | Hello World 示例，请求体 `{ "name": "Job Tool" }`；`name` 省略或为空白时返回 `Hello, World!` |
| `/api/system/info` | POST | 应用信息（前端联通性检测），无请求体 |
| `/api/actuator/health` | GET | 健康检查（Spring Boot Actuator 自带提供，不受本项目约定影响） |
| `/api/swagger-ui.html` | GET | 接口文档（springdoc 自带提供） |
| `/api/h2-console` | GET | H2 控制台（JDBC URL `jdbc:h2:mem:jobtool`，用户 `sa`，密码空） |

> ⚠️ **接口约定：业务接口统一使用 POST。**
> 参数一律通过请求体（JSON）传递，不使用 GET / PUT / DELETE 等其它方式。
> 用其它方式访问业务接口会返回 **405** 与统一错误结构。
> （Actuator、Swagger UI、H2 控制台属于框架自带页面，仍是 GET，不在约定范围内。）

调用示例：

```bash
# 不传 name，返回 Hello, World!
curl -X POST http://localhost:8081/api/hello \
  -H 'Content-Type: application/json' -d '{}'

# 传 name
curl -X POST http://localhost:8081/api/hello \
  -H 'Content-Type: application/json' -d '{"name":"Job Tool"}'
```

## 分层结构

```
src/main/java/com/jobtool/platform/
├── JobToolPlatformApplication.java   启动类（@MapperScan 扫描 mapper）
├── common/                           通用能力
│   ├── ApiResponse.java              统一响应结构 {code, message, data}
│   └── GlobalExceptionHandler.java   全局异常处理
├── config/
│   └── WebMvcConfig.java             CORS 等 Web 配置
├── controller/                       接口层：只处理请求/响应与参数校验
│   ├── GreetingController.java       Hello World 示例
│   └── SystemController.java
├── dto/                              请求体对象（POST 入参）
│   └── GreetingRequest.java          （record + @Size 校验）
├── service/                          业务层：业务逻辑与事务边界
│   ├── GreetingService.java          接口
│   └── impl/
│       └── GreetingServiceImpl.java
└── entity/                           实体
    └── Greeting.java                 （record）
```

分层调用方向固定为 `controller → service → mapper`，不允许跨层调用。

### 新增一个业务模块

以「职位」为例，按下面的顺序加：

1. `entity/` 建实体类（数据库返回对象）；入参对象放 `dto/`；
2. `mapper/` 建 Mapper 接口（简单查询用 `@Select` 注解，复杂 SQL 放 `resources/mapper/**/*.xml`）；
3. `service/` 建接口，`service/impl/` 写实现（`@Transactional` 控制事务）；
4. `controller/` 建 Controller，用 `@PostMapping` + `@RequestBody` 接收参数，返回 `ApiResponse<T>`；
5. 需要建表/初始化数据时，在 `resources/` 下新增 `schema.sql`、`data.sql`（已配置自动执行）。

> `mapper` 包当前为空（职位相关代码已移除），新增 Mapper 后 `@MapperScan` 会自动扫描到。

## 接口约定

### 1. 请求方式统一 POST

业务接口一律使用 `@PostMapping`，参数通过请求体（JSON）传递：

```java
@PostMapping("/jobs")
public ApiResponse<PageResult<JobPosting>> page(@Valid @RequestBody JobQueryRequest request) {
    return ApiResponse.ok(jobPostingService.page(request));
}
```

- 不使用 `GET` / `PUT` / `DELETE`，查询类接口也用 POST；
- 入参对象统一放 `dto/`，用 `record` 承载，配合 `@Valid` 做校验；
- 用其它方式访问业务接口会由全局异常处理返回 **405**。

### 2. 响应结构统一

所有接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code = 0`：成功，业务数据在 `data` 中
- `code != 0`：失败，`message` 为错误原因

异常由 `GlobalExceptionHandler` 统一转换，不会把堆栈暴露给前端。已覆盖：

| 异常 | HTTP 状态 | 说明 |
| --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | `@Valid` 校验失败（如超过 `@Size` 限制） |
| `IllegalArgumentException` | 400 | 业务参数不合法 |
| `HttpRequestMethodNotSupportedException` | 405 | 用了非 POST 方式访问业务接口 |
| `NoResourceFoundException` | 404 | 接口不存在 |
| 其它 `Exception` | 500 | 兜底，只返回通用提示 |

## 数据库

初始化阶段使用 H2 内存数据库，应用重启即重置，方便本地开发。

建表与初始化数据放置约定（`spring.sql.init.mode=always`，文件存在即自动执行）：

- `src/main/resources/schema.sql` —— 建表
- `src/main/resources/data.sql` —— 初始化数据

> 当前这两个文件尚未创建（暂无数据表），需要时直接新增即可。

### 切换为正式数据库

1. 在 `pom.xml` 中把 `h2` 依赖替换为对应驱动（如 `mysql-connector-j`）；
2. 修改 `application.yml` 的 `spring.datasource` 配置；
3. 把 `spring.sql.init.mode` 改为 `never`，改用 Flyway/Liquibase 管理表结构；
4. 表字段为下划线命名时无需额外配置（已开启 `map-underscore-to-camel-case`）。

## Maven 配置说明

本项目使用项目自带的 `./mvnw` 启动脚本，**只影响本项目**，不修改也不依赖全局 Maven 配置：

| 项 | 值 |
| --- | --- |
| Maven 启动脚本 | `./mvnw`（项目根目录） |
| Maven 安装目录 | `/Users/pengfei/Documents/work/coding/maven/apache-maven-3.10.0` |
| 项目 settings | `.mvn/settings.xml`（由 `./mvnw` 通过 `-s` 指定） |
| 本地仓库 | `/Users/pengfei/Documents/work/coding/maven/repository` |
| 仓库地址 | 阿里云公共镜像（聚合 Maven Central），兜底官方 Central |

`./mvnw` 做了三件事：

1. 固定调用 Maven 3.10.0，不受 PATH 中旧版 Maven 影响；
2. 通过 `-s .mvn/settings.xml` 只使用开源公共仓库，**不引用任何私有仓库**（如 `mvn.ziroom.com`）；
3. 通过 `-Dmaven.repo.local` 固定本地仓库路径。

换机器时只需改 `mvnw` 顶部的两个变量（也可用环境变量 `MAVEN_HOME_OVERRIDE`、
`MAVEN_REPO_LOCAL_OVERRIDE` 覆盖）：

```sh
MAVEN_HOME=/path/to/apache-maven-3.10.0
MAVEN_REPO_LOCAL=/path/to/repository
```

### IDE（IDEA）设置

- Maven home path：选择 `apache-maven-3.10.0` 目录
- User settings file：指向本项目的 `.mvn/settings.xml`
- Local repository：勾选 Override 并填 `/Users/pengfei/Documents/work/coding/maven/repository`

> 注意：本机 `apache-maven-3.6.3` 是旧版本，其 `conf/settings.xml` 中的 `localRepository`
> 仍指向一个不存在的路径，用它构建会失败。本项目通过 `./mvnw` 固定使用 3.10.0，不受影响。

## 后续规划

- [ ] 接入正式数据库与 Flyway 迁移
- [ ] 增加登录鉴权（Spring Security + JWT）
- [ ] 补充分页查询、条件筛选与统一分页结构 `PageResult`
- [ ] 写操作增加参数校验（`@Valid`）与业务异常体系
