import { useState } from 'react';
import { Alert, Button, Card, Modal, Form, Input, Select, Space, Table, Tag, message, type TableColumnsType } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty, PCS } from '../../shared/format';
import ReasonModal from '../../shared/ReasonModal';
import { createAdjustment } from '../adjustments/api';
import { useWarehouses } from '../reference/hooks';
import { listShortages, markShortageFound } from './api';
import { SHORTAGE_RESOLUTION, SHORTAGE_SOURCE, SHORTAGE_STATUS, SHORTAGE_STATUS_OPTIONS } from './statuses';
import type { Shortage, ShortageStatus } from './types';

/**
 * Недостачи. Если при сборе или выдаче товара не оказалось на месте, строка остатка блокируется («на проверке»),
 * а здесь появляется недостача. Кладовщик решает: «найдено» (остаток возвращается в оборот)
 * либо оформляет корректировку, которую одобряет администратор.
 */
export default function ShortageListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const isStorekeeper = hasRole('STOREKEEPER');
  const warehouses = useWarehouses();
  const [status, setStatus] = useState<ShortageStatus | undefined>('OPEN');
  const [warehouseId, setWarehouseId] = useState<string>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [selected, setSelected] = useState<Shortage[]>([]);
  const [foundFor, setFoundFor] = useState<Shortage | null>(null);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [form] = Form.useForm<{ comment: string }>();

  const list = useQuery({
    queryKey: ['shortages', 'list', { status, warehouseId, page, size }],
    queryFn: () => listShortages({ status, warehouseId, page, size }),
    placeholderData: keepPreviousData,
  });

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['shortages'] }),
      queryClient.invalidateQueries({ queryKey: ['stock'] }),
    ]);

  const found = useMutation({
    mutationFn: ({ id, comment }: { id: string; comment: string }) => markShortageFound(id, comment),
    onSuccess: () => {
      setError(null);
      setFoundFor(null);
      message.success('Недостача закрыта: остаток возвращён в оборот');
      return refresh();
    },
    onError: (e) => {
      setFoundFor(null);
      setError(e);
    },
  });

  const adjust = useMutation({
    mutationFn: (comment: string) =>
      createAdjustment({
        warehouseId: selected[0].warehouseId,
        comment,
        items: selected.map((s) => ({ shortageId: s.id })),
      }),
    onSuccess: (a) => {
      setError(null);
      setAdjustOpen(false);
      setSelected([]);
      form.resetFields();
      message.success(`Корректировка ${a.number} создана: ждёт одобрения администратора`);
      void refresh();
      navigate(`/adjustments/${a.id}`);
    },
    onError: (e) => {
      setAdjustOpen(false);
      setError(e);
    },
  });

  const isFree = (s: Shortage) => s.status === 'OPEN' && !s.adjustmentId;
  const mixedWarehouses = new Set(selected.map((s) => s.warehouseId)).size > 1;

  const columns: TableColumnsType<Shortage> = [
    { title: 'Номер', dataIndex: 'number' },
    { title: 'Товар', render: (_, s) => `${s.productSku} — ${s.productName}` },
    { title: 'Секция', dataIndex: 'sectionCode' },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Количество', align: 'right', render: (_, s) => `${fmtQty(s.quantity)} ${PCS}` },
    { title: 'Склад', dataIndex: 'warehouseId', render: (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? '—' },
    {
      title: 'Источник',
      render: (_, s) => (
        <Space size={4} wrap>
          {SHORTAGE_SOURCE[s.source] ?? s.source}
          {s.requestId && <Link to={`/requests/${s.requestId}`}>заявка</Link>}
        </Space>
      ),
    },
    {
      title: 'Статус',
      render: (_, s) => (
        <Space size={4} wrap>
          <Tag color={SHORTAGE_STATUS[s.status]?.color}>{SHORTAGE_STATUS[s.status]?.label ?? s.status}</Tag>
          {s.resolution && <span>{SHORTAGE_RESOLUTION[s.resolution] ?? s.resolution}</span>}
          {s.status === 'OPEN' && s.adjustmentId && <Link to={`/adjustments/${s.adjustmentId}`}>в корректировке</Link>}
        </Space>
      ),
    },
    { title: 'Создана', dataIndex: 'createdAt', render: fmtDateTime },
    ...(isStorekeeper
      ? ([
          {
            title: '',
            render: (_: unknown, s: Shortage) =>
              isFree(s) ? (
                <Button size="small" onClick={() => setFoundFor(s)}>
                  Найдено
                </Button>
              ) : null,
          },
        ] as TableColumnsType<Shortage>)
      : []),
  ];

  return (
    <Card
      title="Недостачи"
      extra={
        isStorekeeper && (
          <Button type="primary" disabled={selected.length === 0 || mixedWarehouses} onClick={() => setAdjustOpen(true)}>
            Оформить корректировку{selected.length > 0 ? ` (${selected.length})` : ''}
          </Button>
        )
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Остаток с открытой недостачей помечен «на проверке»: он не резервируется и не списывается, пока вы не примете решение."
      />
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 160 }}
          value={status}
          options={SHORTAGE_STATUS_OPTIONS}
          onChange={(v?: ShortageStatus) => {
            setStatus(v);
            setPage(0);
          }}
        />
        <Select
          allowClear
          placeholder="Склад"
          style={{ width: 220 }}
          value={warehouseId}
          options={warehouses.data?.map((w) => ({ value: w.id, label: w.name }))}
          onChange={(v?: string) => {
            setWarehouseId(v);
            setPage(0);
          }}
        />
      </Space>
      {mixedWarehouses && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Корректировка оформляется по одному складу: оставьте недостачи только одного склада" />
      )}
      <ErrorAlert error={error} style={{ marginBottom: 16 }} />
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Shortage>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={list.data?.content}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        rowSelection={
          isStorekeeper
            ? {
                selectedRowKeys: selected.map((s) => s.id),
                getCheckboxProps: (s) => ({ disabled: !isFree(s) }),
                onChange: (_, rows) => setSelected(rows),
              }
            : undefined
        }
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

      <ReasonModal
        open={!!foundFor}
        title={`Недостача ${foundFor?.number ?? ''}: товар найден`}
        label="Комментарий (где найден, как разобрались)"
        okText="Вернуть остаток в оборот"
        loading={found.isPending}
        onSubmit={(comment) => {
          setError(null);
          if (foundFor) found.mutate({ id: foundFor.id, comment });
        }}
        onCancel={() => setFoundFor(null)}
      />
      <Modal
        open={adjustOpen}
        title="Оформить корректировку"
        okText="Создать корректировку"
        cancelText="Отмена"
        confirmLoading={adjust.isPending}
        onCancel={() => setAdjustOpen(false)}
        onOk={() => form.validateFields().then((v) => adjust.mutate(v.comment.trim()))}
        destroyOnClose
      >
        <p>
          Остаток будет уменьшен на количество недостачи по {selected.length} поз. После создания документ должен одобрить
          администратор (не вы), затем вы его проведёте.
        </p>
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            name="comment"
            label="Комментарий"
            rules={[
              { required: true, whitespace: true, message: 'Укажите комментарий' },
              { max: 1000, message: 'Не длиннее 1000 символов' },
            ]}
          >
            <Input.TextArea rows={3} maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
