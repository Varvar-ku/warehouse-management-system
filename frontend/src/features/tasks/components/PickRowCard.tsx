import { useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Space, Tag, Typography, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { fmtQty, PCS } from '../../../shared/format';
import { isItemProcessed } from '../../picking/statuses';
import type { PickingItem } from '../../picking/types';
import { pickRow } from '../api';

interface Props {
  taskId: string;
  item: PickingItem;
  /** false: задание завершено, ввод закрыт. */
  editable: boolean;
  /** Задание активно, но работник его ещё не принял: сначала «Принять задание». */
  awaitingAccept?: boolean;
}

interface FormValues {
  quantity: number;
  comment?: string;
}

/** Одна строка сбора: крупная секция, товар, количество и кнопка «Собрано». */
export default function PickRowCard({ taskId, item, editable, awaitingAccept }: Props) {
  const [form] = Form.useForm<FormValues>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  const qty = Form.useWatch('quantity', form) ?? item.requestedQuantity;
  const shortage = qty < item.requestedQuantity;
  const done = isItemProcessed(item.status);

  const pick = useMutation({
    mutationFn: (v: FormValues) => pickRow(taskId, item.id, v.quantity, v.comment?.trim()),
    onSuccess: () => {
      setError(null);
      message.success('Записано');
      return queryClient.invalidateQueries({ queryKey: ['picking'] });
    },
    onError: setError,
  });

  return (
    <Card
      size="small"
      style={done ? { opacity: 0.75 } : undefined}
      title={
        <Space wrap>
          <Typography.Text strong style={{ fontSize: 22 }}>
            {item.sectionCode}
          </Typography.Text>
          {done && <Tag color={item.status === 'SHORTAGE' ? 'orange' : 'green'}>{item.status === 'SHORTAGE' ? 'Недобор' : 'Собрано'}</Tag>}
        </Space>
      }
    >
      <Typography.Paragraph style={{ marginBottom: 4, fontSize: 16 }}>
        {item.productSku} — {item.productName}
      </Typography.Paragraph>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        Партия: {item.batchNumber || '—'} · Нужно:{' '}
        <Typography.Text strong>
          {fmtQty(item.requestedQuantity)} {PCS}
        </Typography.Text>
      </Typography.Paragraph>

      {done ? (
        <>
          <Typography.Text>
            {item.status === 'SHORTAGE' ? 'Недобор' : 'Собрано'}: {fmtQty(item.pickedQuantity)} {PCS}
          </Typography.Text>
          {item.comment && <div style={{ color: '#888' }}>Комментарий: {item.comment}</div>}
        </>
      ) : editable ? (
        <>
          <ErrorAlert error={error} style={{ marginBottom: 12 }} />
          <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            initialValues={{ quantity: item.requestedQuantity }}
            onFinish={(v) => pick.mutate(v)}
          >
            <Form.Item
              name="quantity"
              label="Собрано"
              rules={[{ required: true, type: 'number', message: 'Укажите количество' }]}
            >
              <InputNumber
                size="large"
                inputMode="numeric"
                min={0}
                max={item.requestedQuantity}
                precision={0}
                style={{ width: '100%' }}
                addonAfter={PCS}
              />
            </Form.Item>
            {shortage && (
              <Form.Item
                name="comment"
                label="Причина недобора"
                rules={[
                  { required: true, whitespace: true, message: 'Укажите причину недобора' },
                  { max: 1000, message: 'Не длиннее 1000 символов' },
                ]}
              >
                <Input.TextArea rows={2} maxLength={1000} showCount />
              </Form.Item>
            )}
            <Button type="primary" size="large" block htmlType="submit" loading={pick.isPending}>
              {shortage ? 'Записать с недобором' : 'Собрано'}
            </Button>
          </Form>
        </>
      ) : (
        <Typography.Text type="secondary">{awaitingAccept ? 'Сначала примите задание' : 'Задание завершено'}</Typography.Text>
      )}
    </Card>
  );
}
