import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Form, Input, Space, Spin, Tag, message } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { changePassword, fetchMe } from '../api/auth';
import { ApiError, describeError } from '../api/errors';
import { ROLE_LABELS } from '../auth/roles';

interface PasswordForm {
  currentPassword: string;
  newPassword: string;
  repeat: string;
}

export default function ProfilePage() {
  const me = useQuery({ queryKey: ['me'], queryFn: fetchMe });
  const [form] = Form.useForm<PasswordForm>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onFinish = async (v: PasswordForm) => {
    setSaving(true);
    setError(null);
    try {
      await changePassword(v.currentPassword, v.newPassword);
      message.success('Пароль изменён');
      form.resetFields();
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) {
        form.setFields(Object.entries(e.fieldErrors).map(([name, errors]) => ({ name: name as keyof PasswordForm, errors })));
      } else {
        setError(describeError(e).message);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%', maxWidth: 560 }}>
      <Card title="Профиль">
        {me.isLoading && <Spin />}
        {me.isError && <Alert type="error" showIcon message={describeError(me.error).message} />}
        {me.data && (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="Логин">{me.data.login}</Descriptions.Item>
            <Descriptions.Item label="ФИО">{me.data.fullName}</Descriptions.Item>
            <Descriptions.Item label="Почта">{me.data.email ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Телефон">{me.data.phone ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Роли">
              {me.data.roles.map((r) => (
                <Tag key={r}>{ROLE_LABELS[r] ?? r}</Tag>
              ))}
            </Descriptions.Item>
            <Descriptions.Item label="Создан">{new Date(me.data.createdAt).toLocaleString('ru-RU')}</Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      <Card title="Смена пароля">
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form form={form} layout="vertical" requiredMark={false} onFinish={onFinish}>
          <Form.Item name="currentPassword" label="Текущий пароль" rules={[{ required: true, message: 'Введите текущий пароль' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="Новый пароль"
            rules={[
              { required: true, message: 'Введите новый пароль' },
              { min: 8, message: 'Не короче 8 символов' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="repeat"
            label="Повторите новый пароль"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Повторите пароль' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('newPassword') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Пароли не совпадают')),
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={saving}>
            Сменить пароль
          </Button>
        </Form>
      </Card>
    </Space>
  );
}
