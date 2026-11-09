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
import { approveWriteOff, cancelWriteOff, confirmWriteOff, getWriteOff } from './api';
import { WRITE_OFF_REASON, WRITE_OFF_STATUS } from './statuses';
import type { WriteOffItem } from './types';

export default function WriteOffDetailPage() {
  const { id = '' } = useParams();
  const { hasRole } = useAuth();
  const queryClient = useQueryClient();
  const warehouses = useWarehouses();
  const confirmKey = useIdempotencyKey();
  const [error, setError] = useState<unknown>(null);
  const [dialog, setDialog] = useState<'approve' | 'confirm' | 'cancel' | null>(null);

  const q = useQuery({ queryKey: ['write-offs', 'detail', id], queryFn: () => getWriteOff(id) });
  const w = q.data;
  const names = useUserNames([w?.createdBy, w?.approvedBy, w?.confirmedBy], !!w);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['write-offs'] }),
      queryClient.invalidateQueries({ queryKey: ['stock'] }),
    ]);

  const approve = useMutation({
    mutationFn: () => approveWriteOff(id),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      message.success('Списание одобрено');
      return refresh();
    },
    onError: (e) => {
      setDialog(null);
      setError(e);
    },
  });

  // Ключ живёт, пока операция не завершилась успехом: повтор после сбоя сети уйдёт с тем же ключом.
  const confirm = useMutation({
    mutationFn: () => confirmWriteOff(id, confirmKey.get()),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      confirmKey.reset();
      message.success('Списание проведено');
      return refresh();
    },
    onError: (e) => {
      // окно закрываем, чтобы ошибка была видна; повтор уйдёт с тем же Idempotency-Key
      setDialog(null);
      setError(e);
    },
  });

  const cancel = useMutation({
    mutationFn: (reason: string) => cancelWriteOff(id, reason),
    onSuccess: () => {
      setError(null);
      setDialog(null);
      message.success('Списание отменено');
      return refresh();
    },
    onError: (e) => {
      setDialog(null);
      setError(e);
    },
  });

  if (q.isLoading) return <Spin />;
  if (q.isError) return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  if (!w) return null;

  const st = WRITE_OFF_STATUS[w.status];
  const isDraft = w.status === 'DRAFT';
  const waitsApproval = isDraft && w.approvalRequired && !w.approvedAt;
  const isStorekeeper = hasRole('STOREKEEPER');
  const isAdmin = hasRole('ADMIN');

  const columns: TableColumnsType<WriteOffItem> = [
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Секция', dataIndex: 'sectionCode' },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Количество', align: 'right', render: (_, i) => `${fmtQty(i.quantity)} ${PCS}` },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Link to="/write-offs">← Все списания</Link>

      <Card
        title={
          <Space wrap>
            <span>Списание {w.number}</span>
            <Tag color={st?.color}>{st?.label ?? w.status}</Tag>
            {waitsApproval && <Tag color="orange">ждёт одобрения администратора</Tag>}
          </Space>
        }
      >
        <ErrorAlert error={error} style={{ marginBottom: 16 }} />

        {waitsApproval && isStorekeeper && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Проводить это списание можно только после одобрения администратором"
          />
        )}

        <Space wrap size={[12, 12]} style={{ marginBottom: 16 }}>
          {isDraft && isAdmin && w.approvalRequired && !w.approvedAt && (
            <Button type="primary" loading={approve.isPending} onClick={() => setDialog('approve')}>
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
          <Descriptions.Item label="Склад">{warehouses.data?.find((x) => x.id === w.warehouseId)?.name ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Причина">{WRITE_OFF_REASON[w.reasonCode] ?? w.reasonCode}</Descriptions.Item>
          <Descriptions.Item label="Комментарий" span={2}>
            {w.comment || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Создано">
            {fmtDateTime(w.createdAt)}, {nameOf(names, w.createdBy)}
          </Descriptions.Item>
          <Descriptions.Item label="Одобрение">
            {w.approvalRequired ? (w.approvedAt ? `${fmtDateTime(w.approvedAt)}, ${nameOf(names, w.approvedBy)}` : 'требуется') : 'не требуется'}
          </Descriptions.Item>
          <Descriptions.Item label="Проведено">
            {w.confirmedAt ? `${fmtDateTime(w.confirmedAt)}, ${nameOf(names, w.confirmedBy)}` : '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Позиции">
        <Table<WriteOffItem> rowKey="id" size="small" columns={columns} dataSource={w.items} pagination={false} scroll={{ x: 'max-content' }} />
      </Card>

      <ConfirmModal
        open={dialog === 'approve'}
        title="Одобрить списание?"
        description={`Списание ${w.number}`}
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
        title="Провести списание?"
        description="Остаток будет уменьшен. Это действие необратимо."
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
        title={`Отменить списание ${w.number}`}
        label="Причина отмены"
        okText="Отменить списание"
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
