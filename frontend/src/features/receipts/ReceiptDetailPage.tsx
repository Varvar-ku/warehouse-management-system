import { useState } from 'react';
import { Button, Card, Descriptions, Form, Modal, Select, Space, Spin, Table, Tag, Typography, message, type TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty, PCS } from '../../shared/format';
import ReasonModal from '../../shared/ReasonModal';
import { nameOf, useUserNames, useWorkers } from '../directory/hooks';
import { useWarehouses } from '../reference/hooks';
import { cancelReceipt, confirmReceipt, createPlacementTask, getReceipt, recommendPlacement } from './api';
import PlacementTasksCard from './PlacementTasksCard';
import RecommendationPanel from './RecommendationPanel';
import { ITEM_PLACEMENT_STATUS, RECEIPT_STATUS, TARGET_DECISION } from './statuses';
import type { ItemDecision, ItemRecommendation, ReceiptItem } from './types';

const fmtDate = (iso?: string | null) => (iso ? new Date(iso).toLocaleDateString('ru-RU') : '—');

export default function ReceiptDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const warehouses = useWarehouses();
  const workers = useWorkers();
  const [error, setError] = useState<unknown>(null);
  const [recs, setRecs] = useState<ItemRecommendation[] | null>(null);
  const [dialog, setDialog] = useState<'cancel' | 'task' | null>(null);
  const [assignee, setAssignee] = useState<string>();

  const q = useQuery({ queryKey: ['receipts', 'detail', id], queryFn: () => getReceipt(id) });
  const r = q.data;
  const names = useUserNames([r?.createdBy], !!r);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['receipts'] }),
      queryClient.invalidateQueries({ queryKey: ['placement'] }),
      queryClient.invalidateQueries({ queryKey: ['stock'] }),
    ]);

  const recommend = useMutation({
    mutationFn: () => recommendPlacement(id),
    onSuccess: (data) => {
      setError(null);
      setRecs(data);
      return queryClient.invalidateQueries({ queryKey: ['receipts', 'detail', id] });
    },
    onError: setError,
  });

  const confirm = useMutation({
    mutationFn: (decisions: ItemDecision[]) => confirmReceipt(id, decisions),
    onSuccess: () => {
      setError(null);
      setRecs(null);
      message.success('Приёмка подтверждена');
      return refresh();
    },
    onError: setError,
  });

  const createTask = useMutation({
    mutationFn: () => createPlacementTask(id, assignee),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      message.success('Задание на размещение создано');
      return refresh();
    },
    onError: (e) => {
      setDialog(null);
      setError(e);
    },
  });

  const cancel = useMutation({
    mutationFn: (reason: string) => cancelReceipt(id, reason),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      message.success('Поступление отменено');
      return refresh();
    },
    onError: (e) => {
      setDialog(null);
      setError(e);
    },
  });

  if (q.isLoading) return <Spin />;
  if (q.isError) return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  if (!r) return null;

  const warehouse = warehouses.data?.find((w) => w.id === r.warehouseId);
  const st = RECEIPT_STATUS[r.status];

  const columns: TableColumnsType<ReceiptItem> = [
    { title: '№', dataIndex: 'lineNo', width: 50 },
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Количество', align: 'right', render: (_, i) => `${fmtQty(i.quantity)} ${PCS}` },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Срок годности', dataIndex: 'expiryAt', render: fmtDate },
    {
      title: 'Секция',
      render: (_, i) =>
        i.targetSectionCode ? (
          <>
            <div>{i.targetSectionCode}</div>
            {i.targetDecision && <Typography.Text type="secondary">{TARGET_DECISION[i.targetDecision] ?? i.targetDecision}</Typography.Text>}
          </>
        ) : i.recommendation?.sectionCode ? (
          <Typography.Text type="secondary">рекомендована: {i.recommendation.sectionCode}</Typography.Text>
        ) : (
          '—'
        ),
    },
    {
      title: 'Размещение',
      dataIndex: 'placementStatus',
      render: (s: string) => <Tag color={ITEM_PLACEMENT_STATUS[s]?.color}>{ITEM_PLACEMENT_STATUS[s]?.label ?? s}</Tag>,
    },
    {
      title: 'Размещено',
      render: (_, i) => (i.placedQuantity !== null ? `${fmtQty(i.placedQuantity)} ${PCS} → ${i.placedSectionCode ?? '—'}` : '—'),
    },
    { title: 'Комментарий', render: (_, i) => i.discrepancyComment || i.comment || '—' },
  ];

  const isDraft = r.status === 'DRAFT';
  // Бэкенд не отменяет документ в DONE/CANCELLED и если часть позиций уже размещена и проведена по остатку.
  const canCancel = ['DRAFT', 'RECEIVING', 'PLACEMENT'].includes(r.status) && !r.items.some((i) => i.placementStatus === 'PLACED');

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Link to="/receipts">← Все поступления</Link>

      <Card
        title={
          <Space wrap>
            <span>Поступление {r.number}</span>
            <Tag color={st?.color}>{st?.label ?? r.status}</Tag>
          </Space>
        }
      >
        <ErrorAlert error={error} style={{ marginBottom: 16 }} />

        <Space wrap style={{ marginBottom: 16 }}>
          {isDraft && (
            <>
              <Button onClick={() => navigate(`/receipts/${r.id}/edit`)}>Изменить</Button>
              <Button type="primary" loading={recommend.isPending} onClick={() => recommend.mutate()}>
                {recs ? 'Пересчитать размещение' : 'Рассчитать размещение'}
              </Button>
            </>
          )}
          {r.status === 'RECEIVING' && (
            <Button type="primary" onClick={() => setDialog('task')}>
              Создать задание на размещение
            </Button>
          )}
          {canCancel && (
            <Button danger onClick={() => setDialog('cancel')}>
              Отменить поступление
            </Button>
          )}
        </Space>

        <Descriptions size="small" column={{ xs: 1, md: 2 }} bordered>
          <Descriptions.Item label="Склад">{warehouse?.name ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Поставщик">{r.supplierName}</Descriptions.Item>
          <Descriptions.Item label="Документ поставщика">{r.externalDocumentNumber || '—'}</Descriptions.Item>
          <Descriptions.Item label="Получено">{fmtDateTime(r.receivedAt)}</Descriptions.Item>
          <Descriptions.Item label="Создано">
            {fmtDateTime(r.createdAt)}, {nameOf(names, r.createdBy)}
          </Descriptions.Item>
          <Descriptions.Item label="Подтверждено">{fmtDateTime(r.confirmedAt)}</Descriptions.Item>
          <Descriptions.Item label="Завершено">{fmtDateTime(r.completedAt)}</Descriptions.Item>
          {r.cancelReason && <Descriptions.Item label="Причина отмены">{r.cancelReason}</Descriptions.Item>}
        </Descriptions>
      </Card>

      <Card title="Позиции">
        <Table<ReceiptItem>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={r.items}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
      </Card>

      {isDraft && recs && (
        <RecommendationPanel
          receipt={r}
          recommendations={recs}
          confirming={confirm.isPending}
          onConfirm={(decisions) => {
            setError(null);
            confirm.mutate(decisions);
          }}
          onClose={() => setRecs(null)}
        />
      )}

      {(r.status === 'PLACEMENT' || r.status === 'DONE') && <PlacementTasksCard receiptId={r.id} />}

      <ReasonModal
        open={dialog === 'cancel'}
        title={`Отменить поступление ${r.number}`}
        label="Причина отмены"
        okText="Отменить поступление"
        danger
        description={r.status === 'PLACEMENT' ? 'Задание на размещение тоже будет отменено.' : undefined}
        loading={cancel.isPending}
        onSubmit={(reason) => {
          setError(null);
          cancel.mutate(reason);
        }}
        onCancel={() => setDialog(null)}
      />

      <Modal
        title="Задание на размещение"
        open={dialog === 'task'}
        okText="Создать задание"
        cancelText="Назад"
        okButtonProps={{ loading: createTask.isPending }}
        onOk={() => {
          setError(null);
          createTask.mutate();
        }}
        onCancel={() => setDialog(null)}
        afterClose={() => setAssignee(undefined)}
      >
        <Form layout="vertical">
          <Form.Item label="Исполнитель (необязательно)">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Работник склада"
              loading={workers.isLoading}
              value={assignee}
              onChange={setAssignee}
              options={(workers.data ?? []).map((w) => ({ value: w.id, label: `${w.fullName} (${w.login})` }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
