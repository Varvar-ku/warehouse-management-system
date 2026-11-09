import { useState } from 'react';
import { Button, Card, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty, PCS } from '../../shared/format';
import { nameOf, useUserNames } from '../directory/hooks';
import type { PlacementItem, PlacementTask } from '../tasks/api';
import { placementItemLabel, placementTaskLabel } from '../tasks/statuses';
import { listPlacementTasksForReceipt } from './api';
import ResolveModal from './ResolveModal';

/**
 * Задания на размещение по приёмке. Расхождения, зафиксированные работником, разбирает кладовщик;
 * позицию, которую невозможно разместить (брак, потеря), он может отклонить, чтобы приёмка не зависла.
 */
export default function PlacementTasksCard({ receiptId }: { receiptId: string }) {
  const q = useQuery({
    queryKey: ['placement', 'receipt', receiptId],
    queryFn: () => listPlacementTasksForReceipt(receiptId),
  });
  const names = useUserNames((q.data ?? []).map((t) => t.assigneeId), (q.data?.length ?? 0) > 0);
  const [resolving, setResolving] = useState<{ task: PlacementTask; item: PlacementItem } | null>(null);

  if (q.isLoading) return null;
  if (q.isError) return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  const tasks = q.data ?? [];
  if (tasks.length === 0) return null;

  return (
    <>
      {tasks.map((t) => {
        const columns: TableColumnsType<PlacementItem> = [
          { title: '№', dataIndex: 'lineNo', width: 50 },
          { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
          {
            title: 'План',
            render: (_, i) => `${fmtQty(i.quantity)} ${PCS} → ${i.targetSectionCode ?? '—'}`,
          },
          {
            title: 'Факт',
            render: (_, i) =>
              i.placedQuantity !== null ? `${fmtQty(i.placedQuantity)} ${PCS} → ${i.placedSectionCode ?? '—'}` : '—',
          },
          {
            title: 'Статус',
            dataIndex: 'placementStatus',
            render: (s: string) => (
              <Tag color={s === 'PLACED' ? 'success' : s === 'DISCREPANCY' ? 'orange' : s === 'REJECTED' ? 'red' : 'default'}>
                {placementItemLabel(s)}
              </Tag>
            ),
          },
          { title: 'Комментарий', dataIndex: 'discrepancyComment', render: (c: string | null) => c || '—' },
          {
            title: '',
            render: (_, i) =>
              t.status !== 'OPEN' ? null : i.placementStatus === 'DISCREPANCY' ? (
                <Button size="small" type="primary" onClick={() => setResolving({ task: t, item: i })}>
                  Разобрать
                </Button>
              ) : i.placementStatus === 'PENDING' ? (
                <Button size="small" danger onClick={() => setResolving({ task: t, item: i })}>
                  Отклонить
                </Button>
              ) : null,
          },
        ];
        return (
          <Card
            key={t.id}
            title={
              <Space wrap>
                <span>Задание на размещение</span>
                <Tag color={t.status === 'OPEN' ? 'processing' : 'default'}>{placementTaskLabel(t.status)}</Tag>
              </Space>
            }
            extra={
              <Typography.Text type="secondary">
                Исполнитель: {t.assigneeId ? nameOf(names, t.assigneeId) : 'не назначен'} · создано {fmtDateTime(t.createdAt)}
                {t.completedAt ? ` · завершено ${fmtDateTime(t.completedAt)}` : ''}
              </Typography.Text>
            }
          >
            <Table<PlacementItem>
              rowKey="itemId"
              size="small"
              columns={columns}
              dataSource={t.items}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
          </Card>
        );
      })}
      <ResolveModal task={resolving?.task ?? tasks[0]} item={resolving?.item ?? null} onClose={() => setResolving(null)} />
    </>
  );
}
