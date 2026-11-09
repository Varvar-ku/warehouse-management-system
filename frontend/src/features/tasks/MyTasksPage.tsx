import { useState } from 'react';
import { Badge, Card, Empty, List, Progress, Segmented, Space, Spin, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import { listPickingTasks } from '../picking/api';
import { TASK_STATUS, isTaskActive, label } from '../picking/statuses';
import { listPlacementTasks } from './api';
import { isPlacementActive, isPlacementHandled, placementTaskLabel } from './statuses';

type Tab = 'picking' | 'placement';

/** «Мои задания» работника: комплектация и размещение. Мобильный экран: список крупных карточек. */
export default function MyTasksPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('picking');
  const [showDone, setShowDone] = useState(false);

  const picking = useQuery({
    queryKey: ['picking', 'mine'],
    queryFn: () => listPickingTasks(),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  });
  const placement = useQuery({
    queryKey: ['placement', 'list'],
    queryFn: () => listPlacementTasks(),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  });

  const pickingTasks = (picking.data ?? [])
    .filter((t) => showDone || isTaskActive(t))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  const placementTasks = (placement.data ?? [])
    .filter((t) => showDone || isPlacementActive(t))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const activePicking = (picking.data ?? []).filter(isTaskActive).length;
  const activePlacement = (placement.data ?? []).filter(isPlacementActive).length;

  const current = tab === 'picking' ? picking : placement;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Segmented
        block
        size="large"
        value={tab}
        onChange={(v) => setTab(v as Tab)}
        options={[
          { value: 'picking', label: <Badge count={activePicking} offset={[12, 0]}>Комплектация</Badge> },
          { value: 'placement', label: <Badge count={activePlacement} offset={[12, 0]}>Размещение</Badge> },
        ]}
      />
      <Segmented
        value={showDone ? 'all' : 'active'}
        onChange={(v) => setShowDone(v === 'all')}
        options={[
          { value: 'active', label: 'Активные' },
          { value: 'all', label: 'Все' },
        ]}
      />

      {current.isError && <ErrorAlert error={current.error} onRetry={() => current.refetch()} />}
      {current.isLoading && <Spin />}

      {tab === 'picking' && !picking.isLoading && (
        <List
          locale={{ emptyText: <Empty description="Заданий на комплектацию нет" /> }}
          dataSource={pickingTasks}
          renderItem={(t) => (
            <Card
              hoverable
              size="small"
              style={{ marginBottom: 12 }}
              onClick={() => navigate(`/tasks/picking/${t.id}`)}
            >
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <Space wrap>
                  <Typography.Text strong style={{ fontSize: 18 }}>
                    {t.number}
                  </Typography.Text>
                  <Tag color={isTaskActive(t) ? 'processing' : 'default'}>{label(TASK_STATUS, t.status)}</Tag>
                </Space>
                <Typography.Text type="secondary">Заявка {t.requestNumber} · {fmtDateTime(t.createdAt)}</Typography.Text>
                {t.progress && <Progress percent={Math.round(Number(t.progress.percent ?? 0))} />}
              </Space>
            </Card>
          )}
        />
      )}

      {tab === 'placement' && !placement.isLoading && (
        <List
          locale={{ emptyText: <Empty description="Заданий на размещение нет" /> }}
          dataSource={placementTasks}
          renderItem={(t) => {
            const total = t.items.length;
            const placed = t.items.filter((i) => isPlacementHandled(i.placementStatus)).length;
            return (
              <Card
                hoverable
                size="small"
                style={{ marginBottom: 12 }}
                onClick={() => navigate(`/tasks/placement/${t.id}`)}
              >
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Space wrap>
                    <Typography.Text strong style={{ fontSize: 18 }}>
                      Приёмка {t.receiptNumber}
                    </Typography.Text>
                    <Tag color={isPlacementActive(t) ? 'processing' : 'default'}>{placementTaskLabel(t.status)}</Tag>
                  </Space>
                  <Typography.Text type="secondary">{fmtDateTime(t.createdAt)}</Typography.Text>
                  <Progress percent={total ? Math.round((placed / total) * 100) : 0} format={() => `${placed} из ${total}`} />
                </Space>
              </Card>
            );
          }}
        />
      )}
    </Space>
  );
}
