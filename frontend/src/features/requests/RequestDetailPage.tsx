import { useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Result,
  Space,
  Spin,
  Table,
  Timeline,
  message,
  type TableColumnsType,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import { useAuth } from '../../auth/AuthContext';
import ConfirmModal from '../../shared/ConfirmModal';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty } from '../../shared/format';
import StatusTag from '../../shared/StatusTag';
import { useIdempotencyKey } from '../../shared/useIdempotencyKey';
import { useUserNames } from '../directory/hooks';
import AvailabilityCard from '../reservations/AvailabilityCard';
import ReservationsCard from '../reservations/ReservationsCard';
import PickingPanel from '../picking/PickingPanel';
import { useWarehouses } from '../reference/hooks';
import { cancelRequest, deleteDraft, getHistory, getRequest, submitRequest } from './api';
import { USER_CANCELLABLE } from './rules';
import StorekeeperActions from './storekeeper/StorekeeperActions';
import type { RequestItem } from './types';

export default function RequestDetailPage() {
  const { id = '' } = useParams();
  const { user, hasRole } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [form] = Form.useForm<{ reason: string }>();

  const req = useQuery({ queryKey: ['requests', 'detail', id], queryFn: () => getRequest(id) });
  const history = useQuery({
    queryKey: ['requests', 'history', id],
    queryFn: () => getHistory(id),
    enabled: !!req.data,
  });
  const warehouses = useWarehouses();
  const isStorekeeper = hasRole('STOREKEEPER');
  const isStaff = isStorekeeper || hasRole('ADMIN');
  const names = useUserNames([req.data?.requesterId, ...(history.data ?? []).map((h) => h.actorId)], isStaff);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['requests'] });

  const submitKey = useIdempotencyKey();
  const submit = useMutation({
    mutationFn: () => submitRequest(id, submitKey.forBody(id)),
    onSuccess: () => {
      submitKey.reset();
      setError(null);
      message.success('Заявка отправлена');
      return refresh();
    },
    onError: setError,
  });

  const remove = useMutation({
    mutationFn: () => deleteDraft(id),
    onSuccess: () => {
      message.success('Черновик удалён');
      navigate('/requests', { replace: true });
      queryClient.removeQueries({ queryKey: ['requests', 'detail', id] });
      queryClient.removeQueries({ queryKey: ['requests', 'history', id] });
      refresh();
    },
    onError: (e) => {
      setDeleteOpen(false);
      setError(e);
    },
  });

  const cancel = useMutation({
    mutationFn: (reason: string) => cancelRequest(id, reason),
    onSuccess: () => {
      setError(null);
      setCancelOpen(false);
      message.success('Заявка отменена');
      return refresh();
    },
    onError: (e) => {
      setCancelOpen(false);
      setError(e);
    },
  });

  if (req.isLoading) return <Spin />;
  if (req.isError) {
    if (req.error instanceof ApiError && req.error.status === 404) {
      // чужая заявка тоже отвечает 404: это сделано намеренно
      return <Result status="404" title="Заявка не найдена" extra={<Link to="/requests">К списку заявок</Link>} />;
    }
    return <ErrorAlert error={req.error} onRetry={() => req.refetch()} />;
  }
  const r = req.data;
  if (!r) return null;

  const own = hasRole('USER') && r.requesterId === user?.id;
  const canEdit = own && r.status === 'DRAFT';
  const canCancel = own && USER_CANCELLABLE.includes(r.status);
  const showProgress = r.status !== 'DRAFT' && r.status !== 'SUBMITTED';
  const warehouse = warehouses.data?.find((w) => w.id === r.warehouseId);

  const columns: TableColumnsType<RequestItem> = [
    { title: '№', dataIndex: 'lineNo', width: 50 },
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Запрошено, шт', dataIndex: 'requestedQuantity', align: 'right', render: fmtQty },
    ...(showProgress
      ? ([
          { title: 'Одобрено, шт', dataIndex: 'approvedQuantity', align: 'right', render: fmtQty },
          { title: 'Зарезервировано, шт', dataIndex: 'reservedQuantity', align: 'right', render: fmtQty },
          { title: 'Выдано, шт', dataIndex: 'issuedQuantity', align: 'right', render: fmtQty },
          { title: 'Нет в наличии, шт', dataIndex: 'unavailableQuantity', align: 'right', render: fmtQty },
        ] as TableColumnsType<RequestItem>)
      : []),
    { title: 'Комментарий', dataIndex: 'comment', render: (c: string | null) => c || '—' },
  ];

  const actor = (actorId: string | null) =>
    actorId === null
      ? 'система'
      : actorId === user?.id
        ? 'вы'
        : ((isStaff ? names.get(actorId) : undefined) ?? (actorId === r.requesterId ? 'заявитель' : 'сотрудник склада'));
  const events = [...(history.data ?? [])].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Space wrap>
            <span>Заявка {r.number}</span>
            <StatusTag status={r.status} />
          </Space>
        }
        extra={<Link to="/requests">К списку</Link>}
      >
        <ErrorAlert error={error} style={{ marginBottom: 16 }} />

        {isStorekeeper && <StorekeeperActions request={r} onError={setError} />}

        {(canEdit || canCancel) && (
          <Space wrap style={{ marginBottom: 16 }}>
            {canEdit && (
              <>
                <Button type="primary" loading={submit.isPending} onClick={() => submit.mutate()}>
                  Отправить
                </Button>
                <Button onClick={() => navigate(`/requests/${r.id}/edit`)}>Изменить</Button>
                <Button danger onClick={() => setDeleteOpen(true)}>
                  Удалить
                </Button>
              </>
            )}
            {canCancel && (
              <Button danger onClick={() => setCancelOpen(true)}>
                Отменить заявку
              </Button>
            )}
          </Space>
        )}

        <Descriptions column={{ xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2 }} size="small">
          {isStaff && <Descriptions.Item label="Заявитель">{names.get(r.requesterId) ?? '—'}</Descriptions.Item>}
          <Descriptions.Item label="Склад">{warehouse?.name ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Приоритет">{r.priority}</Descriptions.Item>
          <Descriptions.Item label="Создана">{fmtDateTime(r.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Отправлена">{fmtDateTime(r.submittedAt)}</Descriptions.Item>
          <Descriptions.Item label="Проверена">{fmtDateTime(r.reviewedAt)}</Descriptions.Item>
          <Descriptions.Item label="Закрыта">{fmtDateTime(r.closedAt)}</Descriptions.Item>
          {r.issueNumber && <Descriptions.Item label="Документ выдачи">{r.issueNumber}</Descriptions.Item>}
          {r.statusReason && <Descriptions.Item label="Причина статуса">{r.statusReason}</Descriptions.Item>}
          <Descriptions.Item label="Комментарий" span="filled">
            {r.comment || '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Позиции">
        <Table<RequestItem>
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={r.items}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
      </Card>

      {isStorekeeper && <AvailabilityCard request={r} />}
      {isStorekeeper && <ReservationsCard request={r} />}
      {isStorekeeper && <PickingPanel request={r} />}

      <Card title="История статусов">
        {history.isError && <ErrorAlert error={history.error} onRetry={() => history.refetch()} />}
        {history.isLoading && <Spin />}
        <Timeline
          items={events.map((h) => ({
            children: (
              <div>
                <Space wrap size={4}>
                  {h.fromStatus && (
                    <>
                      <StatusTag status={h.fromStatus} />→
                    </>
                  )}
                  <StatusTag status={h.toStatus} />
                </Space>
                <div style={{ color: '#888' }}>
                  {fmtDateTime(h.createdAt)} · {actor(h.actorId)}
                </div>
                {h.comment && <div>{h.comment}</div>}
              </div>
            ),
          }))}
        />
      </Card>

      <ConfirmModal
        open={deleteOpen}
        title="Удалить черновик?"
        description={`Черновик ${r.number} будет удалён. Это действие необратимо.`}
        okText="Удалить"
        danger
        loading={remove.isPending}
        onConfirm={() => {
          setError(null);
          remove.mutate();
        }}
        onCancel={() => setDeleteOpen(false)}
      />

      <Modal
        title="Отменить заявку"
        open={cancelOpen}
        okText="Отменить заявку"
        cancelText="Назад"
        okButtonProps={{ danger: true, loading: cancel.isPending }}
        onOk={() =>
          form
            .validateFields()
            .then((v) => cancel.mutate(v.reason.trim()))
            .catch(() => undefined)
        }
        onCancel={() => setCancelOpen(false)}
        afterClose={() => form.resetFields()}
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            name="reason"
            label="Причина отмены"
            rules={[
              { required: true, whitespace: true, message: 'Укажите причину' },
              { max: 1000, message: 'Не длиннее 1000 символов' },
            ]}
          >
            <Input.TextArea rows={3} maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
