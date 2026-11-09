import { useMemo } from 'react';
import { Button, Card, Table, Typography, type TableColumnsType } from 'antd';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtQty } from '../../shared/format';
import { AVAILABILITY_STATUSES } from '../requests/storekeeperRules';
import type { RequestItem, RequestResponse } from '../requests/types';
import { useAvailability } from './hooks';

/** Доступность товара на складе перед резервом (разд. 11 ТЗ). */
export default function AvailabilityCard({ request }: { request: RequestResponse }) {
  const visible = AVAILABILITY_STATUSES.includes(request.status);
  const q = useAvailability(request, visible);
  const byProduct = useMemo(() => new Map((q.data ?? []).map((a) => [a.productId, a])), [q.data]);
  if (!visible) return null;

  const columns: TableColumnsType<RequestItem> = [
    { title: '№', dataIndex: 'lineNo', width: 50 },
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Запрошено, шт', dataIndex: 'requestedQuantity', align: 'right', render: fmtQty },
    {
      title: 'Доступно, шт',
      align: 'right',
      render: (_, i) => fmtQty(byProduct.get(i.productId)?.available),
    },
    {
      title: 'Не хватает',
      align: 'right',
      render: (_, i) => {
        const shortage = byProduct.get(i.productId)?.shortage;
        return shortage && shortage > 0 ? <Typography.Text type="danger">{fmtQty(shortage)}</Typography.Text> : fmtQty(shortage);
      },
    },
  ];

  return (
    <Card
      title="Доступность на складе"
      extra={
        <Button size="small" loading={q.isFetching} onClick={() => q.refetch()}>
          Обновить
        </Button>
      }
    >
      {q.isError && <ErrorAlert error={q.error} onRetry={() => q.refetch()} style={{ marginBottom: 16 }} />}
      <Table<RequestItem>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={request.items}
        loading={q.isLoading}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  );
}
