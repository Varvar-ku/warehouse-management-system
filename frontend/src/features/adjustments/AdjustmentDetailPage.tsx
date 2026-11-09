import { useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Spin, Table, Tag, message, type TableColumnsType } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import ConfirmModal from '../../shared/ConfirmModal';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty, PCS } from '../../shared/format';
import ReasonModal from '../../shared/ReasonModal';
import { useIdempotencyKey } from '../../shared/useIdempotencyKey';
import { nameOf, useUserNames } from '../directory/hooks';
import { useWarehouses } from '../reference/hooks';
import { approveAdjustment, cancelAdjustment, confirmAdjustment, getAdjustment } from './api';
import { ADJUSTMENT_STATUS } from './statuses';
import type { AdjustmentItem } from './types';

/** Корректировка: черновик → одобрение администратором (не автором) → проведение кладовщиком. */
export default function AdjustmentDetailPage() {
  const { id = '' } = useParams();
  const { hasRole, user } = useAuth();
  const queryClient = useQueryClient();
  const warehouses = useWarehouses();
  const confirmKey = useIdempotencyKey();
  const [error, setError] = useState<unknown>(null);
  const [dialog, setDialog] = useState<'approve' | 'confirm' | 'cancel' | null>(null);

  const q = useQuery({ queryKey: ['adjustments', 'detail', id], queryFn: () => getAdjustment(id) });
  const a = q.data;
  const names = useUserNames([a?.createdBy, a?.approvedBy, a?.confirmedBy], !!a);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['adjustments'] }),
      queryClient.invalidateQueries({ queryKey: ['shortages'] }),
      queryClient.invalidateQueries({ queryKey: ['stock'] }),
    ]);

  const fail = (e: unknown) => {
    setDialog(null);
    setError(e);
  };

  const approve = useMutation({
    mutationFn: () => approveAdjustment(id),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      message.success('Корректировка одобрена');
      return refresh();
    },
    onError: fail,
  });

  // Ключ живёт, пока операция не завершилась успехом: повтор после сбоя сети не спишет остаток дважды.
  const confirm = useMutation({
    mutationFn: () => confirmAdjustment(id, confirmKey.get()),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      confirmKey.reset();
      message.success('Корректировка проведена');
      return refresh();
    },
    onError: fail,
  });

  const cancel = useMutation({
    mutationFn: (reason: string) => cancelAdjustment(id, reason),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      message.success('Корректировка отменена: недостачи снова свободны');
      return refresh();
    },
    onError: fail,
  });

  if (q.isLoading) return <Spin />;
  if (q.isError) return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  if (!a) return null;

  const st = ADJUSTMENT_STATUS[a.status];
  const isDraft = a.status === 'DRAFT';
  const waitsApproval = isDraft && !a.approvedAt;
  const isStorekeeper = hasRole('STOREKEEPER');
  const isAdmin = hasRole('ADMIN');
  const isAuthor = !!user?.id && user.id === a.createdBy;

  const columns: TableColumnsType<AdjustmentItem> = [
    { title: 'Недостача', dataIndex: 'shortageNumber' },
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Секция', dataIndex: 'sectionCode' },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Спишется', align: 'right', render: (_, i) => `${fmtQty(i.quantity)} ${PCS}` },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Link to="/adjustments">← Все корректировки</Link>

      <Card
        title={
          <Space wrap>
            <span>Корректировка {a.number}</span>
            <Tag color={st?.color}>{st?.label ?? a.status}</Tag>
            {waitsApproval && <Tag color="orange">ждёт одобрения администратора</Tag>}
          </Space>
        }
      >
        <ErrorAlert error={error} style={{ marginBottom: 16 }} />

        {waitsApproval && isStorekeeper && (
          <Alert type="info" showIcon style={{ marginBottom: 16 }} message="Проводить корректировку можно только после одобрения администратором" />
        )}
        {waitsApproval && isAdmin && isAuthor && (
          <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Вы автор этого документа: одобрить его должен другой администратор" />
        )}

        <Space wrap size={[12, 12]} style={{ marginBottom: 16 }}>
          {waitsApproval && isAdmin && (
            <Button type="primary" disabled={isAuthor} loading={approve.isPending} onClick={() => setDialog('approve')}>
              Одобрить
            </Button>
          )}
          {isDraft && isStorekeeper && (
            <>
              <Button type="primary" disabled={waitsApproval} loading={confirm.isPending} onClick={() => setDialog('confirm')}>
                Провести
              </Button>
              <Button danger onClick={() => setDialog('cancel')}>
                Отменить
              </Button>
            </>
          )}
        </Space>

        <Descriptions size="small" column={{ xs: 1, md: 2 }} bordered>
          <Descriptions.Item label="Склад">{warehouses.data?.find((x) => x.id === a.warehouseId)?.name ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Создано">
            {fmtDateTime(a.createdAt)}, {nameOf(names, a.createdBy)}
          </Descriptions.Item>
          <Descriptions.Item label="Комментарий" span={2}>
            {a.comment || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Одобрение">
            {a.approvedAt ? `${fmtDateTime(a.approvedAt)}, ${nameOf(names, a.approvedBy)}` : 'требуется'}
          </Descriptions.Item>
          <Descriptions.Item label="Проведено">
            {a.confirmedAt ? `${fmtDateTime(a.confirmedAt)}, ${nameOf(names, a.confirmedBy)}` : '—'}
          </Descriptions.Item>
          {a.cancelReason && (
            <Descriptions.Item label="Причина отмены" span={2}>
              {a.cancelReason}
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      <Card title="Позиции">
        <Table<AdjustmentItem> rowKey="id" size="small" columns={columns} dataSource={a.items} pagination={false} scroll={{ x: 'max-content' }} />
      </Card>

      <ConfirmModal
        open={dialog === 'approve'}
        title="Одобрить корректировку?"
        description={`Корректировка ${a.number}: после одобрения кладовщик сможет списать недостачу.`}
        okText="Одобрить"
        loading={approve.isPending}
        onConfirm={() => {
          setError(null);
          approve.mutate();
        }}
        onCancel={() => setDialog(null)}
      />
      <ConfirmModal
        open={dialog === 'confirm'}
        title="Провести корректировку?"
        description="Остаток будет уменьшен на количество недостачи. Это действие необратимо."
        okText="Провести"
        loading={confirm.isPending}
        onConfirm={() => {
          setError(null);
          confirm.mutate();
        }}
        onCancel={() => setDialog(null)}
      />
      <ReasonModal
        open={dialog === 'cancel'}
        title={`Отменить корректировку ${a.number}`}
        label="Причина отмены"
        okText="Отменить корректировку"
        danger
        loading={cancel.isPending}
        onSubmit={(reason) => {
          setError(null);
          cancel.mutate(reason);
        }}
        onCancel={() => setDialog(null)}
      />
    </Space>
  );
}
