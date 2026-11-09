import { Card, Table, Tag, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty } from '../../shared/format';
import { RESERVATION_STATUSES } from '../requests/storekeeperRules';
import type { RequestResponse } from '../requests/types';
import { listReservations, type Reservation } from './api';

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Активен',
  RELEASED: 'Освобождён',
  CONSUMED: 'Выдан',
};

/** Резервы заявки: секции, партии, количества. */
export default function ReservationsCard({ request }: { request: RequestResponse }) {
  const visible = RESERVATION_STATUSES.includes(request.status);
  const q = useQuery({
    queryKey: ['reservations', 'list', request.id, request.status],
    queryFn: () => listReservations(request.id),
    enabled: visible,
  });
  if (!visible) return null;

  const columns: TableColumnsType<Reservation> = [
    { title: 'Товар', dataIndex: 'productSku' },
    { title: 'Секция', dataIndex: 'sectionCode' },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Резерв', dataIndex: 'quantity', align: 'right', render: fmtQty },
    { title: 'Выдано', dataIndex: 'consumedQuantity', align: 'right', render: fmtQty },
    { title: 'Освобождено', dataIndex: 'releasedQuantity', align: 'right', render: fmtQty },
    { title: 'Статус', dataIndex: 'status', render: (s: string) => <Tag>{STATUS_LABEL[s] ?? s}</Tag> },
    { title: 'Создан', dataIndex: 'createdAt', render: fmtDateTime },
  ];

  return (
    <Card title="Резервы заявки">
      {q.isError && <ErrorAlert error={q.error} onRetry={() => q.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Reservation>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={q.data}
        loading={q.isLoading}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Резервов нет' }}
      />
    </Card>
  );
}
