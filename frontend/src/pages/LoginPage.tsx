import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { Navigate, } from 'react-router-dom';
import { describeError } from '../api/errors';
import { useAuth } from '../auth/AuthContext';
import { homePath } from '../auth/roles';


export default function LoginPage() {
  const { user, roles, expired, login } = useAuth();
  const [error, setError] = useState<{ message: string; traceId: string | null } | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homePath(roles)} replace />;

  const onFinish = async (v: { login: string; password: string }) => {
    setBusy(true);
    setError(null);
    try {
      await login(v.login.trim(), v.password);
    } catch (e) {
      setError(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16 }}>
      <Card style={{ width: 360, maxWidth: '100%' }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>
          Управление складом
        </Typography.Title>
        {expired && <Alert type="info" showIcon message="Сессия истекла, войдите снова" style={{ marginBottom: 16 }} />}
        {error && (
          <Alert
            type="error"
            showIcon
            message={error.message}
            description={error.traceId ? `trace_id: ${error.traceId}` : undefined}
            style={{ marginBottom: 16 }}
          />
        )}
        <Form layout="vertical" requiredMark={false} onFinish={onFinish}>
          <Form.Item name="login" label="Логин" rules={[{ required: true, message: 'Введите логин' }]}>
            <Input autoFocus autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="Пароль" rules={[{ required: true, message: 'Введите пароль' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={busy}>
            Войти
          </Button>
        </Form>
      </Card>
    </div>
  );
}
