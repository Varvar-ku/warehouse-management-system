import { useState } from 'react';
import { Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Tag, Typography } from 'antd';
import { MenuOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABELS } from '../auth/roles';
import { NAV } from './navigation';
import NotificationBell from '../features/notifications/NotificationBell';

const { Header, Sider, Content } = Layout;

const APP_TITLE = 'Управление складом';

/**
 * Каркас приложения. На широком экране слева боковое меню. На узком (меньше 992 px) меню прячется в выдвижную панель
 * по кнопке-«гамбургеру», а шапка укорачивается, чтобы название, колокольчик и имя пользователя не налезали друг на друга.
 */
export default function AppLayout() {
  const { user, roles, hasRole, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const mobile = Grid.useBreakpoint().lg === false;
  const [drawerOpen, setDrawerOpen] = useState(false);
  const items = NAV.filter((n) => hasRole(...n.roles));
  const selected = items.find((n) => pathname.startsWith(n.path))?.path;

  const menu = (
    <Menu
      mode="inline"
      selectedKeys={selected ? [selected] : []}
      items={items.map((n) => ({ key: n.path, label: n.label }))}
      style={{ borderInlineEnd: 0 }}
      onClick={({ key }) => {
        navigate(key);
        setDrawerOpen(false);
      }}
    />
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!mobile && (
        <Sider theme="light" width={220}>
          <div style={{ padding: 16, fontWeight: 600 }}>{APP_TITLE}</div>
          {menu}
        </Sider>
      )}
      {mobile && (
        <Drawer
          placement="left"
          width={260}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          title={APP_TITLE}
          styles={{ body: { padding: 0 } }}
        >
          {menu}
        </Drawer>
      )}
      <Layout style={{ minWidth: 0 }}>
        <Header
          style={{
            background: '#fff',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            paddingInline: mobile ? 12 : 24,
          }}
        >
          {mobile ? (
            <>
              <Button type="text" icon={<MenuOutlined />} aria-label="Открыть меню" onClick={() => setDrawerOpen(true)} />
              <Typography.Text strong ellipsis style={{ flex: 1, minWidth: 0 }}>
                {APP_TITLE}
              </Typography.Text>
            </>
          ) : (
            <div style={{ flex: 1 }} />
          )}
          <NotificationBell />
          <Dropdown
            menu={{
              items: [
                ...(mobile ? [{ key: 'roles', disabled: true, label: roles.map((r) => ROLE_LABELS[r]).join(', ') }] : []),
                { key: 'profile', label: 'Профиль', onClick: () => navigate('/profile') },
                { key: 'logout', label: 'Выйти', onClick: logout },
              ],
            }}
          >
            <Button type="text" style={{ maxWidth: mobile ? 130 : undefined, paddingInline: mobile ? 8 : undefined }}>
              <Space>
                <span
                  style={{
                    display: 'inline-block',
                    maxWidth: mobile ? 100 : 260,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    verticalAlign: 'bottom',
                  }}
                >
                  {user?.fullName}
                </span>
                {!mobile && roles.map((r) => <Tag key={r}>{ROLE_LABELS[r]}</Tag>)}
              </Space>
            </Button>
          </Dropdown>
        </Header>
        <Content style={{ margin: mobile ? 12 : 24, minWidth: 0 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
