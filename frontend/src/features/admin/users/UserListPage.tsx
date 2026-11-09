import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  message,
  type TableColumnsType,
} from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Role, UserResponse } from '../../../api/types';
import { ROLE_LABELS } from '../../../auth/roles';
import ErrorAlert from '../../../shared/ErrorAlert';
import { fmtDateTime } from '../../../shared/format';
import { applyFieldErrors } from '../../../shared/formErrors';
import { createUser, listRoles, listUsers, resetUserPassword, updateUser } from './api';

interface FormValues {
  login: string;
  password: string;
  fullName: string;
  email?: string;
  phone?: string;
  roles: string[];
  active: boolean;
}

const roleLabel = (code: string) => ROLE_LABELS[code as Role] ?? code;

/** Совмещение ролей, которое позволяет одному человеку пройти весь процесс без второй пары глаз. */
function conflictOf(roles: string[]): string | null {
  const has = (r: string) => roles.includes(r);
  if (has('USER') && has('STOREKEEPER')) {
    return 'Роли «Пользователь» и «Кладовщик»: человек сам создаст, проверит, зарезервирует и выдаст свою заявку.';
  }
  if (has('STOREKEEPER') && has('ADMIN')) {
    return 'Роли «Кладовщик» и «Администратор»: человек сможет и оформить списание или корректировку, и одобрить её.';
  }
  return null;
}

/** Пользователи (только ADMIN): создание, правка, роли, блокировка, сброс пароля. */
export default function UserListPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [editing, setEditing] = useState<UserResponse | 'new' | null>(null);
  const [resetFor, setResetFor] = useState<UserResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [form] = Form.useForm<FormValues>();
  const [pwForm] = Form.useForm<{ newPassword: string }>();
  const selectedRoles: string[] = Form.useWatch('roles', form) ?? [];

  const list = useQuery({
    queryKey: ['admin', 'users', { page, size }],
    queryFn: () => listUsers(page, size),
    placeholderData: keepPreviousData,
  });
  const roles = useQuery({ queryKey: ['admin', 'roles'], queryFn: listRoles, staleTime: 10 * 60_000 });

  const roleOptions = (roles.data?.length ? roles.data : Object.entries(ROLE_LABELS).map(([code, name]) => ({ code, name }))).map(
    (r) => ({ value: r.code, label: roleLabel(r.code) }),
  );

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });

  const save = useMutation({
    mutationFn: async (v: FormValues) => {
      if (editing === 'new') {
        return createUser({
          login: v.login.trim(),
          password: v.password,
          fullName: v.fullName.trim(),
          email: v.email?.trim() || undefined,
          phone: v.phone?.trim() || undefined,
          roles: v.roles,
        });
      }
      return updateUser((editing as UserResponse).id, {
        fullName: v.fullName.trim(),
        email: v.email?.trim() || undefined,
        phone: v.phone?.trim() || undefined,
        active: v.active,
        roles: v.roles,
      });
    },
    onSuccess: () => {
      message.success(editing === 'new' ? 'Пользователь создан' : 'Изменения сохранены');
      setError(null);
      setEditing(null);
      return refresh();
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });

  const reset = useMutation({
    mutationFn: (v: { newPassword: string }) => resetUserPassword(resetFor!.id, v.newPassword),
    onSuccess: () => {
      message.success('Пароль сброшен. Передайте его пользователю безопасным способом');
      setError(null);
      setResetFor(null);
      pwForm.resetFields();
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(pwForm, e);
    },
  });

  const open = (u: UserResponse | 'new') => {
    setError(null);
    form.resetFields();
    if (u === 'new') form.setFieldsValue({ roles: ['USER'], active: true });
    else
      form.setFieldsValue({
        fullName: u.fullName,
        email: u.email ?? undefined,
        phone: u.phone ?? undefined,
        roles: u.roles,
        active: u.active,
      });
    setEditing(u);
  };

  const columns: TableColumnsType<UserResponse> = [
    { title: 'Логин', dataIndex: 'login' },
    { title: 'ФИО', dataIndex: 'fullName' },
    { title: 'Почта', dataIndex: 'email', render: (v: string | null) => v || '—' },
    { title: 'Телефон', dataIndex: 'phone', render: (v: string | null) => v || '—' },
    {
      title: 'Роли',
      dataIndex: 'roles',
      render: (rs: string[]) => (
        <Space size={4} wrap>
          {rs.map((r) => (
            <Tag key={r}>{roleLabel(r)}</Tag>
          ))}
        </Space>
      ),
    },
    { title: 'Статус', dataIndex: 'active', render: (a: boolean) => (a ? <Tag color="success">Активен</Tag> : <Tag>Отключён</Tag>) },
    { title: 'Создан', dataIndex: 'createdAt', render: fmtDateTime },
    {
      title: '',
      render: (_, u) => (
        <Space>
          <Button size="small" onClick={() => open(u)}>
            Изменить
          </Button>
          <Button
            size="small"
            onClick={() => {
              setError(null);
              pwForm.resetFields();
              setResetFor(u);
            }}
          >
            Сбросить пароль
          </Button>
        </Space>
      ),
    },
  ];

  const conflict = conflictOf(selectedRoles);

  return (
    <Card
      title="Пользователи"
      extra={
        <Button type="primary" onClick={() => open('new')}>
          Новый пользователь
        </Button>
      }
    >
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<UserResponse>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={list.data?.content}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: list.data?.totalElements ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `Всего: ${t}`,
          onChange: (p, s) => {
            setPage(p - 1);
            setSize(s);
          },
        }}
      />

      <Modal
        open={!!editing}
        title={editing === 'new' ? 'Новый пользователь' : `Пользователь ${(editing as UserResponse | null)?.login ?? ''}`}
        okText="Сохранить"
        cancelText="Отмена"
        confirmLoading={save.isPending}
        onCancel={() => setEditing(null)}
        onOk={() => form.validateFields().then((v) => save.mutate(v))}
        forceRender
        destroyOnClose={false}
      >
        <ErrorAlert error={editing ? error : null} style={{ marginBottom: 12 }} />
        <Form form={form} layout="vertical" requiredMark={false}>
          {editing === 'new' && (
            <>
              <Form.Item
                name="login"
                label="Логин"
                rules={[
                  { required: true, message: 'Укажите логин' },
                  { pattern: /^[A-Za-z0-9._@-]{3,100}$/, message: '3–100 символов: латиница, цифры, . _ @ -' },
                ]}
              >
                <Input autoComplete="off" />
              </Form.Item>
              <Form.Item
                name="password"
                label="Пароль"
                rules={[
                  { required: true, message: 'Укажите пароль' },
                  { min: 8, message: 'Не короче 8 символов' },
                  {
                    validator: (_, v?: string) =>
                      !v || new TextEncoder().encode(v).length <= 72 ? Promise.resolve() : Promise.reject(new Error('Не более 72 байт')),
                  },
                ]}
              >
                <Input.Password autoComplete="new-password" />
              </Form.Item>
            </>
          )}
          <Form.Item
            name="fullName"
            label="ФИО"
            rules={[{ required: true, whitespace: true, message: 'Укажите ФИО' }, { max: 255, message: 'Не длиннее 255 символов' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="email" label="Почта" rules={[{ type: 'email', message: 'Некорректный адрес' }, { max: 255 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Телефон" rules={[{ max: 50, message: 'Не длиннее 50 символов' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="roles" label="Роли" rules={[{ required: true, type: 'array', min: 1, message: 'Нужна хотя бы одна роль' }]}>
            <Checkbox.Group options={roleOptions} />
          </Form.Item>
          {conflict && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="Совмещение ролей нарушает разделение обязанностей"
              description={conflict}
            />
          )}
          {editing !== 'new' && (
            <Form.Item name="active" label="Учётная запись активна" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        open={!!resetFor}
        title={`Сброс пароля: ${resetFor?.login ?? ''}`}
        okText="Сбросить пароль"
        cancelText="Отмена"
        confirmLoading={reset.isPending}
        onCancel={() => setResetFor(null)}
        onOk={() => pwForm.validateFields().then((v) => reset.mutate(v))}
        destroyOnClose
      >
        <ErrorAlert error={resetFor ? error : null} style={{ marginBottom: 12 }} />
        <Form form={pwForm} layout="vertical" requiredMark={false}>
          <Form.Item name="newPassword" label="Новый пароль" rules={[{ required: true, message: 'Укажите пароль' }, { min: 8, message: 'Не короче 8 символов' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
