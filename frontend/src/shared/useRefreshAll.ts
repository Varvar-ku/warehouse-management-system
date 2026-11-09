import { useQueryClient } from '@tanstack/react-query';

/** Обновляет всё, что может измениться после действия кладовщика над заявкой. */
export function useRefreshAll() {
  const qc = useQueryClient();
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['requests'] }),
      qc.invalidateQueries({ queryKey: ['reservations'] }),
      qc.invalidateQueries({ queryKey: ['picking'] }),
    ]);
}
