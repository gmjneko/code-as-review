# Console

Code as Review 的 Web 控制台：模型配置、仓库接入、评审任务的管理界面。

## 快速开始

需要 Node.js ≥ 22.12 和 pnpm 11（版本见 `package.json` 的 `packageManager`）。

```bash
pnpm install
pnpm dev        # http://localhost:5173，/api 代理到 CONSOLE_API_PROXY_TARGET（默认 http://localhost:8080）
```

本机覆盖配置写在 `.env.local`（不入库），可用变量见 `.env`。

## 常用命令

| 命令                       | 作用                                                    |
| -------------------------- | ------------------------------------------------------- |
| `pnpm dev`                 | 启动开发服务器（HMR，修改路由文件时自动重新生成路由树） |
| `pnpm build`               | 类型检查 + 生产构建，产物在 `dist/`                     |
| `pnpm preview`             | 本地预览生产构建                                        |
| `pnpm check`               | **提交前必跑**：类型检查 + lint + 格式检查 + 单元测试   |
| `pnpm typecheck`           | 重新生成路由树并执行 `tsc -b`                           |
| `pnpm lint` / `lint:fix`   | Oxlint（含类型感知规则，warning 视为失败）              |
| `pnpm format`              | Prettier 格式化                                         |
| `pnpm test` / `test:watch` | Vitest 单元 / 组件测试                                  |

## 技术栈

| 关注点      | 选型                                                                      |
| ----------- | ------------------------------------------------------------------------- |
| 构建 / 语言 | Vite 8、TypeScript 6（strict）、React 19                                  |
| UI          | antd 6 + `@ant-design/pro-components` 3（ProLayout / ProTable / ProForm） |
| 路由        | TanStack Router（文件路由、类型安全的 path / params / search）            |
| 服务端状态  | TanStack Query 5                                                          |
| 客户端状态  | Zustand（目前只有登录会话）                                               |
| 校验        | Zod（URL search 参数）                                                    |
| 质量        | Oxlint（type-aware）、Prettier、Vitest + Testing Library                  |

### 为什么是 antd + ProComponents，而不是 Ant Design Pro？

Ant Design Pro 6 是一个基于 **Umi Max** 的脚手架（`max dev` / `max build`），自带路由、请求、权限、数据流等一整套约定。
它和 Vite 不兼容，而且这些能力我们已经用更轻、类型更强的库实现了（TanStack Router / Query）。
Pro 真正有价值的是它的组件层 **ProComponents**，所以我们直接使用 antd 6 + ProComponents 3。
Pro 6 本身依赖的也是同一条 ProComponents 3.x 版本线。

> ProComponents 3.x 目前只发布了预发布版本（antd 6 适配线）。`package.json` 中**精确锁定**版本号（无 `^`），
> 升级时需人工验证，规则见 [docs/conventions.md](docs/conventions.md#依赖与版本)。

## 文档

- [docs/architecture.md](docs/architecture.md)：分层、数据流、认证与错误处理的设计
- [docs/conventions.md](docs/conventions.md)：编码规范与“新增一个功能模块”的步骤

参考实现：`src/features/model-configs`（CRUD + ModalForm）和 `src/features/reviews`（URL 同步的分页表格 + 轮询）。
