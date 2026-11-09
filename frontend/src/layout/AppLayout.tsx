import { Button, Dropdown, Layout, Menu, Space, Tag } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABELS } from '../auth/roles';
import { NAV } from './navigation';
import NotificationBell from '../features/notifications/NotificationBell';

const { Header, Sider, Content } = Layout;

export default function AppLayout() {
  const { user, roles, hasRole, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const items = NAV.filter((n) => hasRole(...n.roles));
  const selected = items.find((n) => pathname.startsWith(n.path))?.path;

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={0} theme="light">
        <div style={{ padding: 16, fontWeight: 600 }}>Управление складом</div>
        <Menu
          mode="inline"
          selectedKeys={selected ? [selected] : []}
          items={items.map((n) => ({ key: n.path, label: n.label }))}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
          <Space size="middle">
            <NotificationBell />
            <Dropdown
                menu={{
                  items: [
                    { key: 'profile', label: 'Профиль', onClick: () => navigate('/profile') },
                    { key: 'logout', label: 'Выйти', onClick: logout },
                  ],
                }}
            >
              <Button type="text">
                <Space>
                  {user?.fullName}
                  {roles.map((r) => (
                      <Tag key={r}>{ROLE_LABELS[r]}</Tag>
                  ))}
                </Space>
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
