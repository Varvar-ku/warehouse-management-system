import { useMemo, useState } from 'react';
import { Card, Progress, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime } from '../../shared/format';
import { listPickingTasks } from './api';
import { TASK_STATUS, isTaskActive, label } from './statuses';
import type { PickingTask } from './types';

/** Раздел «Комплектация»: задания по всем заявкам. Управление заданием выполняется в карточке заявки. */
export default function PickingListPage() {
  const [status, setStatus] = useState<string>();
  const list = useQuery({ queryKey: ['picking', 'all'], queryFn: () => listPickingTasks(), refetchInterval: 30_000 });

  const rows = useMemo(
    () =>
      (list.data ?? [])
        .filter((t) => !status || t.status === status)
        .sort((a, b) => {
          // сначала незавершённые, затем свежие
          const act = Number(isTaskActive(b)) - Number(isTaskActive(a));
          return act !== 0 ? act : new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        }),
    [list.data, status],
  );

  const statusOptions = useMemo(
    () => [...new Set((list.data ?? []).map((t) => t.status))].map((s) => ({ value: s, label: label(TASK_STATUS, s) })),
    [list.data],
  );

  const columns: TableColumnsType<PickingTask> = [
    { title: 'Задание', dataIndex: 'number' },
    {
      title: 'Заявка',
      render: (_, t) => <Link to={`/requests/${t.requestId}`}>{t.requestNumber}</Link>,
    },
    { title: 'Статус', dataIndex: 'status', render: (s: string) => <Tag>{label(TASK_STATUS, s)}</Tag> },
    {
      title: 'Прогресс',
      width: 180,
      render: (_, t) =>
        t.progress ? <Progress size="small" percent={Math.round(Number(t.progress.percent ?? 0))} /> : '—',
    },
    { title: 'Работников', align: 'right', render: (_, t) => t.workers?.length ?? '—' },
    { title: 'Создано', dataIndex: 'createdAt', render: fmtDateTime },
    { title: 'Завершено', dataIndex: 'completedAt', render: fmtDateTime },
  ];

  return (
    <Card title="Комплектация">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 240 }}
          options={statusOptions}
          onChange={(v?: string) => setStatus(v)}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<PickingTask>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={rows}
        loading={list.isFetching && !list.data}
        pagination={{ pageSize: 20, showTotal: (total) => `Всего: ${total}` }}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  );
}
