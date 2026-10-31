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
| Apache POI | 5.4.1 | Excel 读写：解析达成情况、导入/导出共创会数据 |
| Lombok | 1.18.46 | 编译期生成 getter/setter 与日志对象（`provided`，不进入运行时依赖） |
| springdoc-openapi | 3.1.1 | Swagger UI 接口文档 |
| Jackson | 由 Spring Boot 管理 | JSON 序列化（人员名单解析同样使用 Jackson，未引入其它 JSON 库） |

> 依赖全部来自开源公共仓库，未使用任何私有包（原 devtool 功能里的 ziroom 相关依赖已全部移除）。

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
| `/api/devtool/okr/statistics/v1/uploadOkrStatisticsExcel` | POST | 上传 OKR 达成情况 Excel（multipart，参数名 `file`） |
| `/api/devtool/okr/statistics/v1/getOkrStatisticsList` | POST | 查询 OKR 明细与筛选项 |
| `/api/devtool/okr/statistics/v1/clearOkrStatisticsData` | POST | 清空 OKR 内存数据 |
| `/api/devtool/meeting/user/collection/v1/importMeetingUserRealRecordExcel` | POST | 导入共创会参会数据（multipart，参数名 `file`） |
| `/api/devtool/meeting/user/collection/v1/getMeetingUserCollectionList` | POST | 查询共创会明细与战队汇总 |
| `/api/devtool/meeting/user/collection/v1/exportMeetingUserCollectionExcel` | POST | 导出共创会统计 Excel（失败时返回 JSON 错误体） |
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
│   ├── BusinessException.java        业务异常（HTTP 200 + 业务码）
│   ├── ResponseCode.java             业务码枚举
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
│   ├── impl/
│   │   └── GreetingServiceImpl.java
│   └── devtool/                      两个功能的 Service 与实现
├── entity/                           实体
│   ├── Greeting.java                 （record）
│   └── meeting/                      共创会表实体
├── mapper/
│   └── meeting/                      共创会 Mapper（复杂 SQL 在 resources/mapper 下）
├── constants/
│   └── OkrStatisticsConstant.java    OKR 展示常量
├── controller/devtool/               OKR/共创会接口
├── dto/devtool/                      两个功能的 DTO（明细、筛选项、导入导出结果）
└── support/devtool/                  Excel/名单解析、Excel 导出生成
```

分层调用方向固定为 `controller → service → mapper`，不允许跨层调用。

### 新增一个业务模块

以「职位」为例，按下面的顺序加：

1. `entity/` 建实体类（数据库返回对象）；入参对象放 `dto/`；
2. `mapper/` 建 Mapper 接口（简单查询用 `@Select` 注解，复杂 SQL 放 `resources/mapper/**/*.xml`）；
3. `service/` 建接口，`service/impl/` 写实现（`@Transactional` 控制事务）；
4. `controller/` 建 Controller，用 `@PostMapping` + `@RequestBody` 接收参数，返回 `ApiResponse<T>`；
5. 需要建表/初始化数据时，在 `resources/` 下新增 `schema.sql`、`data.sql`（已配置自动执行）。

> `mapper` 包已包含共创会的 Mapper（`mapper/meeting/`），`@MapperScan` 会自动扫描。

## devtool 工具功能

原 `kman-oms` 里的两个 devtool 功能已迁移到本项目，并去掉所有 ziroom 私有依赖：

| 功能 | 后端入口 | 前端页面 |
| --- | --- | --- |
| OKR达成情况统计 | `controller/devtool/OkrStatisticsController` | `views/devtool/okrStatistics` |
| 共创会人员统计 | `controller/devtool/MeetingUserCollectionController` | `views/devtool/meetingUserCollection` |

### OKR达成情况统计

- 数据只保存在内存（Spring 单例 + `AtomicReference` 快照），应用重启后需重新上传；清空接口可手工重置。
- 上传的达成 Excel 会与人员名单取交集：**名单外的人不展示，名单中但 Excel 里没有的人也不展示**。
- 人员名单：`src/main/resources/docs/okr/okrNameList.md`（Markdown 中包一段 `json` 数组，`{"name","code"}`）。
  名单每次上传都会重新读取，改文件后无需重启。
- 解析用 Apache POI，按表头名匹配（含空格归一化），不依赖列的固定下标。

### 共创会人员统计

- 导入模版 21 列，与 `meeting_user_real_record` 字段一一对应（不含自增 id）。
- 导入按 Excel 中出现的「活动ID + 战队ID」先清空再批量插入，整个过程在同一事务内，失败回滚。
- 查询按 `activity_id` 取数，再与人员名单
  `src/main/resources/docs/meeting/meetingNameList.md` 取交集；
  战队汇总行是**整个战队口径**（不施加名单过滤），与导出模板一致。
- 指标口径：观看时长先按参会时长封顶；各率保留 1 位小数，四舍五入统一用 `HALF_UP`（与 Excel `ROUND` 一致）。
- 表结构由 `src/main/resources/schema.sql` 自动创建；`meeting_activity` 只用于回显「共创会名称/活动日期」，
  没有数据也能正常统计，需要时手工插入一行即可。

### 快速验证

```bash
# 上传 OKR 达成情况 Excel（样例文件见迁移前的 docs 目录）
curl -X POST http://localhost:8081/api/devtool/okr/statistics/v1/uploadOkrStatisticsExcel \
  -F 'file=@OKR_2026年10月_01.xlsx'

curl -X POST http://localhost:8081/api/devtool/okr/statistics/v1/getOkrStatisticsList \
  -H 'Content-Type: application/json' -d '{}'

# 导入并查询共创会数据
curl -X POST http://localhost:8081/api/devtool/meeting/user/collection/v1/importMeetingUserRealRecordExcel \
  -F 'file=@共创会导入数据.xlsx'

curl -X POST http://localhost:8081/api/devtool/meeting/user/collection/v1/getMeetingUserCollectionList \
  -H 'Content-Type: application/json' \
  -d '{"activityId":645,"answerScore":100,"attendanceDuration":8820}'
```

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
- 入参对象统一放 `dto/`（`record` 或普通类均可），需要校验时配合 `@Valid`；
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
| `BusinessException` | 200 | 业务错误（`code != 0`，`message` 可直接展示给用户） |
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

> `schema.sql` 当前包含共创会功能的两张表：`meeting_activity`、`meeting_user_real_record`。
> OKR 统计不落库，因此没有对应表。

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
