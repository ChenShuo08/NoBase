# NoBase Agent Harness

> 本目录记录 NoBase AI 后端助手所依赖的 agent harness 设计与集成方式。  
> 当前公开仓库只放架构说明和接入文档，避免把平台内部服务细节直接暴露出去。

## 概览

NoBase 的 agent harness 负责把聊天消息转成可审计的执行计划，调用内置工具或 MCP 工具，把部分结果流式返回给前端，并在高风险操作前要求显式确认。

这里没有把模型当成“只会打字”的生成器，而是通过自定义 `CONTROL` 协议嵌在普通聊天流里。模型如果想触发工具，就输出控制行：

```text
CONTROL {"action":"list_projects","arguments":{}}
CONTROL {"action":"execute_project_sql","arguments":{"project":"demo","sql":"SELECT 1"}}
```

如果整段输出里没有合法控制行，harness 会安全返回兜底文案，而不是误执行任何东西。这个约束保证了“没理解清楚就先不动”。

## 本 README 覆盖内容

- agent 架构概览
- 控制流式协议
- 工具注册表与风险模型
- MCP 工具桥接
- 确认卡流程
- 前端对接方式
- 后续开源路径

## 架构

```text
Frontend Agent Page
    |
    v
AgentChatController
    |
    v
AgentLlmOrchestrationService
    |
    +-- LLM 流式输出 / CONTROL 解析
    |
    +-- AgentChatService
    |       |
    |       +-- 内置工具执行
    |       +-- AgentSessionService
    |       +-- AgentAuditService
    |       +-- AgentKnowledgeService
    |
    +-- AgentMcpToolBridge
            |
            +-- database / auth / storage / function / cron / memory / ai / deployment
```

## 核心流程

1. 前端发起一个流式会话。
2. 后端组装系统提示、历史消息、memory 和项目上下文。
3. orchestration 服务把消息发给模型。
4. 流式返回过程中，后端持续扫描 `CONTROL` 行。
5. 命中控制行后，harness 解析参数、查工具表、做权限校验并执行。
6. 工具结果会被结构化地回灌到聊天流里。
7. 如果是高风险工具，就创建确认卡并暂停执行，等用户批准。
8. 会话 ledger 记录每一步操作，方便审计和复盘。

## CONTROL 协议

`CONTROL` 必须单独占一行，前缀是 `CONTROL`，后面紧跟一个 JSON 对象。harness 在模型输出流里解析它，而不是把它直接展示给用户。

模型输出示例：

```text
先想一下怎么查询项目列表...

CONTROL {"action":"list_projects","arguments":{}}

下面是结果。
```

后端随后会返回结构化事件，例如：

```json
{"event":"status","text":"查询项目中..."}
{"event":"data","tool":"list_projects","result":[{"id":"1","name":"demo"}]}
{"event":"done"}
```

这种协议的好处是：前端可以稳定渲染思考中、执行中、工具结果、确认卡，而不用靠猜 prompt 内容。

## 工具注册表

每个工具在注册表里至少声明：

- 动作名
- 描述
- 风险等级
- 所需角色
- 参数 schema
- 是否需要确认
- 执行处理器

内置工具常见分类：

- `create_project`
- `list_projects`
- `execute_project_sql`
- `reset_user_password`
- schema 查询工具
- metadata 查询工具

MCP 工具常见分类：

- database
- auth
- storage
- function
- cron
- memory
- ai gateway
- deployment

## 风险模型

不是所有工具都能直接放行。代码里至少区分三类风险：

- 只读操作
- 写操作
- 破坏性操作

写操作和破坏性操作通常都需要走确认卡。确认状态被持久化在会话之外，因此流中断、页面刷新后仍可恢复。

## MCP 工具桥

`AgentMcpToolBridge` 把 harness 工具调用映射成平台服务调用。它只把“模型需要看到的、安全的元数据”暴露给上下文，不会把 `service_role` 这类高权限密钥发给模型。

桥接层还会把异常标准化成结构化工具结果，方便模型自修复，而不是直接静默失败。

## 确认卡流程

```text
用户触发高风险工具
    |
    v
后端创建确认记录
    |
    v
前端展示确认卡片
    |
    +-- approve -> 执行并继续
    +-- reject  -> 向流里返回拒绝事件
    +-- cancel  -> 结束本次工具流程
```

确认记录会保留原始参数、工具元数据、请求人和时间戳，所以 agent 行为是可审计、可回放的。

## 前端对接

前端 agent 页面消费结构化事件，并维护这些本地状态：

- 消息气泡
- thinking 区块
- 状态提示
- 工具结果卡片
- 确认卡
- 错误兜底

后端只要保持事件名稳定，前端就不需要根据文本语义去猜。

## 开源范围

这次先在公开仓库里开放“设计 + 协议 + 接入说明”，不直接暴露完整内部实现。后续可以逐步放开：

- 独立 harness 接口定义
- 内置工具最小实现
- 前端集成包
- 本地 MCP bridge demo
- agent 测试 harness

## 与本仓库代码对照

当前仓库里已保留的 agent 相关实现位于：

- 后端入口与控制器：`AgentChatController`
- 流式编排与 CONTROL 解析：`AgentLlmOrchestrationService`、`AgentControlParser`
- 内置工具执行：`AgentChatService`
- 工具注册：`AgentToolRegistry`
- 上下文组装：`AgentContextAssembler`
- MCP 桥接：`AgentMcpToolBridge`
- 审计与会话：`AgentAuditService`、`AgentSessionService`
- 前端页面：`frontend/apps/studio/src/app/(app)/agent/page.tsx`
- 前端 API：`frontend/apps/studio/src/lib/api.ts`

如果你要基于这份 README 继续开源 agent harness，建议优先抽离：

- `CONTROL` 协议和流式 gate
- tool registry schema
- confirmation CRUD
- frontend event contract

## 参与方式

1. 先设计 control protocol contract
2. 实现 streaming controller
3. 做 tool registry 与 risk level
4. 加 risky tool confirmation flow
5. 只向模型暴露安全 metadata
6. 记录所有 tool execution 用于审计
