# Redis 临时多轮对话

会话仅保存在 Redis，通过闲置 TTL 管理，不调用 `AiChatSessionRepository`、
`AiChatTurnRepository`，不执行同步或异步落库。Redis 数据过期、丢失后无法恢复历史。
已有 MySQL 对话表保留给后续迭代，本次无需新增数据库迁移。

## 接口

以下接口均要求登录，归属身份取自服务端认证后的 Principal，前端不提交用户 ID。
当前系统的登录主体使用唯一用户名，因此 Redis 会话的 owner 也使用认证用户名。

### 创建会话

```http
POST /api/v1/ai/sessions
Authorization: Bearer <token>
```

返回 HTTP 201：

```json
{"code":200,"message":"success","data":{
  "sessionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "expiresAt":"2026-10-18T04:30:00Z",
  "turns":[]
}}
```

会话编号由服务端生成 UUID。创建失败时不会调用模型。

### 发送消息

```http
POST /api/v1/ai
Authorization: Bearer <token>
Content-Type: application/json

{"sessionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
 "requestId":"client-request-001",
 "userMessage":"我想在杭州看电影"}
```

后续追问使用相同 sessionId、新 requestId，只需发送本轮消息。
requestId 最长 128 字符，支持字母、数字、下划线和连字符；消息长度 1～4000 字符，不能全空白。
响应的 `data` 调整为：

```json
{"sessionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
 "requestId":"client-request-001",
 "expiresAt":"2026-10-18T04:30:00Z",
 "response":{"schemaVersion":2,"type":"GENERAL",
   "blocks":[{"type":"TEXT","id":"text-0","data":{"text":"回答内容"}}],
   "showtimeQueries":[]}}
```

原有结构化响应内容位于 `data.response`，结构及工具证据校验保持不变。
同一会话中，已完成的相同 requestId 与相同消息直接返回已保存结果，不重新调用模型；
相同 requestId 携带不同消息返回 HTTP 409。
模型失败的本轮不写入历史，可使用原 requestId 重试。
如果 Redis 写入结果在网络中丢失，客户端也应使用原 requestId 重试，以获取已提交结果。

### 读取历史与主动清理

- `GET /api/v1/ai/sessions/{sessionId}`：返回 expiresAt 和已完成 turns；每轮包含 requestId、
  userMessage、结构化 response 和 completedAt，不返回系统提示词或内部工具消息。
- `DELETE /api/v1/ai/sessions/{sessionId}`：返回 HTTP 204；只删除当前用户拥有的会话及生成租约。
  会话不存在或属于其他用户时也返回 204，但不会删除其他用户的数据。
- 历史查询、发送消息对不存在/已过期/其他用户的会话统一返回 HTTP 404，避免泄露归属。
- 前端退出通知不是必需步骤；即使没有发送 DELETE，也由 Redis TTL 清理。

## 存储和并发

使用同一 Redis Cluster hash slot 中的两个 Key：

```text
ai:session:{sessionId}       Hash：owner、version、data
ai:session:{sessionId}:lock  String：生成租约的随机 token
```

data 包含 LangChain4j 消息 JSON 和已完成的展示历史。
模型上下文默认最多 40 条消息（包含系统、助手、用户和工具消息），使用
MessageWindowChatMemory 清理旧消息及失去对应调用的工具结果。
每次调用都建立独立的记忆副本，只有回答通过结构、工具证据及业务校验后才提交至 Redis。
没有全局共享的 ChatMemory，也不会长期在 JVM 缓存已过期会话。

- 默认闲置 TTL 30 分钟。合法请求取得生成租约时续期，成功提交时再次续期；读取历史不续期。
- 同一会话同一时间仅允许一个有效生成租约；其他请求返回 HTTP 409，不排队等待模型。
- 默认生成租约 4 分钟。模型超时 30 秒、禁用自动模型重试，最多 6 轮工具调用往返，
  为城市解析、区县解析、时间、电影和场次的完整查询链预留空间。
  若整体生成超过租约，结果不能再写回；锁过期允许新的请求接管。
- 提交脚本同时检查会话存在、owner、token、version，避免旧请求覆盖新结果。
- 会话过期或被删除后，晚到回答不能重新创建会话。旧持有者释放锁也不会删除新持有者的锁。
- 模型窗口与历史保留是两个限制：默认保存最多 50 个完整回合用于展示与幂等重放。
  达到回合上限，或 Redis 文档超过 512 KiB 时返回 HTTP 409，客户端应创建新会话。
  不静默删除幂等记录以继续接受旧请求。
- Redis 不可用时返回 HTTP 503，不降级到跨实例不一致的进程内记忆，也不会继续消耗模型调用。
- 历史内容用于理解追问，不是最新库存依据。模型推荐的资源仍必须来自本轮真实工具结果。

这里只保证临时会话语义。Redis 重启后的保留能力取决于部署的持久化配置，不承诺持久历史。
消息条数限制也不等于精确 Token 限制；较大的工具结果仍需结合模型上下文预算控制。

## 配置

```properties
ai.conversation.ttl=30m
ai.conversation.lock-ttl=4m
ai.conversation.max-messages=40
ai.conversation.max-turns=50
ai.conversation.max-session-bytes=524288
ai.conversation.max-tool-round-trips=6
ai.conversation.model-timeout=30s
```

参数有正数及上限校验，生成租约必须短于会话 TTL。使用默认值即可，无需修改本地私有配置。

## 验证

离线单元测试使用脚本化模型，不访问真实模型 API：

```sh
./mvnw -o '-Dtest=AiResponseTest,AiWiringTest,AiConversationServiceTest,AiRedisFailureTest,OrderHttpStatusTest' test
```

真实 Redis 测试启动随机端口的临时 Redis（不读取或清理开发机 Redis 数据），
覆盖多轮上下文恢复、跨用户隔离、幂等、并发租约、TTL、删除及过期后的晚到提交、容量限制、HTTP 生命周期：

```sh
RUN_REDIS_INTEGRATION_TESTS=true ./mvnw -o '-Dtest=AiRedisConversationIntegrationTest' test
```

需要 `redis-server` 可执行程序，可通过 `REDIS_SERVER_EXECUTABLE` 指定绝对路径。
测试结束会停止该临时进程；不需要 MySQL 或真实模型密钥。
