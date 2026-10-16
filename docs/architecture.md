# 项目架构说明

本项目采用按业务模块组织的模块化单体结构。一级包表示业务能力，模块内部再按职责划分 Controller、Service、Mapper、Domain 和 DTO。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| `auth` | 注册、登录、Token、Spring Security 配置和当前用户解析 |
| `user` | 用户实体、用户持久化以及用户账号查询服务 |
| `repo` | 代码仓库登记、归属校验和仓库管理接口 |
| `llm` | 用户模型配置、默认模型解析和模型实例创建 |
| `review` | 评审任务编排、Diff 处理、Agent、评论收集和结果发布 |
| `scm` | Git/SCM Provider、工作区准备和凭据持久化；GitHub 使用持久 mirror + 任务 detached worktree |
| `webhook` | 外部代码托管平台 Webhook 入口 |
| `rag` | Issue/PR 内容同步、Milvus/ES 混合检索、Agent 核验和关系写回 |
| `common` | 跨业务复用的响应、异常、锁、加密和持久化配置 |

## 依赖方向

```text
controller -> service
service    -> domain / mapper / infrastructure
mapper     -> domain
agent/diff/comment -> domain 或值对象
common     -> 不依赖具体业务模块
```

Controller 不直接访问 Mapper。跨模块访问通过对方的 Service 或明确的基础设施接口完成。例如评审任务通过 `RepoService` 获取仓库，认证通过 `UserAccountService` 访问用户。

`review/agent`、`review/diff`、`review/comment`、`review/publish` 和 `scm/local` 属于具有独立职责的组件，不强行放入通用三层目录。

## 新增功能的放置规则

- 新 HTTP 接口放在对应模块的 `controller`，业务流程放在 `service`。
- 新数据库实体放在模块的 `domain`，Mapper 放在 `mapper`。
- 请求和响应对象放在模块的 `dto`，不要直接暴露数据库实体。
- 与评审算法有关的代码放在 `review/agent`、`review/diff` 或 `review/comment`。
- 与外部代码托管平台交互的代码放在 `scm` 的 Provider 或适配器目录。
- 真正跨模块的基础设施才放到 `common`。

## 评审触发方式

评论区触发支持 `/命令`（如 `/review`、`/review high`）：

- `/命令` 只需判断评论是否以命令开头，参数可以直接跟在后面，不易误触发；

两者都来自同一类 Webhook 事件（Issue / PR 评论），由 `webhook` 模块验证签名、按 delivery id 去重并创建任务。PR 评论命令创建
`trigger_type = WEBHOOK_COMMAND` 的评审任务；Issue 事件在当前阶段写入待处理的 `issue_investigation_task`，等待后续复现执行器。

GitHub 的自动事件默认包括 Pull Request `opened` 和 `synchronize`。事件规则存放在
`repository_trigger_rule`，可以按仓库关闭或指定 effort/model。Webhook Secret 加密存放在
`code_repository.webhook_secret_cipher`，GitHub PAT 加密存放在 `scm_credential`。

## Issue/PR RAG 关联

启用 `code-review.rag.enabled` 后，GitHub Issue、PR 和普通讨论评论会异步写入
`rag_item`/`rag_chunk`，正文按一条内容切块，评论按单条切块。Milvus 负责 dense 向量召回，
ES 通过 `code-review.rag.elasticsearch.enabled` 作为可选 BM25 召回；应用层按父 Issue/PR 去重
并用 RRF 合并排名，随后由无工具 AgentScope Agent 对 Top 3 原始 GitHub 对象做最终核验。
关系保存到 `rag_relation`，并在关联双方维护带 marker 的幂等汇总评论。已有仓库通过
`POST /api/repositories/{id}/rag/sync` 手动全量同步，之后由 `issues`、`pull_request` 和
`issue_comment` Webhook 增量更新。
