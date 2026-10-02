# 数据库脚本

执行顺序按文件名前缀的版本号：

| 文件 | 内容 |
| --- | --- |
| `V1__schema.sql` | Code Review 业务表 |

```bash
mysql -uroot -p < sql/V1__schema.sql
```

## 业务表

| 表 | 用途 |
| --- | --- |
| `sys_user` | 注册用户 |
| `scm_credential` | GitHub / GitLab 凭证（加密存储，本期只建表） |
| `code_repository` | 用户添加的仓库，`source_type` 区分 LOCAL / GITHUB / GITLAB |
| `llm_model_config` | 用户自己的 OpenAI 兼容模型配置，API Key 加密存储 |
| `review_task` | 一次审查任务（一个任务对应一次 Agent 审查会话，含计划结果与完成轮数） |
| `review_comment` | 审查发现；被误报过滤剔除的记为 `FILTERED`，保留以便追溯 |
| `webhook_event` | Webhook 投递记录，用 `(provider, delivery_id)` 去重（本期只建表） |

## AgentScope 自动创建的表

以下表由 AgentScope 在运行时自动创建，**不在本目录维护**：

| 表 | 来源 | 说明 |
| --- | --- | --- |
| `agentscope_sessions` | `MysqlAgentStateStore`（`agentscope-extensions-mysql`） | Agent 会话状态（上下文消息等）。应用以 `createIfNotExist=true` 构造，首次写入前自动建表，库为 `code_as_review` |

会话的 `session_id` 列由 AgentScope 存为 `userId:sessionId`；本项目的 sessionId 格式为 `review-{taskId}:{phase}`（`phase` 为 `plan`、`main-r{轮次}`、`filter-r{轮次}`），可据此关联 `review_task`。
