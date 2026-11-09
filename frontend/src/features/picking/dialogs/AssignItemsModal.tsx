import { useMemo, useState } from 'react';
import { Button, InputNumber, Modal, Table, Typography, message, type TableColumnsType } from 'antd';
import { useMutation } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { fmtQty } from '../../../shared/format';
import { round4 } from '../../../shared/math';
import { useRefreshAll } from '../../../shared/useRefreshAll';
import { nameOf } from '../../directory/hooks';
import { assignItems } from '../api';
import { isWorkerActive } from '../statuses';
import type { AssignmentInput, PickingTask } from '../types';

interface Props {
  task: PickingTask;
  names: Map<string, string>;
  open: boolean;
  onClose: () => void;
}

interface Group {
  requestItemId: string;
  lineNo: number;
  title: string;
  unitCode: string;
  remaining: number;
}

const cell = (requestItemId: string, workerId: string) => `${requestItemId}|${workerId}`;

/**
 * Распределение нераспределённого остатка по работникам. Назначение идёт по позиции заявки
 * (requestItemId), одну позицию можно разделить между несколькими работниками.
 */
export default function AssignItemsModal({ task, names, open, onClose }: Props) {
  const refresh = useRefreshAll();
  const [values, setValues] = useState<Record<string, number>>({});
  const [error, setError] = useState<unknown>(null);

  const workers = task.workers.filter((w) => isWorkerActive(w.status, w.completedAt));

  // Остаток к распределению по позиции заявки = сумма строк без работника.
  const groups = useMemo<Group[]>(() => {
    const m = new Map<string, Group>();
    for (const it of task.items) {
      if (it.assignedWorkerId) continue;
      const g = m.get(it.requestItemId) ?? {
        requestItemId: it.requestItemId,
        lineNo: it.lineNo,
        title: `${it.productSku} — ${it.productName}`,
        unitCode: it.unitCode,
        remaining: 0,
      };
      g.remaining = round4(g.remaining + it.requestedQuantity);
      m.set(it.requestItemId, g);
    }
    return [...m.values()].sort((a, b) => a.lineNo - b.lineNo);
  }, [task.items]);

  const rowSum = (g: Group) => round4(workers.reduce((s, w) => s + (values[cell(g.requestItemId, w.workerId)] ?? 0), 0));
  const over = (g: Group) => rowSum(g) > g.remaining;
  const anyOver = groups.some(over);
  const anyValue = groups.some((g) => rowSum(g) > 0);

  const fillAll = (workerId: string) =>
    setValues(() => Object.fromEntries(groups.map((g) => [cell(g.requestItemId, workerId), g.remaining])));

  const assign = useMutation({
    mutationFn: () => {
      const assignments: AssignmentInput[] = [];
      for (const g of groups) {
        for (const w of workers) {
          const quantity = values[cell(g.requestItemId, w.workerId)] ?? 0;
          if (quantity > 0) assignments.push({ requestItemId: g.requestItemId, workerId: w.workerId, quantity });
        }
      }
      return assignItems(task.id, assignments);
    },
    onSuccess: () => {
      message.success('Строки распределены');
      onClose();
      return refresh();
    },
    onError: setError,
  });

  const columns: TableColumnsType<Group> = [
    { title: '№', dataIndex: 'lineNo', width: 50 },
    { title: 'Товар', dataIndex: 'title' },
    { title: 'Не назначено', dataIndex: 'remaining', align: 'right', render: fmtQty },
    ...workers.map(
      (w): TableColumnsType<Group>[number] => ({
        title: (
          <div>
            <div>{nameOf(names, w.workerId)}</div>
            <Button size="small" type="link" style={{ padding: 0 }} onClick={() => fillAll(w.workerId)}>
              Всё ему
            </Button>
          </div>
        ),
        width: 150,
        render: (_, g) => (
          <InputNumber
            min={0}
            max={g.remaining}
            precision={0}
            style={{ width: 120 }}
            status={over(g) ? 'error' : undefined}
            value={values[cell(g.requestItemId, w.workerId)] ?? null}
            onChange={(v) => setValues((prev) => ({ ...prev, [cell(g.requestItemId, w.workerId)]: v ?? 0 }))}
          />
        ),
      }),
    ),
    {
      title: 'Итого',
      align: 'right',
      render: (_, g) =>
        over(g) ? <Typography.Text type="danger">{fmtQty(rowSum(g))} (больше остатка)</Typography.Text> : fmtQty(rowSum(g)),
    },
  ];

  return (
    <Modal
      title="Распределить строки"
      open={open}
      width={Math.min(1100, 520 + workers.length * 160)}
      okText="Назначить"
      cancelText="Назад"
      okButtonProps={{ loading: assign.isPending, disabled: !anyValue || anyOver }}
      onOk={() => {
        setError(null);
        assign.mutate();
      }}
      onCancel={onClose}
      afterClose={() => {
        setValues({});
        setError(null);
      }}
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      {workers.length === 0 ? (
        <Typography.Text type="secondary">В задании нет активных работников. Сначала добавьте работников.</Typography.Text>
      ) : groups.length === 0 ? (
        <Typography.Text type="secondary">Все строки уже распределены.</Typography.Text>
      ) : (
        <>
          <Typography.Paragraph type="secondary">
            Укажите, сколько по каждой позиции отдать каждому работнику. Позицию можно разделить между несколькими.
          </Typography.Paragraph>
          <Table<Group>
            rowKey="requestItemId"
            size="small"
            columns={columns}
            dataSource={groups}
            pagination={false}
            scroll={{ x: 'max-content' }}
          />
        </>
      )}
    </Modal>
  );
}
