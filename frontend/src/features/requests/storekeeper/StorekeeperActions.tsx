import { useRef, useState } from 'react';
import { Alert, Button, Space, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { uuid } from '../../../lib/uuid';
import ConfirmModal from '../../../shared/ConfirmModal';
import ReasonModal from '../../../shared/ReasonModal';
import { useRefreshAll } from '../../../shared/useRefreshAll';
import CreateTaskModal from '../../picking/dialogs/CreateTaskModal';
import { usePickingTaskForRequest } from '../../picking/hooks';
import ReserveDialog from '../../reservations/ReserveDialog';
import { cancelRequest } from '../api';
import { closeRequest, issueRequest, markReadyForIssue, rejectRequest, reserveRequest, reviewRequest } from '../storekeeperApi';
import { storekeeperActions, type StorekeeperAction } from '../storekeeperRules';
import type { RequestResponse } from '../types';

interface Props {
  request: RequestResponse;
  /** Ошибка действия показывается в общем блоке страницы. null сбрасывает её. */
  onError: (e: unknown) => void;
}

interface Run {
  key: StorekeeperAction;
  done: string;
  /** Содержимое запроса (причина, комментарий): при его изменении нужен новый ключ идемпотентности. */
  sig?: string;
  fn: (idempotencyKey: string) => Promise<unknown>;
}

type Dialog = 'reserve' | 'ready' | 'close' | 'reject' | 'cancel' | 'partial' | 'task';

/** Кнопки кладовщика по статусу заявки. Окончательное решение, что допустимо, остаётся за бэкендом. */
export default function StorekeeperActions({ request: r, onError }: Props) {
  const refresh = useRefreshAll();
  // В поздних статусах набор кнопок зависит от того, собрано ли что-то (данные берутся из того же кэша, что и блок комплектации)
  const lateStatus = r.status === 'PICKING_WITH_SHORTAGE' || r.status === 'READY_FOR_ISSUE';
  const { task } = usePickingTaskForRequest(r.id, lateStatus);
  const progress = task.data?.progress;
  const nothingPicked = lateStatus && progress ? Number(progress.pickedQuantity) === 0 : undefined;
  const actions = storekeeperActions(r.status, { nothingPicked });
  const [dialog, setDialog] = useState<Dialog | null>(null);

  // Ключ живёт, пока операция не завершилась успехом: повтор после сбоя сети или двойной клик не выполнят её дважды.
  const keys = useRef(new Map<string, string>());
  const keyOf = (x: Run) => `${r.id}:${x.key}:${x.sig ?? ''}`;

  const run = useMutation({
    mutationFn: (x: Run) => {
      let k = keys.current.get(keyOf(x));
      if (!k) keys.current.set(keyOf(x), (k = uuid()));
      return x.fn(k);
    },
    onSuccess: (_, x) => {
      keys.current.delete(keyOf(x));
      onError(null);
      setDialog(null);
      message.success(x.done);
      return refresh();
    },
    onError: (e) => {
      setDialog(null);
      onError(e);
    },
  });

  const exec = (x: Run) => {
    onError(null);
    run.mutate(x);
  };
  const loading = (key: StorekeeperAction) => run.isPending && run.variables?.key === key;
  const has = (a: StorekeeperAction) => actions.includes(a);
  const close = () => setDialog(null);

  if (actions.length === 0) return null;

  const isFirstReview = r.status === 'SUBMITTED';

  return (
    <>
      {nothingPicked && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="По заявке ничего не собрано"
          description="Оформлять и выдавать нечего. Отмените заявку: резерв освободится."
        />
      )}
      <Space wrap size={[12, 12]} style={{ marginBottom: 16 }}>
        {has('review') && (
          <Button
            type={isFirstReview ? 'primary' : 'default'}
            loading={loading('review')}
            onClick={() => exec({ key: 'review', done: 'Проверка выполнена', fn: (k) => reviewRequest(r.id, k) })}
          >
            {isFirstReview ? 'Проверить' : 'Повторная проверка'}
          </Button>
        )}
        {has('reserve') && (
          <Button type="primary" loading={loading('reserve')} onClick={() => setDialog('reserve')}>
            Зарезервировать
          </Button>
        )}
        {has('reserve-partial') && (
          <Button type="primary" onClick={() => setDialog('partial')}>
            Зарезервировать частично
          </Button>
        )}
        {has('create-task') && (
          <Button type="primary" onClick={() => setDialog('task')}>
            Создать задание комплектации
          </Button>
        )}
        {has('ready') && (
          <Button type="primary" loading={loading('ready')} onClick={() => setDialog('ready')}>
            Готово к выдаче
          </Button>
        )}
        {has('issue') && (
          <Button
            type="primary"
            loading={loading('issue')}
            onClick={() => exec({ key: 'issue', done: 'Заявка выдана', fn: (k) => issueRequest(r.id, undefined, k) })}
          >
            Выдать
          </Button>
        )}
        {has('close') && (
          <Button loading={loading('close')} onClick={() => setDialog('close')}>
            Закрыть заявку
          </Button>
        )}
        {has('reject') && (
          <Button danger onClick={() => setDialog('reject')}>
            Отклонить
          </Button>
        )}
        {has('cancel') && (
          <Button danger onClick={() => setDialog('cancel')}>
            Отменить заявку
          </Button>
        )}
      </Space>

      <ConfirmModal
        open={dialog === 'reserve'}
        title="Зарезервировать товар по заявке?"
        description={`Заявка ${r.number}`}
        okText="Зарезервировать"
        loading={loading('reserve')}
        onConfirm={() => exec({ key: 'reserve', done: 'Резерв выполнен', fn: (k) => reserveRequest(r.id, undefined, k) })}
        onCancel={close}
      />
      <ConfirmModal
        open={dialog === 'ready'}
        title="Заявка собрана и готова к выдаче?"
        description={`Заявка ${r.number}`}
        okText="Готово к выдаче"
        loading={loading('ready')}
        onConfirm={() => exec({ key: 'ready', done: 'Заявка готова к выдаче', fn: (k) => markReadyForIssue(r.id, undefined, k) })}
        onCancel={close}
      />
      <ConfirmModal
        open={dialog === 'close'}
        title="Закрыть заявку?"
        description={`Вы уверены, что хотите закрыть заявку ${r.number}? Это действие завершит её обработку.`}
        okText="Закрыть заявку"
        danger
        loading={loading('close')}
        onConfirm={() => exec({ key: 'close', done: 'Заявка закрыта', fn: (k) => closeRequest(r.id, k) })}
        onCancel={close}
      />

      <ReasonModal
        open={dialog === 'reject'}
        title="Отклонить заявку"
        label="Причина отказа"
        okText="Отклонить"
        danger
        description="Заявитель получит уведомление. Отклонить можно, пока товар не зарезервирован."
        loading={loading('reject')}
        onSubmit={(reason) =>
          exec({ key: 'reject', sig: reason, done: 'Заявка отклонена', fn: (k) => rejectRequest(r.id, reason, k) })
        }
        onCancel={close}
      />
      <ReasonModal
        open={dialog === 'cancel'}
        title="Отменить заявку"
        label="Причина отмены"
        okText="Отменить заявку"
        danger
        loading={loading('cancel')}
        onSubmit={(reason) =>
          exec({ key: 'cancel', sig: reason, done: 'Заявка отменена', fn: (k) => cancelRequest(r.id, reason, k) })
        }
        onCancel={close}
      />
      <ReserveDialog request={r} open={dialog === 'partial'} onClose={close} />
      <CreateTaskModal request={r} open={dialog === 'task'} onClose={close} />
    </>
  );
}
