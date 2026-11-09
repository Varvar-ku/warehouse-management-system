import { useState } from 'react';
import { Button, Card, Checkbox, Form, Input, Modal, Select, Space, Switch, Table, Tabs, Tag, message, type TableColumnsType } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { applyFieldErrors } from '../../../shared/formErrors';
import { useDebouncedValue } from '../../../shared/useDebouncedValue';
import WarehouseSelect from '../../../shared/WarehouseSelect';
import { useWarehouses } from '../../reference/hooks';
import type { Warehouse } from '../../reference/types';
import { createWarehouse, searchAdminSections, updateWarehouse } from './api';
import SectionModal from './SectionModal';
import type { Section, SectionStatus } from './types';

const SECTION_STATUS: Record<SectionStatus, { label: string; color: string }> = {
  ACTIVE: { label: 'Активна', color: 'success' },
  BLOCKED: { label: 'Заблокирована', color: 'orange' },
  ARCHIVED: { label: 'В архиве', color: 'default' },
};

function WarehousesTab() {
  const queryClient = useQueryClient();
  const q = useWarehouses();
  const [target, setTarget] = useState<Warehouse | 'new' | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [form] = Form.useForm<{ code: string; name: string; address?: string; active: boolean }>();
  const editing = target !== 'new' ? target : null;

  const open = (t: Warehouse | 'new') => {
    setError(null);
    form.resetFields();
    form.setFieldsValue(t === 'new' ? { active: true } : { code: t.code, name: t.name, address: t.address ?? undefined, active: t.active });
    setTarget(t);
  };

  const save = useMutation({
    mutationFn: (v: { code: string; name: string; address?: string; active: boolean }) =>
      editing
        ? updateWarehouse(editing.id, { name: v.name.trim(), address: v.address?.trim() ?? '', active: v.active })
        : createWarehouse({ code: v.code.trim(), name: v.name.trim(), address: v.address?.trim() || undefined }),
    onSuccess: () => {
      message.success('Склад сохранён');
      setTarget(null);
      return queryClient.invalidateQueries({ queryKey: ['warehouses'] });
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });

  const columns: TableColumnsType<Warehouse> = [
    { title: 'Код', dataIndex: 'code' },
    { title: 'Название', dataIndex: 'name' },
    { title: 'Адрес', dataIndex: 'address', render: (a: string | null) => a || '—' },
    { title: 'Состояние', dataIndex: 'active', render: (a: boolean) => (a ? <Tag color="success">Активен</Tag> : <Tag>Отключён</Tag>) },
    { title: '', render: (_, w) => <Button size="small" onClick={() => open(w)}>Изменить</Button> },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => open('new')}>
          Новый склад
        </Button>
      </Space>
      {q.isError && <ErrorAlert error={q.error} onRetry={() => q.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Warehouse> rowKey="id" size="middle" columns={columns} dataSource={q.data} loading={q.isFetching} pagination={false} />
      <Modal
        open={!!target}
        title={editing ? `Склад ${editing.code}` : 'Новый склад'}
        okText="Сохранить"
        cancelText="Отмена"
        confirmLoading={save.isPending}
        onCancel={() => setTarget(null)}
        onOk={() => form.validateFields().then((v) => save.mutate(v))}
        forceRender
      >
        <ErrorAlert error={error} style={{ marginBottom: 12 }} />
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="code" label="Код" extra={editing ? 'Код не меняется' : undefined} rules={[{ required: true, whitespace: true, message: 'Укажите код' }, { max: 50 }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 255 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="address" label="Адрес">
            <Input />
          </Form.Item>
          {editing && (
            <Form.Item
              name="active"
              label="Активен"
              valuePropName="checked"
              extra="Отключённый склад исчезает из выбора и в нём нельзя создавать секции. Открытые документы сначала завершите."
            >
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}

function SectionsTab() {
  const [warehouseId, setWarehouseId] = useState<string>();
  const [status, setStatus] = useState<SectionStatus>();
  const [q, setQ] = useState('');
  const [zone, setZone] = useState('');
  const [includeArchived, setIncludeArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [target, setTarget] = useState<Section | 'new' | null>(null);
  const warehouses = useWarehouses();
  const dq = useDebouncedValue(q, 300);
  const dz = useDebouncedValue(zone, 300);

  const list = useQuery({
    queryKey: ['admin', 'sections', { warehouseId, status, q: dq, zone: dz, includeArchived, page, size }],
    queryFn: () =>
      searchAdminSections({ warehouseId, status, q: dq || undefined, zone: dz || undefined, includeArchived, page, size }),
    placeholderData: keepPreviousData,
  });
  const reset = () => setPage(0);

  const columns: TableColumnsType<Section> = [
    {
      title: 'Код',
      dataIndex: 'code',
      render: (c: string, s) => (
        <Space size={4}>
          {c}
          {s.unassignedZone && <Tag color="purple">Нераспределено</Tag>}
        </Space>
      ),
    },
    { title: 'Название', dataIndex: 'name' },
    { title: 'Склад', dataIndex: 'warehouseId', render: (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? '—' },
    { title: 'Зона', dataIndex: 'zone', render: (z: string | null) => z || '—' },
    { title: 'Стеллаж', dataIndex: 'rack', render: (r: string | null) => r || '—' },
    { title: 'Приоритет', dataIndex: 'priority', align: 'right' },
    { title: 'Статус', dataIndex: 'status', render: (s: SectionStatus) => <Tag color={SECTION_STATUS[s]?.color}>{SECTION_STATUS[s]?.label ?? s}</Tag> },
    { title: '', render: (_, s) => <Button size="small" onClick={() => setTarget(s)}>Изменить</Button> },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <WarehouseSelect allowClear style={{ width: 220 }} value={warehouseId} onChange={(v) => { setWarehouseId(v); reset(); }} />
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 170 }}
          options={(Object.keys(SECTION_STATUS) as SectionStatus[]).map((s) => ({ value: s, label: SECTION_STATUS[s].label }))}
          onChange={(v?: SectionStatus) => { setStatus(v); reset(); }}
        />
        <Input.Search allowClear placeholder="Код или название" style={{ width: 220 }} value={q} onChange={(e) => { setQ(e.target.value); reset(); }} />
        <Input allowClear placeholder="Зона" style={{ width: 140 }} value={zone} onChange={(e) => { setZone(e.target.value); reset(); }} />
        <Checkbox checked={includeArchived} onChange={(e) => { setIncludeArchived(e.target.checked); reset(); }}>
          с архивными
        </Checkbox>
        <Button type="primary" onClick={() => setTarget('new')}>
          Новая секция
        </Button>
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Section>
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
      <SectionModal target={target} defaultWarehouseId={warehouseId} onClose={() => setTarget(null)} />
    </>
  );
}

/** Склады и секции (ADMIN). */
export default function WarehousesPage() {
  return (
    <Card title="Склады и секции">
      <Tabs
        items={[
          { key: 'sections', label: 'Секции', children: <SectionsTab /> },
          { key: 'warehouses', label: 'Склады', children: <WarehousesTab /> },
        ]}
      />
    </Card>
  );
}
