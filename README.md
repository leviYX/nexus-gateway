# Nexus Gateway：Netty 网关学习项目

一个基于 Netty 从零实现的 API 网关项目。通过逐步搭建真实的网络服务，学习 pipeline、异步模型、协议编解码、请求转发和资源管理，不以生产可用为第一目标。

长期会探索 HTTP、HTTPS/TLS、HTTP/2、TCP、UDP、Protobuf 和自定义协议；当前先把 HTTP 链路跑通。

## 当前已实现

- Maven 单模块项目、Maven Wrapper、可独立运行的可执行 JAR
- Netty HTTP/1.1 服务端，默认监听 `0.0.0.0:8080`
- 静态本地路由注册表，JSON 响应和 GET / HEAD 支持
- HTTP Keep-Alive、404 / 405 响应、基础坏请求处理
- 请求体聚合上限 1 MiB，超出返回 413
- 启动日志、连接异常日志、JVM 关闭钩子和 Netty 资源回收
- 真实 HTTP 集成测试

目前所有路由均为本地响应，尚未实现向上游服务转发。`/health` 仅表示当前 HTTP 服务存活，不检查上游依赖。

| 路径 | 方法 | 用途 |
| --- | --- | --- |
| `/` | GET、HEAD | 服务信息 |
| `/health` | GET、HEAD | 健康检查，GET 返回 `{"status":"UP"}` |
| `/gateway/routes` | GET、HEAD | 查看当前本地路由列表 |

路由按路径精确匹配，查询参数不参与匹配。未知路径返回 404，已注册路径使用其他方法返回 405 并附带 `Allow: GET, HEAD`。HEAD 返回与 GET 对应的响应头，不返回响应体。

## 环境与依赖

- JDK 25 或更高版本（编译目标为 Java 25）
- Maven 3.9.11（由 Wrapper 提供，无需全局安装 Maven）
- Netty `4.2.21.Final`
- Jackson `2.20.0`
- JUnit Jupiter `5.13.4`

POM 参考同级 `atlas-gateway` 项目，复用编译、测试和 Shade 打包配置。保留 `netty-all` 聚合依赖以方便后续协议学习；指标库待实现指标功能时再引入。

首次运行 Wrapper 需要网络以下载 Maven 和依赖。Windows 使用 `mvnw.cmd` 替换下文的 `./mvnw`；已安装 Maven 时也可直接使用 `mvn`。

## 快速启动

构建并执行测试：

```bash
./mvnw clean verify
```

运行可执行 JAR：

```bash
java -jar target/nexus-gateway.jar
```

也可以在开发时直接运行：

```bash
./mvnw compile exec:java
```

在 IDE 中选择 JDK 25+，导入 `pom.xml`，运行 `dev.nexus.gateway.GatewayApplication` 即可。

验证接口：

```bash
curl -i http://127.0.0.1:8080/
curl -i http://127.0.0.1:8080/health
curl -i http://127.0.0.1:8080/gateway/routes
curl -I http://127.0.0.1:8080/health
curl -i http://127.0.0.1:8080/missing
curl -i -X POST http://127.0.0.1:8080/health
```

使用 Ctrl+C 或发送 SIGTERM 停止服务。关闭过程停止监听、关闭客户端连接并等待事件循环退出；当前尚不提供在途请求排空机制。

## 配置

配置优先级：JVM 系统属性 > 环境变量 > 默认值。

| 系统属性 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `gateway.host` | `GATEWAY_HOST` | `0.0.0.0` | 监听地址 |
| `gateway.port` | `GATEWAY_PORT` | `8080` | 监听端口；0 表示由系统分配 |

示例：

```bash
java -Dgateway.host=127.0.0.1 -Dgateway.port=9090 -jar target/nexus-gateway.jar
GATEWAY_HOST=127.0.0.1 GATEWAY_PORT=9090 ./mvnw compile exec:java
```

配置无效或端口被占用时启动失败，并释放已创建的 Netty 资源。请求体上限当前由 `GatewayConfig.MAX_CONTENT_LENGTH` 常量控制。

## 代码结构与请求流程

```text
src/main/java/dev/nexus/gateway/
├── GatewayApplication.java          启动入口、关闭钩子
├── config/GatewayConfig.java        配置加载与校验
├── handler/HttpRequestHandler.java  本地接口、JSON 响应和 HTTP 状态处理
├── route/RouteRegistry.java         静态本地路由定义
└── server/
    ├── GatewayServer.java          监听、事件循环和连接生命周期
    └── HttpServerInitializer.java   HTTP pipeline 组装
src/test/java/dev/nexus/gateway/
├── GatewayServerTest.java           真实 HTTP 集成测试
└── GatewayConfigTest.java           配置优先级与格式校验
```

入站请求经过 `HttpServerCodec → HttpObjectAggregator → HttpRequestHandler`；出站响应沿 pipeline 反向流转，由 HTTP 编码器写回客户端。

- 一个 boss 事件循环负责接受连接，worker 事件循环负责网络读写。
- 使用 Netty 4.2 的 `MultiThreadIoEventLoopGroup` 与 `NioIoHandler`，当前固定使用 Java NIO transport。
- 聚合器将 HTTP 消息组装为 `FullHttpRequest`；处理器继承 `SimpleChannelInboundHandler`，回调结束后自动释放入站消息。
- 关闭钩子先关闭监听和客户端连接，再关闭事件循环；启动失败同样需要清理资源。

## 测试

```bash
./mvnw test
```

当前共 11 项测试。HTTP 测试绑定 `127.0.0.1` 的临时端口，验证接口和路由列表、查询参数、HEAD、404、405、413、Keep-Alive、端口冲突，以及关闭后端口释放和重复关闭；配置测试验证系统属性覆盖和非法端口校验。执行环境需要允许本地端口监听。

## 后续开发路线

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 项目骨架、最小 HTTP 服务、本地接口、基础测试 | ✅ 已实现 |
| 2 | Mock 后端、单条上游路由、最小反向代理闭环 | ❌ 未开始 |
| 3 | 路径前缀路由、请求/响应头处理、基础过滤器 | ❌ 未开始 |
| 4 | 上游超时、访问日志、错误日志、基础指标 | ❌ 未开始 |
| 5 | 限流、配置文件加载、基础配置热更新 | ❌ 未开始 |
| 6 | 上游连接池、简单鉴权、更多集成测试和压测 | ❌ 未开始 |

学习过程中优先关注 Netty 核心机制和小步演进：入站与出站事件、`ChannelFuture` 异步协作、`ByteBuf` 生命周期、连接关闭与背压。生产级高可用、完整插件体系、服务发现和运维控制台暂不作为优先目标。
