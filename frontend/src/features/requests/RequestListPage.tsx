import { useMemo, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, type TableColumnsType } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import StatusTag from '../../shared/StatusTag';
import { REQUEST_STATUS_OPTIONS, type RequestStatus } from '../../shared/statuses';
import { useUserNames } from '../directory/hooks';
import { useWarehouses } from '../reference/hooks';
import { listRequests } from './api';
import type { RequestSummary } from './types';

/** Фиксированный размер страницы списка заявок. */
const PAGE_SIZE = 15;

export default function RequestListPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<RequestStatus>();
  const [warehouseId, setWarehouseId] = useState<string>();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);

  const filters = { status, warehouseId, q: q || undefined, page, size: PAGE_SIZE };
  const list = useQuery({
    queryKey: ['requests', 'list', filters],
    queryFn: () => listRequests(filters),
    placeholderData: keepPreviousData,
  });
  const isStaff = hasRole('STOREKEEPER') || hasRole('ADMIN');
  const names = useUserNames(
    (list.data?.content ?? []).map((r) => r.requesterId),
    isStaff,
  );
  const warehouses = useWarehouses();
  const warehouseName = useMemo(
    () => new Map((warehouses.data ?? []).map((w) => [w.id, w.name])),
    [warehouses.data],
  );

  const columns: TableColumnsType<RequestSummary> = [
    { title: 'Номер', dataIndex: 'number', render: (n: string, r) => <Link to={`/requests/${r.id}`}>{n}</Link> },
    { title: 'Статус', dataIndex: 'status', render: (s: RequestStatus) => <StatusTag status={s} /> },
    ...(isStaff
      ? ([
          { title: 'Заявитель', dataIndex: 'requesterId', render: (id: string) => names.get(id) ?? '—' },
        ] as TableColumnsType<RequestSummary>)
      : []),
    { title: 'Приоритет', dataIndex: 'priority', align: 'center' },
    ...(isStaff
      ? ([
          { title: 'Склад', dataIndex: 'warehouseId', render: (id: string) => warehouseName.get(id) ?? '—' },
        ] as TableColumnsType<RequestSummary>)
      : []),
    { title: 'Создана', dataIndex: 'createdAt', render: fmtDateTime },
    { title: 'Отправлена', dataIndex: 'submittedAt', render: fmtDateTime },
    { title: 'Позиций', dataIndex: 'itemsCount', align: 'right' },
  ];

  return (
    <Card
      title={hasRole('USER') ? 'Мои заявки' : 'Заявки'}
      extra={
        hasRole('USER') && (
          <Button type="primary" onClick={() => navigate('/requests/new')}>
            Новая заявка
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 220 }}
          options={REQUEST_STATUS_OPTIONS}
          onChange={(v?: RequestStatus) => {
            setStatus(v);
            setPage(0);
          }}
        />
        {isStaff && (
          <Select
            allowClear
            placeholder="Склад"
            style={{ width: 220 }}
            loading={warehouses.isLoading}
            options={(warehouses.data ?? []).map((w) => ({ value: w.id, label: w.name }))}
            onChange={(v?: string) => {
              setWarehouseId(v);
              setPage(0);
            }}
          />
        )}
        <Input.Search
          allowClear
          placeholder="Номер или комментарий"
          style={{ width: 260 }}
          onSearch={(v) => {
            setQ(v.trim());
            setPage(0);
          }}
        />
      </Space>

      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}

      <Table<RequestSummary>
        rowKey="id"
        columns={columns}
        dataSource={list.data?.content}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: (list.data?.page ?? page) + 1,
          pageSize: PAGE_SIZE,
          total: list.data?.totalElements ?? 0,
          showSizeChanger: false,
          showTotal: (total) => `Всего: ${total}`,
          onChange: (p) => setPage(p - 1),
        }}
      />
    </Card>
  );
}
