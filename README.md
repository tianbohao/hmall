# hmall 黑马商城（微服务版）

## 项目简介

hmall 是一个基于 Spring Cloud Alibaba 的在线商城微服务项目，包含商品、搜索、购物车、用户、交易、支付和网关等模块。项目以 Nacos 作为注册中心和配置中心，使用 OpenFeign 完成服务调用，RabbitMQ 完成异步通知，Seata 处理订单链路的分布式事务，Elasticsearch 实现商品搜索。

项目主要演示微服务拆分、服务治理、分布式事务、消息驱动和 Elasticsearch 搜索等能力，适合学习和二次开发。

## 技术栈

- 基础框架：Spring Boot 2.7.12
- 微服务框架：Spring Cloud 2021.0.3、Spring Cloud Alibaba 2021.0.4.0
- 注册中心 / 配置中心：Nacos 2.x
- 网关：Spring Cloud Gateway
- 服务调用：OpenFeign + Spring Cloud LoadBalancer
- 持久层：MyBatis-Plus 3.4.3
- 数据库：MySQL 8.0+
- 消息队列：RabbitMQ 3.x（需安装 delayed-message 插件）
- 分布式事务：Seata 1.5.x（XA 模式）
- 搜索引擎：Elasticsearch 7.12.1 + IK 分词器
- 搜索可视化：Kibana 7.12.1
- 限流熔断：Sentinel（cart-service）
- 身份认证：JWT（RS256）
- 接口文档：Knife4j / Swagger
- 常用工具：Lombok、Hutool、Jackson
- 构建工具：Maven
- JDK：11

## 项目结构

```text
hmall/
├── hm-common/          # 公共模块（异常、返回结构、工具类、MQ、MyBatis、拦截器等）
├── hm-api/             # 微服务 API 模块（FeignClient、共享 DTO、降级实现）
├── hm-gateway/         # 网关服务（8080，JWT 鉴权、动态路由）
├── item-service/       # 商品服务（8081，商品 CRUD、库存、商品变更消息）
├── search-service/     # 搜索服务（8087，ES 搜索、聚合、竞价排名、索引同步）
├── cart-service/       # 购物车服务（8082）
├── user-service/       # 用户服务（8084，登录、余额、收货地址）
├── trade-service/      # 交易服务（8085，订单、库存扣减、延迟消息）
├── pay-service/        # 支付服务（8086，支付单、余额支付）
└── hm-service/         # 历史单体版本（非微服务运行必需）
```

服务端口：

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| hm-gateway | 8080 | 统一入口、鉴权、动态路由 |
| item-service | 8081 | 商品管理 |
| cart-service | 8082 | 购物车 |
| user-service | 8084 | 用户与地址 |
| trade-service | 8085 | 交易订单 |
| pay-service | 8086 | 支付 |
| search-service | 8087 | Elasticsearch 搜索 |
| hm-service | 8080 | 旧单体版本，不要与网关同时启动 |

## 模块详情

### hm-common

- 通用常量与枚举
- 统一响应结构 `R`
- 自定义异常与异常处理
- 用户上下文 `UserContext`
- RabbitMQ 工具封装与自动配置
- MyBatis-Plus 分页配置
- Jackson 配置
- 用户信息拦截器
- 通用工具类

### hm-api

- `ItemClient`：商品查询、库存扣减 / 恢复、按 id 查询商品
- `CartClient`：购物车清理
- `UserClient`：余额扣减
- `TradeClient`：订单状态更新
- `PayClient`：支付单查询
- 共享 DTO、Feign 降级实现、统一 Feign 配置

### hm-gateway

- 基于 Nacos 的动态路由加载
- JWT 登录校验
- 用户信息透传
- 路由配置来源：Nacos `gateway-routes.json`

### item-service

- 商品分页查询、按 id 批量查询、按 id 单条查询
- 商品新增、更新、上下架、删除
- 库存批量扣减与恢复
- 商品变更后发送 MQ 消息：`item.update` / `item.delete`

### search-service

- `GET /search/list`：商品搜索
- `POST /search/filters`：分类、品牌聚合
- 支持关键字、分类、品牌、价格区间、分页、排序
- 使用 `function_score` 让 `isAD=true` 的广告商品优先展示
- 监听 `item.topic`，增量同步商品数据到 Elasticsearch

### cart-service

- 添加商品到购物车
- 修改购物车数量
- 删除购物车商品
- 查询我的购物车
- 监听订单创建消息，清理已下单商品

### user-service

- 用户登录并签发 JWT
- 查询当前用户收货地址
- 根据 id 查询收货地址
- 余额扣减

### trade-service

- 创建订单
- 根据 id 查询订单
- 标记订单已支付
- 订单超时取消、恢复库存
- 使用 Seata `@GlobalTransactional` 处理下单链路
- 订单创建后发送延迟消息检测支付状态

### pay-service

- 生成支付单
- 基于用户余额支付
- 支付成功后发送 MQ 通知交易服务

### hm-service

- 课程前期的单体版本，保留作学习参考
- 与微服务版本功能有重叠，且端口与网关冲突，不建议同时启动

## 环境要求

- JDK 11
- Maven 3.6+
- MySQL 8.0+
- Nacos 2.x
- RabbitMQ 3.x（安装 `rabbitmq_delayed_message_exchange` 插件）
- Seata 1.5.x
- Elasticsearch 7.12.1（安装 IK 分词器）
- Kibana 7.12.1（可选）
- Sentinel Dashboard（可选，cart-service 使用）

## 快速开始

### 1. 克隆项目

```bash
git clone https://gitee.com/tianbohao/hmall.git
cd hmall
```

### 2. 准备基础设施

启动以下服务：

- MySQL
- Nacos
- RabbitMQ
- Seata
- Elasticsearch
- Kibana（可选）
- Sentinel Dashboard（可选）

RabbitMQ 需要创建虚拟主机和用户，默认配置为：

- virtual-host：`/hmall`
- username：`hmall`
- password：`123`

交易服务使用了延迟消息，需要确保 RabbitMQ 已安装 `rabbitmq_delayed_message_exchange` 插件。

### 3. 创建数据库

创建以下数据库并导入对应表结构和初始化数据：

| 数据库 | 主要表 |
| --- | --- |
| hm-item | item |
| hm-cart | cart |
| hm-user | user、address |
| hm-trade | order、order_detail、order_logistics |
| hm-pay | pay_order |

如果还需要运行旧单体 `hm-service`，额外准备 `hmall` 数据库。

### 4. 配置 Nacos

各服务的 `bootstrap.yaml` 中已经配置了 Nacos 地址和共享配置，请按实际环境修改：

```yaml
spring:
  cloud:
    nacos:
      server-addr: 你的Nacos地址:8848
```

需要准备以下 Nacos 配置：

| dataId | 用途 |
| --- | --- |
| shared-jdbc.yaml | 数据源和 MyBatis-Plus 公共配置 |
| shared-log.yaml | 日志配置 |
| shared-swagger.yaml | Knife4j 接口文档配置 |
| shared-seata.yaml | Seata 注册中心和事务组配置 |
| shared-mq.yaml | RabbitMQ 连接配置 |
| cart-service.yaml | 购物车业务配置 |
| gateway-routes.json | 网关动态路由配置 |

网关路由示例：

```json
[
  {
    "id": "item",
    "predicates": [{ "name": "Path", "args": { "_genkey_0": "/items/**" } }],
    "filters": [],
    "uri": "lb://item-service"
  },
  {
    "id": "search",
    "predicates": [{ "name": "Path", "args": { "_genkey_0": "/search/**" } }],
    "filters": [],
    "uri": "lb://search-service"
  },
  {
    "id": "cart",
    "predicates": [{ "name": "Path", "args": { "_genkey_0": "/carts/**" } }],
    "filters": [],
    "uri": "lb://cart-service"
  },
  {
    "id": "user",
    "predicates": [{ "name": "Path", "args": { "_genkey_0": "/users/**", "_genkey_1": "/addresses/**" } }],
    "filters": [],
    "uri": "lb://user-service"
  },
  {
    "id": "trade",
    "predicates": [{ "name": "Path", "args": { "_genkey_0": "/orders/**" } }],
    "filters": [],
    "uri": "lb://trade-service"
  },
  {
    "id": "pay",
    "predicates": [{ "name": "Path", "args": { "_genkey_0": "/pay-orders/**" } }],
    "filters": [],
    "uri": "lb://pay-service"
  }
]
```

### 5. 初始化 Elasticsearch 索引

在 Kibana Dev Tools 或任意 HTTP 客户端中创建 `items` 索引：

```json
PUT /items
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "name": { "type": "text", "analyzer": "ik_max_word" },
      "price": { "type": "integer" },
      "image": { "type": "keyword", "index": false },
      "category": { "type": "keyword" },
      "brand": { "type": "keyword" },
      "sold": { "type": "integer" },
      "commentCount": { "type": "integer" },
      "isAD": { "type": "boolean" },
      "updateTime": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis"
      }
    }
  }
}
```

将 `item` 表中状态正常的商品导入 `items` 索引。之后商品的新增、修改、上下架、删除会通过 RabbitMQ 增量同步到索引，搜索服务本身不负责全量数据导入。

### 6. 构建并运行

```bash
mvn clean package -DskipTests
```

按需启动各服务。Windows 本机运行时需要使用 `local` profile，避免默认 `dev` profile 中的 MySQL 主机名 `mysql` 无法解析：

```bash
java -jar item-service/target/item-service.jar --spring.profiles.active=local
java -jar search-service/target/search-service.jar
java -jar cart-service/target/cart-service.jar --spring.profiles.active=local
java -jar user-service/target/user-service.jar --spring.profiles.active=local
java -jar trade-service/target/trade-service.jar --spring.profiles.active=local
java -jar pay-service/target/pay-service.jar --spring.profiles.active=local
java -jar hm-gateway/target/hm-gateway.jar
```

也可以在 IDEA 中直接运行各服务的启动类：

- `com.hmall.item.ItemApplication`
- `com.hmall.search.SearchApplication`
- `com.hmall.cart.CartApplication`
- `com.hmall.user.UserApplication`
- `com.hmall.trade.TradeApplication`
- `com.hmall.pay.PayApplication`
- `com.hmall.gateway.GatewayApplication`

### 7. 访问服务

- 网关入口：`http://localhost:8080`
- 商品服务接口文档：`http://localhost:8081/doc.html`
- 购物车服务接口文档：`http://localhost:8082/doc.html`
- 用户服务接口文档：`http://localhost:8084/doc.html`
- 交易服务接口文档：`http://localhost:8085/doc.html`
- 支付服务接口文档：`http://localhost:8086/doc.html`
- 搜索服务接口文档：`http://localhost:8087/doc.html`
- Kibana：`http://你的ES所在主机:5601`

前端项目未包含在本仓库中，网关只提供后端 API。

## 主要功能模块

### 商品与搜索

- 商品分页查询、详情查询
- 商品新增、修改、上下架、删除
- 商品库存扣减与恢复
- 关键字搜索商品名称
- 按分类、品牌、价格区间过滤
- 按价格、销量、评论数等字段排序
- 分类、品牌聚合，返回当前搜索结果中可用的过滤项
- 广告商品竞价排名：`isAD=true` 的商品默认排在前面

搜索接口示例：

```http
GET http://localhost:8080/search/list?key=电视&pageNo=1&pageSize=20&sortBy=price&isAsc=false&category=曲面电视&brand=华为&minPrice=90000&maxPrice=159900
```

```http
POST http://localhost:8080/search/filters
Content-Type: application/json

{
  "key": "电视",
  "pageNo": 1,
  "pageSize": 20,
  "sortBy": "",
  "isAsc": false
}
```

返回示例：

```json
{
  "category": ["曲面电视"],
  "brand": ["华为"]
}
```

### 购物车

- 添加商品到购物车
- 修改购物车商品数量
- 删除购物车商品
- 查询当前用户购物车
- 下单后通过 MQ 清理购物车

### 用户与地址

- 用户登录并返回 JWT
- 查询当前用户地址列表
- 根据 id 查询地址
- 余额扣减

### 交易与支付

- 创建订单
- 订单查询
- 订单超时取消
- 扣减和恢复库存
- 生成支付单
- 余额支付
- 支付成功消息通知

### 微服务基础设施

- Nacos 服务注册与配置管理
- Spring Cloud Gateway 统一网关
- OpenFeign 服务调用
- RabbitMQ 异步消息
- Seata 分布式事务
- Sentinel 限流熔断（购物车服务）
- Knife4j 接口文档

## 开发规范

- 使用 RESTful API 设计风格
- 提供 `R`/`PageDTO` 等通用返回结构
- 使用 `hm-common` 中的异常和工具类
- 服务间调用统一通过 `hm-api` 中的 FeignClient
- 数据库访问使用 MyBatis-Plus
- 增删改商品时通过 MQ 同步搜索索引
- 跨服务事务使用 Seata 全局事务
- 异步通知使用 RabbitMQ

## 部署说明

生产环境部署时，请重点检查以下内容：

- 修改 JWT 密钥库密码和默认密钥，使用更安全的密钥
- 配置 HTTPS 证书
- 修改 MySQL、RabbitMQ、Nacos、Seata 的默认账号密码
- 根据并发量调整数据库连接池参数
- 为 RabbitMQ 配置持久化和监控，关注 `.error.queue` 中的失败消息
- 为 Elasticsearch 配置持久化存储、快照备份和磁盘水位监控
- 单节点 ES 建议将索引副本数设置为 0，避免集群长期处于 yellow 状态
- 备份 Nacos 中的共享配置和 `gateway-routes.json`
- 生产环境将 `com.hmall` 日志级别调整为 `info`，Feign 日志调整为 `basic`
- 配置日志滚动策略，避免日志文件持续增长
- 不要同时启动 `hm-service` 和 `hm-gateway`，两者都使用 8080 端口
- 如果服务需要跨主机部署，统一配置 `spring.cloud.inetutils.preferred-networks`，避免注册到 VMware 虚拟网卡地址

## 已知限制

- `search-service` 只负责增量同步，不包含全量数据导入功能，首次部署需要自行导入商品索引数据
- Elasticsearch 索引使用 IK 分词器，未安装 IK 插件时需要调整索引 mapping 或安装插件
- 交易服务依赖 RabbitMQ 延迟消息插件和 Seata 服务
- 前端项目未包含在本仓库中

## 贡献指南

欢迎提交 Issue 和 Pull Request 来改进本项目。

## 许可证

本项目仅供学习交流使用。
