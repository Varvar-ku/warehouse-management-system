import { useCallback, useState } from 'react';

const storageKey = (userId: string, taskId: string) => `placement-accepted:${userId}:${taskId}`;

function read(userId: string, taskId: string): boolean {
  try {
    return localStorage.getItem(storageKey(userId, taskId)) === '1';
  } catch {
    return false;
  }
}

/**
 * «Принять» задание на размещение. В бэкенде у задания размещения нет состояния «принято»
 * (только OPEN, COMPLETED, CANCELLED), поэтому принятие хранится в браузере работника.
 * Задание, по которому уже подтверждена хотя бы одна позиция, считается принятым на любом устройстве.
 * Когда бэкенд получит это состояние, меняется только этот хук.
 */
export function usePlacementAccepted(taskId: string, userId: string | undefined, anyHandled: boolean) {
  const [stored, setStored] = useState(() => (userId ? read(userId, taskId) : false));
  const accept = useCallback(() => {
    setStored(true);
    if (!userId) return;
    try {
      localStorage.setItem(storageKey(userId, taskId), '1');
    } catch {
      /* хранилище недоступно: принятие действует до перезагрузки страницы */
    }
  }, [taskId, userId]);
  return { accepted: stored || anyHandled, accept };
}
