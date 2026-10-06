# 数据库脚本

项目当前使用一个完整的建表脚本：

| 文件 | 内容 |
| --- | --- |
| `V1__schema.sql` | Code Review 全部业务表，包含 GitHub Webhook 和凭据备注字段 |

```bash
mysql -uroot -p < sql/V1__schema.sql
```

## 业务表

| 表 | 用途 |
| --- | --- |
| `sys_user` | 注册用户 |
| `scm_credential` | GitHub PAT 等 SCM 凭证（加密存储，支持备注） |
| `code_repository` | 用户添加的仓库，`source_type` 区分 LOCAL / GITHUB / GITLAB |
| `llm_model_config` | 用户自己的 OpenAI 兼容模型配置，API Key 加密存储 |
| `review_task` | 一次审查任务（一个任务对应一次 Agent 审查会话，含计划结果与完成轮数） |
| `review_comment` | 审查发现；被误报过滤剔除的记为 `FILTERED`，保留以便追溯 |
| `webhook_event` | GitHub Webhook 投递记录，用 `(provider, delivery_id)` 去重 |
| `repository_trigger_rule` | 每个仓库的 GitHub 自动事件和 `/review` 命令规则 |
| `issue_investigation_task` | Issue 自动事件产生的待处理任务；当前不会进入 Agent 执行器 |

## AgentScope 自动创建的表

以下表由 AgentScope 在运行时自动创建，**不在本目录维护**：

| 表 | 来源 | 说明 |
| --- | --- | --- |
| `agentscope_sessions` | `MysqlAgentStateStore`（`agentscope-extensions-mysql`） | Agent 会话状态（上下文消息等）。应用以 `createIfNotExist=true` 构造，首次写入前自动建表，库为 `code_as_review` |

会话的 `session_id` 列由 AgentScope 存为 `userId:sessionId`；本项目的 sessionId 格式为 `review-{taskId}:{phase}`（`phase` 为 `plan`、`main-r{轮次}`、`filter-r{轮次}`），可据此关联 `review_task`。

## GitHub Webhook

执行 `V1__schema.sql` 后，用户可以在控制台为 GitHub 仓库生成 Secret 和查看 Webhook URL，
再手动在 GitHub 仓库设置中创建 Webhook。服务端验证 `X-Hub-Signature-256`，并以
`X-GitHub-Delivery` 做幂等去重。远程镜像和任务 worktree 默认位于
`code-review.workspace-root` 下的 `repositories/{repositoryId}/mirror.git` 和
`tasks/{taskId}/worktree`。
