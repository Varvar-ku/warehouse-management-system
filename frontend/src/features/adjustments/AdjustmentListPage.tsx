import { useState } from 'react';
import { Card, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import { useWarehouses } from '../reference/hooks';
import { listAdjustments } from './api';
import { ADJUSTMENT_STATUS, ADJUSTMENT_STATUS_OPTIONS } from './statuses';
import type { Adjustment, AdjustmentStatus } from './types';

/** Корректировки остатка по недостачам: оформляет кладовщик, одобряет администратор. Создаются из раздела «Недостачи». */
export default function AdjustmentListPage() {
  const warehouses = useWarehouses();
  const [status, setStatus] = useState<AdjustmentStatus>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const list = useQuery({
    queryKey: ['adjustments', 'list', { status, page, size }],
    queryFn: () => listAdjustments({ status, page, size }),
    placeholderData: keepPreviousData,
  });

  const columns: TableColumnsType<Adjustment> = [
    { title: 'Номер', dataIndex: 'number', render: (n: string, r) => <Link to={`/adjustments/${r.id}`}>{n}</Link> },
    {
      title: 'Статус',
      dataIndex: 'status',
      render: (s: AdjustmentStatus, r) => (
        <Space size={4} wrap>
          <Tag color={ADJUSTMENT_STATUS[s]?.color}>{ADJUSTMENT_STATUS[s]?.label ?? s}</Tag>
          {s === 'DRAFT' && !r.approvedAt && <Tag color="orange">ждёт одобрения</Tag>}
          {s === 'DRAFT' && r.approvedAt && <Tag color="blue">одобрено</Tag>}
        </Space>
      ),
    },
    { title: 'Склад', dataIndex: 'warehouseId', render: (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? '—' },
    { title: 'Позиций', align: 'right', render: (_, r) => r.items.length },
    { title: 'Создано', dataIndex: 'createdAt', render: fmtDateTime },
  ];

  return (
    <Card title="Корректировки">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 180 }}
          options={ADJUSTMENT_STATUS_OPTIONS}
          onChange={(v?: AdjustmentStatus) => {
            setStatus(v);
            setPage(0);
          }}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Adjustment>
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
