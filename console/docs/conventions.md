# 前端开发规范

能由工具检查的规则都已写进 `tsconfig` / `.oxlintrc.json` / Prettier，`pnpm check` 通过是合并的前提。
本文档说明这些规则背后的约定，以及工具查不出来的部分。

## 目录

- [新增一个功能模块](#新增一个功能模块)
- [路由](#路由)
- [数据获取](#数据获取)
- [API 与类型](#api-与类型)
- [表单与交互](#表单与交互)
- [UI 与样式](#ui-与样式)
- [TypeScript](#typescript)
- [命名与文件](#命名与文件)
- [测试](#测试)
- [依赖与版本](#依赖与版本)
- [不要做的事](#不要做的事)

## 新增一个功能模块

以接入“仓库管理”（后端 `RepoController`，`/api/repositories`）为例：

1. **类型** `features/repositories/types.ts`：照着 `RepoDtos` 写接口，注释标明对应的 Java 类型；后端枚举写成字符串字面量联合类型。
2. **API** `api.ts`：导出 `repositoryApi` 对象，每个方法对应一个接口，只调用 `http`，查询类方法接收 `signal`。
3. **Queries** `queries.ts`：
   - key 工厂 `repositoryKeys`，以模块名为根：`['repositories']`；
   - `repositoryQueries.list()` / `.detail(id)` 返回 `queryOptions(...)`；
   - mutation hooks（`useCreateRepository` …），`onSuccess` 中 `return invalidateQueries(...)`，并设置 `meta.successMessage`。
4. **页面** `pages/RepositoryListPage.tsx`：以 `PageContainer` 为根，表格用受控 ProTable。
5. **公共 API** `index.ts`：只导出路由和其他模块需要的东西（页面、queries、search schema、类型）。
6. **路由** `routes/_authenticated/repositories/index.tsx`：`createFileRoute` + loader + `component`。开发服务器会自动重新生成 `routeTree.gen.ts`，也可以手动执行 `pnpm routes`。
7. **菜单** `app/menu.tsx`：添加 `{ path, name, icon }`，`path` 写错会编译失败。
8. **测试**：为有分支逻辑的部分补测试（见[测试](#测试)），然后跑 `pnpm check`。

`features/model-configs`（CRUD）和 `features/reviews`（分页表格）是可直接参照的完整示例。

## 路由

- 路由文件只负责：`beforeLoad`（守卫 / 重定向）、`validateSearch`、`loaderDeps` / `loader`、`component`。组件本身写在 feature 的 `pages/` 里。
- 需要登录的页面放在 `routes/_authenticated/` 下，由布局路由统一守卫，页面里不要再判断登录状态。
- 页面读取 params / search 用 `getRouteApi('<route id>')`，**不要**导入路由文件。`components/` 下的组件不感知路由，只通过 props 通信。
- **URL 是视图状态的权威来源**：分页、筛选、排序、当前 Tab 放进 search 参数，用 feature 的 `search.ts` 中的 zod schema 校验，每个字段都要 `.default()` 和 `.catch()`，保证非法 URL 能回退为默认值而不是报错；在路由上用 `stripSearchParams(defaults)` 让 URL 保持干净。
- 链接一律用 TanStack 的 `<Link to=...>` / `navigate({ to })`，路径与参数都有类型检查。不要拼字符串 URL。
- 文件名以 `-` 开头的文件不会被识别为路由，可用于与路由同目录的私有文件。

## 数据获取

- **所有服务端数据都经过 TanStack Query**，不在 `useEffect` 里请求数据，也不使用 ProTable 的 `request`。
- `queryKey` 只通过模块的 key 工厂生成，queryFn 必须把 `signal` 传给 api 层，以支持取消。
- loader 与页面使用**同一个** `queryOptions`，两种模式：

  | 场景                                | loader                                     | 页面                                                                                                                       |
  | ----------------------------------- | ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
  | 详情页、小列表（数据是页面的前提）  | `return queryClient.ensureQueryData(opts)` | `useSuspenseQuery(opts)`                                                                                                   |
  | 分页 / 筛选表格（翻页不应阻塞导航） | `void queryClient.prefetchQuery(opts)`     | `useQuery({ ...opts, throwOnError: (_, q) => q.state.data === undefined })`，opts 中设 `placeholderData: keepPreviousData` |

- **Mutation**：
  - `mutationFn` 写成箭头函数再调用 api（`(body: X) => xxxApi.create(body)`），**不要**直接传 `xxxApi.create`：TanStack Query 会把上下文对象作为第二个参数传给 `mutationFn`，直接传入会让它落进 api 方法的可选参数里。
  - `onSuccess` 中 `return queryClient.invalidateQueries({ queryKey: xxxKeys.all })`，让 mutation 保持 pending 直到列表刷新完成。会影响其他记录的操作（如“设为默认”）要失效整个模块。
  - 成功提示用 `meta: { successMessage }`；失败由全局处理器 toast，不要在每个调用点重复写 `message.error`。只有需要自定义错误展示时才设置 `meta: { silent: true }` 并自行处理。
- 需要轮询的数据用 `refetchInterval` 函数，在没有进行中的任务时返回 `false`（见 `reviewQueries.list`）。
- 全局默认：`staleTime` 30 秒；4xx 不重试，其他错误最多重试 2 次。没有充分理由不要在单个查询上覆盖。

## API 与类型

- 只通过 `@/shared/api` 的 `http` 调用后端，它返回解包后的 `data`，失败时抛出 `ApiError`。不要直接使用 `fetch`。
- 路径相对于 `/api`：写 `/repositories`，而不是 `/api/repositories`。
- DTO 类型与后端字段**完全一致**（名称、可空性）。后端可能返回 `null` 的字段标注为 `T | null`，不要用 `?` 糊弄过去。
- 后端枚举写成联合类型，展示文案放在 `constants.ts`，并用 `Record<Union, ...>` 声明，后端新增枚举值时编译器会提醒补全。
- 时间字段类型用 `InstantString`（UTC，带 `Z`），展示时由 dayjs 转为浏览器本地时区，表格中用 `valueType: 'dateTime'`，其他地方用 dayjs 格式化。
- 判断错误类型用 `ApiError` 的 `code` / `status` / `isClientError` / `isSessionExpired`，不要匹配 message 文本。

## 表单与交互

- 表单使用 ProForm 系列（`ModalForm`、`DrawerForm`、`ProFormText` …）。校验规则与后端 Bean Validation 约束保持一致（长度、正则、必填），并注释说明来源 DTO。
- `onFinish`、Popconfirm 的 `onConfirm` 等接收 Promise 的回调，统一写成 `runAction(() => mutation.mutateAsync(...))`：成功返回 `true`（弹窗关闭），失败返回 `false`（弹窗保持打开，错误已由全局处理器提示）。
- 弹窗表单设置 `modalProps={{ destroyOnHidden: true }}`，避免残留上一次的输入。
- 危险操作（删除、取消任务）必须二次确认（`Popconfirm` 或 `modal.confirm`），按钮设置 `danger`。
- 表格中的操作使用 `<Button type="link" size="small">`，不要使用没有 `href` 的 `<a>`（无法用键盘访问，lint 会报错）。
- 组件内的 `message` / `modal` 用 `App.useApp()` 获取；不要使用 antd 的静态方法（`message.success(...)`），它们拿不到主题和语言上下文。

## UI 与样式

- 当前使用 antd 默认主题。主题定制**只能**在 `app/AppProviders.tsx` 的 `ConfigProvider` 中进行（`theme.token` / `components`）。
- 优先使用 antd 布局组件（`Flex`、`Space`、`Row/Col`）和 token（`theme.useToken()`），不要硬编码颜色、间距和字号。
- 页面以 `PageContainer` 为根，标题和面包屑由菜单自动生成；不要在页面里再写一个标题。
- 必须写样式时，优先用组件的 `style` / `styles` 属性配合 token；超出这个范围再引入 CSS Modules（`*.module.css`），不要写全局 CSS。
- 文案目前统一为简体中文。是否引入 i18n 尚未决定；在此之前，新增文案集中写在组件或 `constants.ts` 中，不要散落在工具函数里。

## TypeScript

- `strict`、`noUncheckedIndexedAccess`、`erasableSyntaxOnly` 均已开启：不使用 `enum`、`namespace`、构造函数参数属性。
- 禁止 `any`。不可避免的类型断言（JSON 反序列化等信任边界）必须附带
  `// oxlint-disable-next-line typescript/no-unsafe-type-assertion -- <原因>`。
- 非空断言 `!` 只允许在确定成立且无法用类型表达的地方使用（目前只有 `main.tsx` 的 `#root`）。
- 类型导入使用 `import type` / `import { type X }`（lint 会自动修复）。
- 用联合类型配合 `Record` 获得穷尽检查，不要用 `string` 表示有限取值。
- 模块级代码不得有副作用（见 [architecture.md §7](architecture.md#7-构建与代码分割)）。

## 命名与文件

| 对象                          | 约定                                               | 例子                                |
| ----------------------------- | -------------------------------------------------- | ----------------------------------- |
| React 组件文件                | PascalCase，一个文件一个导出组件                   | `ModelConfigFormModal.tsx`          |
| 页面组件                      | `<Entity><List                                     | Detail>Page`                        | `ReviewListPage` |
| 其他模块文件                  | kebab-case                                         | `token-refresh.ts`、`run-action.ts` |
| feature 目录                  | 复数 kebab-case，与后端资源路径一致                | `model-configs`、`reviews`          |
| Hook                          | `use` 前缀；mutation hook 用动词                   | `useCreateModelConfig`              |
| api 对象 / key 工厂 / queries | `<entity>Api` / `<entity>Keys` / `<entity>Queries` | `reviewApi`、`reviewKeys`           |
| 常量                          | UPPER_SNAKE_CASE                                   | `REVIEW_STATUS_ENUM`                |
| 测试文件                      | 与被测文件同目录，`*.test.ts(x)`                   | `http.test.ts`                      |

- 只使用具名导出，不使用 `export default`（lint 强制；`*.config.ts` 等工具配置文件除外）。
- 导入顺序：第三方库 → `@/` 别名 → 相对路径，组间空一行。
- 代码注释用英文，解释**为什么**，不复述代码在做什么。

## 测试

- 必须测试：有分支或协议细节的纯逻辑（http 解包与重试、token 刷新、redirect 校验、全局错误上报）、有业务规则的组件行为（如“编辑时 API Key 留空则不提交”）。
- 不必测试：纯展示的列定义、对第三方组件的简单透传。
- 组件测试用 `@/test/render` 的 `renderWithProviders`，它会挂载真实的 Providers；用 `vi.mock('../api')` 替换 api 层，不要 mock `fetch` 以外的 HTTP 细节。
- 按用户可感知的方式查询元素（`getByRole`、`getByLabelText`），避免依赖 class 名。
- 运行组件测试时，jsdom 会在 stderr 打印 “Could not parse CSS stylesheet”。这来自 antd 的 CSS-in-JS，无害。

## 依赖与版本

- 包管理器固定为 pnpm（`packageManager` 字段）。pnpm 11 的发布时间策略（`minimumReleaseAge`）会拦截刚发布的版本，**不要**通过添加 `minimumReleaseAgeExclude` 绕过它，应选择已过冷却期的版本。
- `@ant-design/pro-components` 使用精确版本（预发布线）。升级流程：查看 [变更记录](https://github.com/ant-design/pro-components/releases)，升级，然后执行 `pnpm check` 并手动检查布局、表格、表单页面。
- 新增依赖前先确认现有依赖能否满足。代码中直接导入的包必须出现在 `package.json` 中，不能依赖间接安装的包（例如 ProComponents 带进来的 `es-toolkit`），需要时显式添加。

## 不要做的事

- 在 `useEffect` 中请求数据，或把查询结果复制进 `useState` / Zustand。
- 使用 ProTable 的 `request` / `actionRef.reload()` 管理数据。
- 从其他 feature 的内部文件导入，或在任何地方导入 `routes/`。
- 在页面中自行判断登录状态或手动跳转登录页（交给 `_authenticated` 路由守卫和 `setup-auth`）。
- 用 `localStorage` 保存 Query 能管理的数据。
- 使用 antd 静态方法（`message.xxx`、`Modal.confirm`）或从 `antd/es/*`、`antd/lib/*` 导入。
- 手改 `routeTree.gen.ts`。
