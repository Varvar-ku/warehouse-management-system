import { useState } from 'react';
import { Card, DatePicker, Select, Space, Switch, Table, Tabs, Tag, Tooltip, Typography, type TableColumnsType } from 'antd';
import type { LabeledValue } from 'antd/es/select';
import type { Dayjs } from 'dayjs';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthContext';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty, PCS } from '../../shared/format';
import { rangeToQuery } from '../../shared/dateRange';
import ProductSelect from '../../shared/ProductSelect';
import WarehouseSelect from '../../shared/WarehouseSelect';
import { nameOf, useUserNames } from '../directory/hooks';
import { useProductNames } from '../reference/productNames';
import SectionSelect from '../tasks/components/SectionSelect';
import { listMovements, listStock, type Movement, type MovementType, type StockRow } from './api';
import { Link } from 'react-router-dom';
import { MOVEMENT_TYPE, MOVEMENT_TYPE_OPTIONS } from './labels';

function StockTab() {
  const [warehouseId, setWarehouseId] = useState<string>();
  const [sectionId, setSectionId] = useState<string>();
  const [product, setProduct] = useState<LabeledValue>();
  const [onlyAvailable, setOnlyAvailable] = useState(false);
  const [includeZero, setIncludeZero] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const productId = product ? String(product.value) : undefined;

  const list = useQuery({
    queryKey: ['stock', 'list', { warehouseId, sectionId, productId, onlyAvailable, includeZero, page, size }],
    queryFn: () => listStock({ warehouseId, sectionId, productId, onlyAvailable, includeZero, page, size }),
    placeholderData: keepPreviousData,
  });
  const reset = () => setPage(0);

  const columns: TableColumnsType<StockRow> = [
    { title: 'Товар', render: (_, r) => `${r.productSku} — ${r.productName}` },
    {
      title: 'Секция',
      dataIndex: 'sectionCode',
      render: (code: string, r) =>
        r.onHold ? (
          <Space size={4}>
            {code}
            <Tooltip title="Зафиксирована недостача при сборе. Остаток не резервируется, пока кладовщик не разберёт её.">
              <Link to="/shortages">
                <Tag color="orange" style={{ marginInlineEnd: 0, cursor: 'pointer' }}>
                  На проверке
                </Tag>
              </Link>
            </Tooltip>
          </Space>
        ) : (
          code
        ),
    },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Остаток, шт', dataIndex: 'quantity', align: 'right', render: fmtQty },
    { title: 'В резерве, шт', dataIndex: 'reservedQuantity', align: 'right', render: fmtQty },
    {
      title: 'Доступно, шт',
      dataIndex: 'availableQuantity',
      align: 'right',
      render: (v: number, r) =>
        v <= 0 || r.onHold ? <Typography.Text type="secondary">{fmtQty(v)}</Typography.Text> : <strong>{fmtQty(v)}</strong>,
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <WarehouseSelect
          allowClear
          style={{ width: 220 }}
          value={warehouseId}
          onChange={(v) => {
            setWarehouseId(v);
            setSectionId(undefined);
            reset();
          }}
        />
        <div style={{ width: 200 }}>
          <SectionSelect
            warehouseId={warehouseId ?? ''}
            disabled={!warehouseId}
            allowClear
            value={sectionId}
            onChange={(v) => {
              setSectionId(v);
              reset();
            }}
          />
        </div>
        <div style={{ width: 300 }}>
          <ProductSelect
            allowClear
            value={product}
            onChange={(v) => {
              setProduct(v);
              reset();
            }}
          />
        </div>
        <Space>
          <Switch
            checked={onlyAvailable}
            onChange={(v) => {
              setOnlyAvailable(v);
              reset();
            }}
          />
          только доступные
        </Space>
        <Space>
          <Switch
            checked={includeZero}
            onChange={(v) => {
              setIncludeZero(v);
              reset();
            }}
          />
          с нулевым остатком
        </Space>
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<StockRow>
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
    </>
  );
}

function MovementsTab({ showPeople }: { showPeople: boolean }) {
  const [type, setType] = useState<MovementType>();
  const [warehouseId, setWarehouseId] = useState<string>();
  const [product, setProduct] = useState<LabeledValue>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const productId = product ? String(product.value) : undefined;
  const { from, to } = rangeToQuery(range, true);

  const list = useQuery({
    queryKey: ['stock', 'movements', { type, warehouseId, productId, from, to, page, size }],
    queryFn: () => listMovements({ type, warehouseId, productId, from, to, page, size }),
    placeholderData: keepPreviousData,
  });
  const rows = list.data?.content ?? [];
  const productNames = useProductNames(rows.map((m) => m.productId));
  const people = useUserNames(rows.map((m) => m.performedBy), showPeople);
  const reset = () => setPage(0);

  const columns: TableColumnsType<Movement> = [
    { title: 'Дата', dataIndex: 'performedAt', render: fmtDateTime },
    {
      title: 'Тип',
      dataIndex: 'movementType',
      render: (t: MovementType) => <Tag color={MOVEMENT_TYPE[t]?.color}>{MOVEMENT_TYPE[t]?.label ?? t}</Tag>,
    },
    { title: 'Товар', dataIndex: 'productId', render: (id: string) => productNames.get(id) ?? '…' },
    {
      title: 'Откуда → куда',
      render: (_, m) => `${m.fromSectionCode ?? '—'} → ${m.toSectionCode ?? '—'}`,
    },
    { title: 'Количество', align: 'right', render: (_, m) => `${fmtQty(m.quantity)} ${PCS}` },
    { title: 'Документ', dataIndex: 'documentType', render: (d: string | null) => d || '—' },
    ...(showPeople
      ? ([{ title: 'Исполнитель', dataIndex: 'performedBy', render: (id: string | null) => nameOf(people, id) }] as TableColumnsType<Movement>)
      : []),
    { title: 'Комментарий', dataIndex: 'comment', render: (c: string | null) => c || '—' },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Тип движения"
          style={{ width: 200 }}
          options={MOVEMENT_TYPE_OPTIONS}
          onChange={(v?: MovementType) => {
            setType(v);
            reset();
          }}
        />
        <WarehouseSelect
          allowClear
          style={{ width: 220 }}
          value={warehouseId}
          onChange={(v) => {
            setWarehouseId(v);
            reset();
          }}
        />
        <div style={{ width: 300 }}>
          <ProductSelect
            allowClear
            value={product}
            onChange={(v) => {
              setProduct(v);
              reset();
            }}
          />
        </div>
        <DatePicker.RangePicker
          showTime
          format="DD.MM.YYYY HH:mm"
          onChange={(v) => {
            setRange(v as [Dayjs | null, Dayjs | null] | null);
            reset();
          }}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Movement>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={rows}
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
    </>
  );
}

/** Остатки по секциям и партиям и журнал движений (только чтение). */
export default function StockPage() {
  const { hasRole } = useAuth();
  const showPeople = hasRole('STOREKEEPER', 'ADMIN');
  return (
    <Card title="Остатки">
      <Tabs
        items={[
          { key: 'stock', label: 'Остатки', children: <StockTab /> },
          { key: 'movements', label: 'Журнал движений', children: <MovementsTab showPeople={showPeople} /> },
        ]}
      />
    </Card>
  );
}
