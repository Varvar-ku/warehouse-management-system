import { useEffect, useRef, useState } from 'react';
import { Button, Card, Progress, Space, Spin, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty } from '../../shared/format';
import { nameOf, useUserNames } from '../directory/hooks';
import { PICKING_STATUSES } from '../requests/storekeeperRules';
import type { RequestResponse } from '../requests/types';
import AddWorkersModal from './dialogs/AddWorkersModal';
import AssignItemsModal from './dialogs/AssignItemsModal';
import CompleteTaskModal from './dialogs/CompleteTaskModal';
import RemoveWorkerModal from './dialogs/RemoveWorkerModal';
import { usePickingTaskForRequest } from './hooks';
import { ITEM_STATUS, TASK_STATUS, WORKER_ROLE, WORKER_STATUS, isTaskActive, isWorkerActive, label } from './statuses';
import type { PickingItem, PickingWorker, WorkerProgress } from './types';

interface WorkerRow {
  worker: PickingWorker;
  progress?: WorkerProgress;
}

/** Блок комплектации в карточке заявки (кладовщик): работники, прогресс, строки, управление заданием. */
export default function PickingPanel({ request }: { request: RequestResponse }) {
  const visible = PICKING_STATUSES.includes(request.status);
  const { list, task, hasTask } = usePickingTaskForRequest(request.id, visible);
  const t = task.data;
  const queryClient = useQueryClient();

  const [addOpen, setAddOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);
  const [removing, setRemoving] = useState<PickingWorker | null>(null);

  const names = useUserNames(
    [...(t?.workers.map((w) => w.workerId) ?? []), ...(t?.items.map((i) => i.assignedWorkerId) ?? [])],
    visible && !!t,
  );

  // Когда задание само завершилось (работники собрали всё), статус заявки на странице устарел.
  const prevStatus = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (t && prevStatus.current && prevStatus.current !== t.status) {
      queryClient.invalidateQueries({ queryKey: ['requests'] });
    }
    prevStatus.current = t?.status;
  }, [t, queryClient]);

  if (!visible) return null;

  if (list.isLoading || (hasTask && task.isLoading)) {
    return (
      <Card title="Комплектация">
        <Spin />
      </Card>
    );
  }
  if (list.isError || task.isError) {
    const retry = () => {
      list.refetch();
      if (hasTask) task.refetch();
    };
    return (
      <Card title="Комплектация">
        <ErrorAlert error={list.error ?? task.error} onRetry={retry} />
      </Card>
    );
  }
  if (!t) {
    return (
      <Card title="Комплектация">
        <Typography.Text type="secondary">Задание комплектации для этой заявки не создавалось.</Typography.Text>
      </Card>
    );
  }

  const active = isTaskActive(t);
  const progress = t.progress;
  const percent = Math.max(0, Math.min(100, Math.round(Number(progress?.percent ?? 0))));
  const progressByWorker = new Map((progress?.workers ?? []).map((p) => [p.workerId, p]));
  const workerRows: WorkerRow[] = t.workers.map((worker) => ({ worker, progress: progressByWorker.get(worker.workerId) }));
  const hasUnassigned = (progress?.unassignedQuantity ?? 0) > 0;

  const workerColumns: TableColumnsType<WorkerRow> = [
    { title: 'Работник', render: (_, r) => nameOf(names, r.worker.workerId) },
    { title: 'Роль', render: (_, r) => label(WORKER_ROLE, r.worker.role) },
    { title: 'Статус', render: (_, r) => <Tag>{label(WORKER_STATUS, r.worker.status)}</Tag> },
    { title: 'Назначено', align: 'right', render: (_, r) => fmtQty(r.progress?.assignedQuantity) },
    { title: 'Собрано', align: 'right', render: (_, r) => fmtQty(r.progress?.pickedQuantity) },
    {
      title: 'Прогресс',
      width: 160,
      render: (_, r) => <Progress size="small" percent={Math.round(Number(r.progress?.percent ?? 0))} />,
    },
    {
      title: '',
      width: 130,
      render: (_, r) =>
        active && isWorkerActive(r.worker.status, r.worker.completedAt) ? (
          <Button size="small" onClick={() => setRemoving(r.worker)}>
            Заменить / снять
          </Button>
        ) : null,
    },
  ];

  const itemColumns: TableColumnsType<PickingItem> = [
    { title: '№', dataIndex: 'lineNo', width: 50 },
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Секция', dataIndex: 'sectionCode' },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Нужно', dataIndex: 'requestedQuantity', align: 'right', render: fmtQty },
    { title: 'Собрано', dataIndex: 'pickedQuantity', align: 'right', render: fmtQty },
    { title: 'Статус', dataIndex: 'status', render: (s: string) => <Tag>{label(ITEM_STATUS, s)}</Tag> },
    {
      title: 'Работник',
      dataIndex: 'assignedWorkerId',
      render: (id: string | null) => (id ? nameOf(names, id) : <Typography.Text type="warning">не назначен</Typography.Text>),
    },
    { title: 'Собрано в', dataIndex: 'pickedAt', render: fmtDateTime },
    { title: 'Комментарий', dataIndex: 'comment', render: (c: string | null) => c || '—' },
  ];

  return (
    <>
      <Card
        title={
          <Space wrap>
            <span>Комплектация {t.number}</span>
            <Tag color={active ? 'processing' : 'default'}>{label(TASK_STATUS, t.status)}</Tag>
          </Space>
        }
        extra={
          active && (
            <Space wrap>
              <Button size="small" onClick={() => setAddOpen(true)}>
                Добавить работников
              </Button>
              <Button size="small" type={hasUnassigned ? 'primary' : 'default'} onClick={() => setAssignOpen(true)}>
                Распределить строки
              </Button>
              <Button size="small" onClick={() => setCompleteOpen(true)}>
                Завершить
              </Button>
            </Space>
          )
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {progress && (
            <div>
              <Progress percent={percent} />
              <Typography.Text type="secondary">
                Собрано {fmtQty(progress.pickedQuantity)} из {fmtQty(progress.totalQuantity)} · обработано строк{' '}
                {progress.itemsProcessed} из {progress.itemsTotal}
                {hasUnassigned && ` · не назначено: ${fmtQty(progress.unassignedQuantity)}`}
              </Typography.Text>
            </div>
          )}

          <Table<WorkerRow>
            rowKey={(r) => r.worker.id}
            size="small"
            columns={workerColumns}
            dataSource={workerRows}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: 'Работников пока нет' }}
          />

          <Table<PickingItem>
            rowKey="id"
            size="small"
            columns={itemColumns}
            dataSource={t.items}
            pagination={false}
            scroll={{ x: 'max-content' }}
          />
        </Space>
      </Card>

      <AddWorkersModal task={t} open={addOpen} onClose={() => setAddOpen(false)} />
      <AssignItemsModal task={t} names={names} open={assignOpen} onClose={() => setAssignOpen(false)} />
      <CompleteTaskModal task={t} open={completeOpen} onClose={() => setCompleteOpen(false)} />
      <RemoveWorkerModal task={t} worker={removing} names={names} onClose={() => setRemoving(null)} />
    </>
  );
}
