# AI 结构化响应

先调用 `POST /api/v1/ai/sessions` 创建临时会话，再以登录用户身份请求 `POST /api/v1/ai`：

```json
{"sessionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","requestId":"request-001","userMessage":"推荐一部正在上映的电影"}
```

消息不能为空白，最长 4000 字符。调用链：
AiController → AiApplicationService → Redis 会话租约 → AiAssistantFactory → MovieAssistant → AiTools。
回答通过 AiResponseAssembler 校验后，原子写回 Redis。会话生命周期见 [Redis 临时多轮对话](ai-redis-conversation.md)。
自然语言地名通过 `search_areas` 查询真实 ID，再调用 `get_theaters` 或场次工具，
匹配与歧义处理见 [城市、区县解析](ai-area-lookup.md)。

MovieAssistant 返回 Result<AiAnswerDTO>。模型只生成回答类型、文字、推荐资源 ID、
推荐理由和缺失条件；应用层读取该 Result 的实际工具执行结果，校验 ID 后组装 blocks。
本次调用的数据保存在局部变量中，不共享用户间的工具结果。

成功响应的 `data.response` 内容示例（追问）；外层 `data` 还包含 sessionId、requestId 和 expiresAt：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "requestId": "request-001",
    "expiresAt": "2026-10-18T04:30:00Z",
    "response": {
      "schemaVersion": 2,
      "type": "CLARIFICATION",
      "blocks": [
        {"type": "TEXT", "id": "text-0", "data": {"text": "你想在哪个城市观看？"}},
        {"type": "INPUT_REQUEST", "id": "input-0", "data": {"missingFields": ["CITY"]}}
      ],
      "showtimeQueries": []
    }
  }
}
```

response 中的 type 表示本轮主要回答类型：MOVIE_RECOMMENDATION、SHOWTIME_OPTIONS、
CLARIFICATION、NO_RESULTS、GENERAL。blocks 是有序数组，各 block 的 id 在本轮响应内稳定，
便于将来流式追加 TEXT 内容；当前接口仍一次性返回完整 JSON，不提供流式输出。
目前的 block 类型及 data：

- TEXT：`{text}`，原模型 message；
- MOVIE_CARD：`{movie: ShowingMovieVO, reason}`；
- SHOWTIME_CARD：`{showtime: ShowtimeCandidateDTO, reason}`；
- INPUT_REQUEST：`{missingFields: [...]}`，只在追问时出现；
- NOTICE：`{text}`，场次查询的库存提示。

电影详情和场次价格、余座、地址均取自实际工具返回对象。一个 CLARIFICATION
响应也可以包含电影或场次卡片。模型的 `AiAnswerDTO` 与对外协议分开：模型不直接生成 block
或资源详情，由服务端验证后组装。

showtimeQueries 为每次实际场次查询的元数据：
toolCallId、page、size、hasMore、returnedShowtimeIds。
它不是卡片数组的分页；模型可能只推荐某页中的一部分候选。
同一资源多次返回时，卡片使用最后收到的工具结果。
存在场次查询时，NOTICE 固定提示不锁座、不创建订单及余座不保证连座。

模型返回未知或重复 ID、违反回答类型约束、缺少字段、无查询证据的 NO_RESULTS、
非法 JSON，或者工具/模型请求失败时返回 HTTP 500 和通用错误说明。
请求参数错误返回 HTTP 400。原始模型错误不会通过接口暴露。
NO_RESULTS 要求实际查询为空；如果调用过场次工具，以本次场次查询结果判断，
否则以电影查询结果判断。多个场次查询中只要有候选，就不能宣称全部无结果。

模型配置使用 JSON Mode（`response_format.type=json_object`），不再声明 JSON Schema 能力。
配置的模型及兼容 API 服务必须支持 JSON Mode 与工具调用；提示词指定字段，
LangChain4j 解析为 AiAnswerDTO 后，由 Bean Validation 和 AiResponseAssembler 校验字段与业务规则。
JSON Mode 只约束 JSON 语法，不保证字段结构或事实正确；不合规结果仍返回错误，不降级为任意文本。
前端应把 TEXT 当普通文本安全渲染，
以服务端卡片作为票价和余座的展示依据；下单仍需再次校验库存。

V2 Flyway 迁移创建 ai_chat_session 和 ai_chat_turn。Session 通过随机 UUID 关联用户，
保留业务状态 JSON、状态、版本及时间；Turn 保存请求幂等 ID、用户消息、处理状态、
响应类型、协议版本与最终响应 JSON。仓库方法按用户 ID 限定会话和历史查询。
这些表和仓库仍未接入聊天请求流程；当前采用 Redis TTL 管理临时会话，
按请求建立独立的 LangChain4j ChatMemory 副本恢复多轮上下文。
客户端后续请求只发送本轮消息和会话/请求编号，地区 ID 仍需来自已知上下文。

离线验证（不请求真实模型）：

```sh
./mvnw -Dtest=AiModelConfigTest,AiResponseTest,AiWiringTest,AiConversationServiceTest,AiRedisFailureTest test
```

AiResponseTest 使用脚本化 ChatModel，实际执行 LangChain4j 的工具调用、Schema 生成、
JSON 解析、应用层校验与 HTTP 序列化，覆盖跨请求数据隔离。
AiModelConfigTest 使用真实模型配置连接本机随机端口的临时模拟接口，检查实际 HTTP 请求
携带 `response_format: {"type":"json_object"}`，并覆盖普通回答解析、工具调用后继续保持
JSON Mode 和非法业务字段拒绝；不访问真实模型、不使用真实密钥、不消耗额度。
