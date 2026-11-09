import { useState } from 'react';
import { Button, Card, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import { useWarehouses } from '../reference/hooks';
import { listWriteOffs } from './api';
import { WRITE_OFF_REASON, WRITE_OFF_REASON_OPTIONS, WRITE_OFF_STATUS, WRITE_OFF_STATUS_OPTIONS } from './statuses';
import type { WriteOff, WriteOffReason, WriteOffStatus } from './types';

/** Списания: кладовщик оформляет и проводит, администратор одобряет. */
export default function WriteOffListPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const warehouses = useWarehouses();
  const [status, setStatus] = useState<WriteOffStatus>();
  const [reason, setReason] = useState<WriteOffReason>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const list = useQuery({
    queryKey: ['write-offs', 'list', { status, reason, page, size }],
    queryFn: () => listWriteOffs({ status, reason, page, size }),
    placeholderData: keepPreviousData,
  });

  const columns: TableColumnsType<WriteOff> = [
    { title: 'Номер', dataIndex: 'number', render: (n: string, r) => <Link to={`/write-offs/${r.id}`}>{n}</Link> },
    {
      title: 'Статус',
      dataIndex: 'status',
      render: (s: WriteOffStatus, r) => (
        <Space size={4} wrap>
          <Tag color={WRITE_OFF_STATUS[s]?.color}>{WRITE_OFF_STATUS[s]?.label ?? s}</Tag>
          {s === 'DRAFT' && r.approvalRequired && !r.approvedAt && <Tag color="orange">ждёт одобрения</Tag>}
        </Space>
      ),
    },
    { title: 'Причина', dataIndex: 'reasonCode', render: (c: WriteOffReason) => WRITE_OFF_REASON[c] ?? c },
    { title: 'Склад', dataIndex: 'warehouseId', render: (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? '—' },
    { title: 'Позиций', render: (_, r) => r.items.length, align: 'right' },
    { title: 'Создано', dataIndex: 'createdAt', render: fmtDateTime },
  ];

  return (
    <Card
      title="Списания"
      extra={
        hasRole('STOREKEEPER') && (
          <Button type="primary" onClick={() => navigate('/write-offs/new')}>
            Новое списание
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 200 }}
          options={WRITE_OFF_STATUS_OPTIONS}
          onChange={(v?: WriteOffStatus) => {
            setStatus(v);
            setPage(0);
          }}
        />
        <Select
          allowClear
          placeholder="Причина"
          style={{ width: 220 }}
          options={WRITE_OFF_REASON_OPTIONS}
          onChange={(v?: WriteOffReason) => {
            setReason(v);
            setPage(0);
          }}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<WriteOff>
        rowKey="id"
        columns={columns}
        dataSource={list.data?.content}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: list.data?.totalElements ?? 0,
          showSizeChanger: true,
          showTotal: (total) => `Всего: ${total}`,
          onChange: (p, s) => {
            setPage(p - 1);
            setSize(s);
          },
        }}
      />
    </Card>
  );
}
