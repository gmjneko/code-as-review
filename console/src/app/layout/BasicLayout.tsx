import { LogoutOutlined, UserOutlined } from '@ant-design/icons'
import { ProLayout } from '@ant-design/pro-components'
import { Link, Outlet, useLocation } from '@tanstack/react-router'
import { Dropdown } from 'antd'

import { useCurrentUser, useLogout } from '@/features/auth'

import { appMenu, isMenuPath } from '../menu'

export function BasicLayout() {
  const pathname = useLocation({ select: (location) => location.pathname })
  const user = useCurrentUser()
  const logout = useLogout()

  return (
    <ProLayout
      title="Code as Review"
      logo="/favicon.svg"
      layout="mix"
      fixSiderbar
      route={{ path: '/', children: appMenu }}
      location={{ pathname }}
      menuItemRender={(item, dom) =>
        isMenuPath(item.path) ? <Link to={item.path}>{dom}</Link> : dom
      }
      avatarProps={{
        icon: <UserOutlined />,
        size: 'small',
        title: user?.username,
        render: (_, avatar) => (
          <Dropdown
            menu={{
              items: [
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出登录',
                  disabled: logout.isPending,
                  onClick: () => logout.mutate(),
                },
              ],
            }}
          >
            {avatar}
          </Dropdown>
        ),
      }}
    >
      <Outlet />
    </ProLayout>
  )
}
