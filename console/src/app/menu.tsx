import { AuditOutlined, BranchesOutlined, KeyOutlined, RobotOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'

import type { FileRoutesByTo } from '@/routeTree.gen'

/** Every navigable path; a typo or a removed route is a compile error. */
export type AppPath = keyof FileRoutesByTo

export interface AppMenuItem {
  path: AppPath
  name: string
  icon?: ReactNode
  children?: AppMenuItem[]
}

/** Side menu of the authenticated layout. Page titles and breadcrumbs are derived from it. */
export const appMenu: AppMenuItem[] = [
  { path: '/reviews', name: '评审任务', icon: <AuditOutlined /> },
  { path: '/repositories', name: '代码仓库', icon: <BranchesOutlined /> },
  { path: '/credentials', name: '凭据管理', icon: <KeyOutlined /> },
  { path: '/model-configs', name: '模型配置', icon: <RobotOutlined /> },
]

const menuPaths = new Set<string>(
  (function* collect(items: AppMenuItem[]): Generator<AppPath> {
    for (const item of items) {
      yield item.path
      if (item.children) yield* collect(item.children)
    }
  })(appMenu),
)

/** Narrows the untyped `path` that ProLayout hands back to a menu item's typed path. */
export function isMenuPath(path: string | undefined): path is AppPath {
  return path !== undefined && menuPaths.has(path)
}
