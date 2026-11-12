import { useMemo, useState } from 'react';
import { Button, Card, Empty, Input, Progress, Result, Space, Spin, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import { useAuth } from '../../auth/AuthContext';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtQty } from '../../shared/format';
import { getPickingTask } from '../picking/api';
import { TASK_STATUS, WORKER_STATUS, isItemProcessed, isTaskActive, label } from '../picking/statuses';
import { acceptPickingTask } from './api';
import PickRowCard from './components/PickRowCard';

/** Экран сбора: свои строки задания, общий прогресс, ввод собранного количества. */
export default function PickingTaskPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [error, setError] = useState<unknown>(null);

  const q = useQuery({
    queryKey: ['picking', 'task', id],
    queryFn: () => getPickingTask(id),
    refetchInterval: (query) => (query.state.data && isTaskActive(query.state.data) ? 15_000 : false),
    refetchIntervalInBackground: false,
  });
  const t = q.data;

  const accept = useMutation({
    mutationFn: () => acceptPickingTask(id, user!.id),
    onSuccess: () => {
      setError(null);
      message.success('Задание принято');
      return queryClient.invalidateQueries({ queryKey: ['picking'] });
    },
    onError: setError,
  });

  // Строки: сначала несобранные, затем по секции (маршрут обхода в бэкенде пока не хранится).
  const rows = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (t?.items ?? [])
      .filter((i) => !needle || i.sectionCode.toLowerCase().includes(needle) || i.productSku.toLowerCase().includes(needle))
      .sort((a, b) => {
        const done = Number(isItemProcessed(a.status)) - Number(isItemProcessed(b.status));
        return done !== 0 ? done : a.sectionCode.localeCompare(b.sectionCode) || a.lineNo - b.lineNo;
      });
  }, [t?.items, search]);

  if (q.isLoading) return <Spin />;
  if (q.isError) {
    if (q.error instanceof ApiError && (q.error.status === 404 || q.error.status === 403)) {
      return <Result status="404" title="Задание не найдено" extra={<Link to="/tasks">К заданиям</Link>} />;
    }
    return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  }
  if (!t) return null;

  const active = isTaskActive(t);
  const me = t.workers.find((w) => w.workerId === user?.id);
  const canAccept = active && me?.status === 'ASSIGNED';
  // Собирать можно только после «Принять»: пока статус ASSIGNED, ввод закрыт (бэкенд тоже откажет: TASK_NOT_ACCEPTED)
  const awaitingAccept = canAccept;
  const firstOpenId = rows.find((r) => !isItemProcessed(r.status))?.id;
  const progress = t.progress;
  const percent = Math.round(Number(progress?.percent ?? 0));

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Link to="/tasks">← Мои задания</Link>

      <Card size="small">
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Space wrap>
            <Typography.Text strong style={{ fontSize: 20 }}>
              {t.number}
            </Typography.Text>
            <Tag color={active ? 'processing' : 'default'}>{label(TASK_STATUS, t.status)}</Tag>
            {me && <Tag>{label(WORKER_STATUS, me.status)}</Tag>}
          </Space>
          <Typography.Text type="secondary">Заявка {t.requestNumber}</Typography.Text>
          {progress && (
            <>
              <Progress percent={percent} />
              <Typography.Text type="secondary">
                Собрано {fmtQty(progress.pickedQuantity)} из {fmtQty(progress.totalQuantity)}
              </Typography.Text>
            </>
          )}
        </Space>
      </Card>

      <ErrorAlert error={error} />

      {canAccept && (
        <Button type="primary" size="large" block autoFocus loading={accept.isPending} onClick={() => accept.mutate()}>
          Принять задание
        </Button>
      )}

      <Input.Search
        allowClear
        size="large"
        placeholder="Код секции или артикул (можно сканером)"
        onChange={(e) => setSearch(e.target.value)}
        onSearch={setSearch}
      />

      {rows.length === 0 ? (
        <Empty description={search ? 'Ничего не найдено' : 'Для вас нет строк в этом задании'} />
      ) : (
        rows.map((item) => (
          <PickRowCard
            key={item.id}
            taskId={t.id}
            item={item}
            editable={active && !awaitingAccept}
            awaitingAccept={awaitingAccept}
            autoFocus={!search && item.id === firstOpenId}
          />
        ))
      )}
    </Space>
  );
}
