import { useQuery } from '@tanstack/react-query';
import { getPickingTask, listPickingTasks } from './api';
import { isTaskActive } from './statuses';
import type { PickingTask } from './types';

/**
 * Задание комплектации заявки. В заявке нет ссылки на задание, поэтому ищем его в списке по requestId:
 * берём незавершённое, а если такого нет, самое свежее. Пока задание активно, оно обновляется раз в 15 секунд
 * (работники собирают с других устройств).
 */
export function usePickingTaskForRequest(requestId: string, enabled: boolean) {
  const list = useQuery({
    queryKey: ['picking', 'list', requestId],
    queryFn: () => listPickingTasks(requestId),
    enabled,
  });

  const mine = (list.data ?? [])
    .filter((t) => t.requestId === requestId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  const current: PickingTask | undefined = mine.find(isTaskActive) ?? mine[0];

  const task = useQuery({
    queryKey: ['picking', 'task', current?.id],
    queryFn: () => getPickingTask(current!.id),
    enabled: !!current,
    refetchInterval: (q) => (q.state.data && isTaskActive(q.state.data) ? 15_000 : false),
    refetchIntervalInBackground: false,
  });

  return { list, task, hasTask: !!current };
}
