import { Button, Card, Empty, Progress, Result, Space, Spin, Tag, Typography, message } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import { useAuth } from '../../auth/AuthContext';
import ErrorAlert from '../../shared/ErrorAlert';
import { getPlacementTask } from './api';
import PlacementItemCard from './components/PlacementItemCard';
import { usePlacementAccepted } from './placementAccept';
import { isPlacementActive, isPlacementHandled, placementTaskLabel } from './statuses';

/** Экран размещения: работник принимает задание и подтверждает количество по секциям, назначенным кладовщиком. */
export default function PlacementTaskPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const q = useQuery({
    queryKey: ['placement', 'task', id],
    queryFn: () => getPlacementTask(id),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  });
  const anyHandled = (q.data?.items ?? []).some((i) => isPlacementHandled(i.placementStatus));
  const { accepted, accept } = usePlacementAccepted(id, user?.id, anyHandled);

  if (q.isLoading) return <Spin />;
  if (q.isError) {
    if (q.error instanceof ApiError && (q.error.status === 404 || q.error.status === 403)) {
      return <Result status="404" title="Задание не найдено" extra={<Link to="/tasks">К заданиям</Link>} />;
    }
    return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  }
  const t = q.data;
  if (!t) return null;

  const active = isPlacementActive(t);
  const awaitingAccept = active && !accepted;
  const total = t.items.length;
  const handled = t.items.filter((i) => isPlacementHandled(i.placementStatus)).length;
  const items = [...t.items].sort((a, b) => {
    const done = Number(isPlacementHandled(a.placementStatus)) - Number(isPlacementHandled(b.placementStatus));
    return done !== 0 ? done : a.lineNo - b.lineNo;
  });
  const firstOpenId = items.find((i) => !isPlacementHandled(i.placementStatus))?.itemId;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Link to="/tasks">← Мои задания</Link>

      <Card size="small">
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Space wrap>
            <Typography.Text strong style={{ fontSize: 20 }}>
              Приёмка {t.receiptNumber}
            </Typography.Text>
            <Tag color={active ? 'processing' : 'default'}>{placementTaskLabel(t.status)}</Tag>
          </Space>
          <Progress percent={total ? Math.round((handled / total) * 100) : 0} format={() => `${handled} из ${total}`} />
        </Space>
      </Card>

      {awaitingAccept && (
        <Button
          type="primary"
          size="large"
          block
          autoFocus
          onClick={() => {
            accept();
            message.success('Задание принято');
          }}
        >
          Принять задание
        </Button>
      )}

      {items.length === 0 ? (
        <Empty description="В задании нет позиций" />
      ) : (
        items.map((item) => (
          <PlacementItemCard
            key={item.itemId}
            taskId={t.id}
            item={item}
            editable={active && accepted}
            awaitingAccept={awaitingAccept}
            autoFocus={item.itemId === firstOpenId}
          />
        ))
      )}
    </Space>
  );
}
