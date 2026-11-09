import { useEffect, useMemo, useState } from 'react';
import { Alert, InputNumber, Modal, Table, Typography, message, type TableColumnsType } from 'antd';
import { useMutation } from '@tanstack/react-query';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtQty, PCS } from '../../shared/format';
import { round4 } from '../../shared/math';
import { useRefreshAll } from '../../shared/useRefreshAll';
import { reserveRequest } from '../requests/storekeeperApi';
import type { RequestItem, RequestResponse } from '../requests/types';
import { useAvailability } from './hooks';

interface Props {
  request: RequestResponse;
  open: boolean;
  onClose: () => void;
}

/** Частичное обеспечение: кладовщик решает, какое количество по каждой позиции резервировать. */
export default function ReserveDialog({ request, open, onClose }: Props) {
  const refresh = useRefreshAll();
  const availability = useAvailability(request, open);
  const byProduct = useMemo(
    () => new Map((availability.data ?? []).map((a) => [a.productId, a.available])),
    [availability.data],
  );
  const [values, setValues] = useState<Record<string, number>>({});
  const [error, setError] = useState<unknown>(null);

  // Когда пришла доступность, подставляем по умолчанию «сколько есть, но не больше запрошенного».
  useEffect(() => {
    if (!open || !availability.data) return;
    const next: Record<string, number> = {};
    for (const i of request.items) {
      const available = byProduct.get(i.productId) ?? 0;
      next[i.id] = round4(Math.max(0, Math.min(i.requestedQuantity, available)));
    }
    setValues(next);
  }, [open, availability.data, byProduct, request.items]);

  const total = round4(Object.values(values).reduce((s, v) => s + (v || 0), 0));

  const reserve = useMutation({
    mutationFn: () =>
      reserveRequest(
        request.id,
        request.items.map((i) => ({ itemId: i.id, approvedQuantity: values[i.id] ?? 0 })),
      ),
    onSuccess: () => {
      message.success('Резерв выполнен');
      onClose();
      return refresh();
    },
    onError: setError,
  });

  const columns: TableColumnsType<RequestItem> = [
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Запрошено', dataIndex: 'requestedQuantity', align: 'right', render: fmtQty },
    { title: 'Доступно', align: 'right', render: (_, i) => fmtQty(byProduct.get(i.productId)) },
    {
      title: 'Резервировать',
      align: 'right',
      width: 170,
      render: (_, i) => (
        <InputNumber
          min={0}
          max={i.requestedQuantity}
          precision={0}
          value={values[i.id] ?? 0}
          addonAfter={PCS}
          onChange={(v) => setValues((prev) => ({ ...prev, [i.id]: v ?? 0 }))}
        />
      ),
    },
  ];

  return (
    <Modal
      title="Зарезервировать частично"
      open={open}
      width={760}
      okText="Зарезервировать"
      cancelText="Назад"
      okButtonProps={{ loading: reserve.isPending, disabled: total <= 0 || availability.isLoading }}
      onOk={() => {
        setError(null);
        reserve.mutate();
      }}
      onCancel={onClose}
      afterClose={() => setError(null)}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="Товара на складе меньше, чем запрошено. Укажите, сколько резервировать по каждой позиции."
      />
      {availability.isError && <ErrorAlert error={availability.error} onRetry={() => availability.refetch()} style={{ marginBottom: 12 }} />}
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      <Table<RequestItem>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={request.items}
        loading={availability.isLoading}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
        Всего к резерву: {fmtQty(total)}
      </Typography.Text>
    </Modal>
  );
}
