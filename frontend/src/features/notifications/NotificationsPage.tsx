import { useState } from 'react';
import { Button, Card, Empty, List, Pagination, Space, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import { fetchUnreadCount, listNotifications, markAllRead, markRead } from './api';
import type { NotificationItem } from './types';

const PAGE_SIZE = 10;

export default function NotificationsPage() {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const unread = useQuery({ queryKey: ['notifications', 'unread'], queryFn: fetchUnreadCount, refetchInterval: 10_000, refetchIntervalInBackground: false });
  const list = useQuery({
    queryKey: ['notifications', 'history', page],
    queryFn: () => listNotifications(page, PAGE_SIZE),
    // уведомления приходят асинхронно (через события): страница подтягивает новые без перезагрузки
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['notifications'] });
    await queryClient.invalidateQueries({ queryKey: ['notifications', 'history'] });
  };
  const read = useMutation({ mutationFn: markRead, onSuccess: refresh, onError: () => message.error('Не удалось отметить уведомление прочитанным') });
  const readAll = useMutation({ mutationFn: markAllRead, onSuccess: refresh, onError: () => message.error('Не удалось прочитать уведомления') });

  const onItemClick = (n: NotificationItem) => {
    if (!n.read) read.mutate(n.id);
    if (n.requestId) navigate(`/requests/${n.requestId}`);
  };

  return (
    <Card
      title="Уведомления"
      extra={<Button onClick={() => readAll.mutate()} disabled={(unread.data?.unread ?? 0) === 0} loading={readAll.isPending}>Прочитать все</Button>}
    >
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} />}
      <List<NotificationItem>
        loading={list.isLoading}
        dataSource={list.data?.content ?? []}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Уведомлений нет" /> }}
        renderItem={(n) => (
          <List.Item style={{ cursor: n.requestId ? 'pointer' : 'default' }} onClick={() => onItemClick(n)}>
            <List.Item.Meta
              title={<Typography.Text strong={!n.read}>{n.title}</Typography.Text>}
              description={<Space direction="vertical" size={2}><span>{n.message}</span><Typography.Text type="secondary">{fmtDateTime(n.createdAt)}</Typography.Text></Space>}
            />
            {!n.read && <Typography.Text type="secondary">Новое</Typography.Text>}
          </List.Item>
        )}
      />
      {(list.data?.totalElements ?? 0) > PAGE_SIZE && (
        <Pagination current={page + 1} pageSize={PAGE_SIZE} total={list.data?.totalElements ?? 0} onChange={(p) => setPage(p - 1)} showSizeChanger={false} style={{ marginTop: 16, textAlign: 'right' }} />
      )}
    </Card>
  );
}
