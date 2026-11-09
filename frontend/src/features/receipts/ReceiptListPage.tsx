import { useState } from 'react';
import { Button, Card, Input, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import { useDebouncedValue } from '../../shared/useDebouncedValue';
import WarehouseSelect from '../../shared/WarehouseSelect';
import { useWarehouses } from '../reference/hooks';
import { listReceipts } from './api';
import { RECEIPT_STATUS, RECEIPT_STATUS_OPTIONS } from './statuses';
import type { ReceiptStatus, ReceiptSummary } from './types';

/** Поступления (приёмка товара кладовщиком). */
export default function ReceiptListPage() {
  const navigate = useNavigate();
  const warehouses = useWarehouses();
  const [status, setStatus] = useState<ReceiptStatus>();
  const [warehouseId, setWarehouseId] = useState<string>();
  const [text, setText] = useState('');
  const q = useDebouncedValue(text.trim(), 400);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const list = useQuery({
    queryKey: ['receipts', 'list', { status, warehouseId, q, page, size }],
    queryFn: () => listReceipts({ status, warehouseId, q: q || undefined, page, size }),
    placeholderData: keepPreviousData,
  });
  const whName = (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? '—';

  const columns: TableColumnsType<ReceiptSummary> = [
    { title: 'Номер', dataIndex: 'number', render: (n: string, r) => <Link to={`/receipts/${r.id}`}>{n}</Link> },
    {
      title: 'Статус',
      dataIndex: 'status',
      render: (s: ReceiptStatus) => <Tag color={RECEIPT_STATUS[s]?.color}>{RECEIPT_STATUS[s]?.label ?? s}</Tag>,
    },
    { title: 'Склад', dataIndex: 'warehouseId', render: whName },
    { title: 'Поставщик', dataIndex: 'supplierName' },
    { title: 'Документ поставщика', dataIndex: 'externalDocumentNumber', render: (v: string | null) => v || '—' },
    { title: 'Получено', dataIndex: 'receivedAt', render: fmtDateTime },
    { title: 'Позиций', dataIndex: 'itemsCount', align: 'right' },
  ];

  return (
    <Card
      title="Приёмка"
      extra={
        <Button type="primary" onClick={() => navigate('/receipts/new')}>
          Новое поступление
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="Номер, поставщик, документ"
          style={{ width: 260 }}
          onChange={(e) => {
            setText(e.target.value);
            setPage(0);
          }}
        />
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 240 }}
          options={RECEIPT_STATUS_OPTIONS}
          onChange={(v?: ReceiptStatus) => {
            setStatus(v);
            setPage(0);
          }}
        />
        <WarehouseSelect
          allowClear
          style={{ width: 240 }}
          value={warehouseId}
          onChange={(v) => {
            setWarehouseId(v);
            setPage(0);
          }}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<ReceiptSummary>
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
