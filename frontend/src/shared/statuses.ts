export type RequestStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'PARTIALLY_AVAILABLE'
  | 'RESERVED'
  | 'RESERVED_PARTIAL'
  | 'PICKING'
  | 'PICKED'
  | 'PICKING_WITH_SHORTAGE'
  | 'READY_FOR_ISSUE'
  | 'ISSUED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'CLOSED';

export const REQUEST_STATUS: Record<RequestStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  SUBMITTED: { label: 'Отправлена', color: 'blue' },
  // Устарел: бэкенд больше не переводит заявки в этот статус, он остался только в старых записях истории
  UNDER_REVIEW: { label: 'На проверке (устар.)', color: 'blue' },
  APPROVED: { label: 'Одобрена', color: 'green' },
  PARTIALLY_AVAILABLE: { label: 'Частично доступна', color: 'orange' },
  RESERVED: { label: 'Зарезервирована', color: 'green' },
  RESERVED_PARTIAL: { label: 'Зарезервирована частично', color: 'orange' },
  PICKING: { label: 'Комплектуется', color: 'processing' },
  PICKED: { label: 'Собрана', color: 'cyan' },
  PICKING_WITH_SHORTAGE: { label: 'Собрана с недобором', color: 'orange' },
  READY_FOR_ISSUE: { label: 'Готова к выдаче', color: 'green' },
  ISSUED: { label: 'Выдана', color: 'success' },
  REJECTED: { label: 'Отклонена', color: 'red' },
  CANCELLED: { label: 'Отменена', color: 'default' },
  CLOSED: { label: 'Закрыта', color: 'default' },
};

/** Для фильтров: устаревший UNDER_REVIEW не предлагаем, заявок в нём больше не бывает. */
export const REQUEST_STATUS_OPTIONS = (Object.keys(REQUEST_STATUS) as RequestStatus[])
  .filter((s) => s !== 'UNDER_REVIEW')
  .map((s) => ({
    value: s,
    label: REQUEST_STATUS[s].label,
  }));
