import { useEffect, useRef, useState } from 'react';
import { BellOutlined } from '@ant-design/icons';
import { Badge, Button, Empty, List, Popover, Space, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fmtDateTime } from '../../shared/format';
import { fetchUnreadCount, listNotifications, markAllRead, markRead } from './api';
import type { NotificationItem } from './types';

/** В выпадающем окне колокольчика показываются только последние уведомления; остальные на странице «Уведомления». */
const RECENT = 3;


export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: fetchUnreadCount,
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const count = unread.data?.unread ?? 0;

  // Последние уведомления грузятся при открытии окна и обновляются, пока оно открыто
  const recent = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => listNotifications(0, RECENT),
    enabled: open,
    refetchInterval: open ? 10_000 : false,
  });

  // Новое уведомление означает смену статуса заявки: обновляем открытые карточки и списки заявок,
  // а также историю уведомлений (иначе она показывает новое только после перезагрузки страницы)
  const prev = useRef<number>();
  useEffect(() => {
    const n = unread.data?.unread;
    if (n === undefined) return;
    if (prev.current !== undefined && n > prev.current) {
      queryClient.invalidateQueries({ queryKey: ['requests'] });
      queryClient.invalidateQueries({ queryKey: ['notifications', 'history'] });
      queryClient.invalidateQueries({ queryKey: ['notifications', 'recent'] });
    }
    prev.current = n;
  }, [unread.data, queryClient]);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const readAll = useMutation({ mutationFn: markAllRead, onSuccess: refresh });

  const read = useMutation({ mutationFn: markRead, onSuccess: refresh });
  const onItemClick = (n: NotificationItem) => {
    if (!n.read) read.mutate(n.id);
    setOpen(false);
    navigate(n.requestId ? `/requests/${n.requestId}` : '/notifications');
  };

  const content = (
    <div style={{ width: 320, maxWidth: '80vw' }}>
      <List<NotificationItem>
        size="small"
        loading={recent.isLoading}
        dataSource={recent.data?.content ?? []}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Уведомлений нет" /> }}
        renderItem={(n) => (
          <List.Item style={{ cursor: 'pointer', paddingInline: 0 }} onClick={() => onItemClick(n)}>
            <List.Item.Meta
              title={
                <Typography.Text strong={!n.read} ellipsis style={{ maxWidth: 270 }}>
                  {n.title}
                </Typography.Text>
              }
              description={
                <Space direction="vertical" size={0}>
                  <Typography.Text type="secondary" ellipsis style={{ maxWidth: 270 }}>
                    {n.message}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {fmtDateTime(n.createdAt)}
                  </Typography.Text>
                </Space>
              }
            />
            {!n.read && <Badge status="processing" />}
          </List.Item>
        )}
      />
      <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
        <Button block type="primary" onClick={() => { setOpen(false); navigate('/notifications'); }}>
          Все уведомления
        </Button>
        <Button block disabled={count === 0} loading={readAll.isPending} onClick={() => readAll.mutate()}>
          Прочитать все
        </Button>
      </Space>
    </div>
  );

  return (
    <Badge count={count} size="small" overflowCount={99}>
      <Popover
        trigger="click"
        placement="bottomRight"
        open={open}
        onOpenChange={setOpen}
        content={content}
        title="Уведомления" 
      >
        <Button type="text" shape="circle" icon={<BellOutlined />} aria-label="Уведомления" />
      </Popover>
    </Badge>
  );
}
