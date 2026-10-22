# AI 城市、区县解析与影院查询

用户输入地名，不需要提供数据库 ID。模型通过 `search_areas` 工具调用 `AreaSearchService`，
从本地 `area` 表取得真实 ID，再查询影院或场次；不会联网抓取、猜测或自动创建地区。
地区基础数据为空时，仅修改提示词不能让系统识别名称。

## 地区工具

`search_areas(keyword, level, parentId)`：

- `keyword` 为提取出的地名，去除首尾空白后长度为 1～32；不是整句用户消息。
- `level` 默认 1（城市），2 表示区县。沿用现有数据模型：0 省，1 市，2 区县。
- `parentId` 可选：城市的父级为省，区县的父级为城市，后端校验父级存在且层级正确。
- 先精确匹配名称及追加常见后缀的简称，例如“汕头/汕头市”“龙湖/龙湖区”。
  不直接删除名称中的“市/区/县”等字，避免破坏“市中区”等正式名称。
- 精确匹配无结果时做名称前缀匹配；SQL 通配符 `%`、`_` 和转义符按字面量转义。
- 返回最多 10 个候选，每项含 `id/name/level/parentId/parentName`；父地区一次批量查询，避免 N+1。
- `MATCHED`：系统内唯一候选；`AMBIGUOUS`：多个候选，需要用户确认；
  `NOT_FOUND`：地区未收录或名称需更正。`hasMore=true` 表示候选未列全，不可擅自选择第一项。

相同区县名可通过父城市消歧。模型可以利用 Redis 中的历史理解用户补充的城市，
但历史不是独立的、永久保留的地区状态；窗口之外的信息缺失时应重新询问。
地区 ID 不等于电影院、场次或库存：地区成功匹配后仍需实际查询对应业务数据。

## 影院与场次

`get_theaters(cityId, districtId)` 查询系统收录的影院名称和地址。后端验证城市、区县层级和归属，
查询限定城市，最多返回 20 家并提供 `hasMore`。不允许缺少城市时扫描全部影院。
旧影院列表 HTTP API 保持不变；AI 使用新增的有界查询方法。

例如“汕头龙湖有哪些影院”：先解析汕头市，再在该市下解析龙湖区，最后查询影院。
只查询影院不需要电影、日期或购票人数。模型使用现有五字段协议中的 `GENERAL` 回答，
电影和场次数组为空；不新增对外卡片类型。
查询为空只表示“本系统暂未收录该地区影院”，不代表现实中没有影院。

例如“汕头龙湖明天下午有什么可购场次”：解析地区、获取时间、确认电影后，
使用现有 `get_showtime_candidates` 查询，不锁座、不创建订单。
地区解析失败或有歧义时使用 `CLARIFICATION`，缺失字段为 `CITY` 或 `DISTRICT`；
不能套用需要电影/场次查询证据的 `NO_RESULTS`。

默认最多 6 轮工具往返、模型单次超时 30 秒、生成租约 4 分钟，
容纳城市、区县、时间、电影和场次的顺序查询。配置覆盖值仍需与模型耗时预算匹配。

## 地区基础数据

Flyway `V4__public_province_city_county_reference.sql` 固化江苏、福建、江西315条公开地区参考记录，
并创建地区名称/父级查询索引。新库不再导入单独的广东/汕头示例，也没有扩展广东全量数据。
原有的广东省、汕头市、龙湖区3条示例已按用户要求，在确认无引用并备份后单独清理，未修改其他业务数据。
来源日期、覆盖范围与验证样例见 [公开地区导入记录](public-area-reference.md)，仍非全国全量导入。
接口继续使用 `districtId` 表示区县，不新增重复的regionId字段。

手动验证：重启应用、登录并创建 AI 会话后，依次发送新 requestId 的消息：

1. “查询福州市的城市信息。”——从数据库解析 350100，而不是让用户提供 ID。
2. “查询福州市鼓楼区的信息。”——在福州市下解析 350102。
3. “福州鼓楼有哪些影院？”——查询系统收录情况；仅添加地区数据不会自动生成影院。
4. “福州鼓楼明天下午有哪些电影场次？”——仍受实际电影、影院和场次数据限制。

## 测试

```sh
./mvnw -o '-Dtest=AreaSearchServiceTest,TheaterServiceTest,AiResponseTest,AiWiringTest,AiConversationServiceTest,AiRedisFailureTest' test

RUN_MYSQL_INTEGRATION_TESTS=true RUN_REDIS_INTEGRATION_TESTS=true ./mvnw -o '-Dtest=AreaLookupMySqlIntegrationTest,AreaSearchServiceTest,TheaterServiceTest,Ai*Test' test
```

MySQL 测试使用项目现有的隔离测试数据库生命周期，只创建/删除自身随机命名的测试库，
验证真实 Flyway 迁移、JPQL、简称、同名消歧、前缀/通配符和影院过滤。
Redis 测试使用临时实例；模型由脚本或本机模拟接口替代，不请求真实模型，不消耗模型额度。
