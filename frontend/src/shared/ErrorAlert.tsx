import type { CSSProperties } from 'react';
import { Alert, Button, Typography } from 'antd';
import { ApiError, describeError } from '../api/errors';

interface Props {
  error: unknown;
  onRetry?: () => void;
  style?: CSSProperties;
}

/** Единый показ ошибки API: сообщение, детали и trace_id (его можно скопировать и передать разработчику). */
export default function ErrorAlert({ error, onRetry, style }: Props) {
  if (!error) return null;
  const { message, traceId } = describeError(error);
  const changed = error instanceof ApiError && error.code === 'CONCURRENT_MODIFICATION';
  const description =
    changed || traceId ? (
      <>
        {changed && <div>{message}</div>}
        {traceId && (
          <Typography.Text type="secondary" copyable={{ text: traceId }}>
            trace_id: {traceId}
          </Typography.Text>
        )}
      </>
    ) : undefined;

  return (
    <Alert
      type="error"
      showIcon
      style={style}
      message={changed ? 'Данные изменились' : message}
      description={description}
      action={
        onRetry ? (
          <Button size="small" onClick={onRetry}>
            {changed ? 'Обновить' : 'Повторить'}
          </Button>
        ) : undefined
      }
    />
  );
}
