import { useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Space, Tag, Typography, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { PCS, fmtQty } from '../../../shared/format';
import { confirmPlacement, type PlacementItem } from '../api';
import { isPlacementHandled, placementItemLabel } from '../statuses';

interface Props {
  taskId: string;
  item: PlacementItem;
  editable: boolean;
  /** Задание активно, но работник его ещё не принял. */
  awaitingAccept?: boolean;
}

interface FormValues {
  quantity: number;
  comment?: string;
}

/**
 * Одна позиция размещения. Секцию выбирает кладовщик при подтверждении приёмки, работник её не меняет:
 * он видит назначенную секцию и подтверждает фактическое количество. Расхождение по количеству разбирает кладовщик.
 */
export default function PlacementItemCard({ taskId, item, editable, awaitingAccept }: Props) {
  const [form] = Form.useForm<FormValues>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);

  const quantity = Form.useWatch('quantity', form);
  const differs = quantity !== undefined && quantity !== item.quantity;
  const done = isPlacementHandled(item.placementStatus);
  const noTarget = !item.targetSectionId;

  const confirm = useMutation({
    mutationFn: (v: FormValues) => confirmPlacement(taskId, item.itemId, item.targetSectionId!, v.quantity, v.comment?.trim()),
    onSuccess: () => {
      setError(null);
      message.success('Размещение подтверждено');
      return queryClient.invalidateQueries({ queryKey: ['placement'] });
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
            {item.targetSectionCode ?? 'Секция не назначена'}
          </Typography.Text>
          <Tag color={item.placementStatus === 'DISCREPANCY' ? 'orange' : item.placementStatus === 'REJECTED' ? 'red' : done ? 'green' : 'default'}>
            {placementItemLabel(item.placementStatus)}
          </Tag>
        </Space>
      }
    >
      <Typography.Paragraph style={{ marginBottom: 4, fontSize: 16 }}>
        {item.productSku} — {item.productName}
      </Typography.Paragraph>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        Разместить:{' '}
        <Typography.Text strong>
          {fmtQty(item.quantity)} {PCS}
        </Typography.Text>
      </Typography.Paragraph>

      {done ? (
        <>
          {item.placementStatus === 'REJECTED' ? (
            <Typography.Text>Позиция отклонена кладовщиком, размещать её не нужно</Typography.Text>
          ) : (
            <Typography.Text>
              Размещено: {fmtQty(item.placedQuantity)} {PCS} в секции {item.placedSectionCode ?? '—'}
            </Typography.Text>
          )}
          {item.placementStatus === 'DISCREPANCY' && (
            <div style={{ color: '#d46b08' }}>Расхождение с планом: решение примет кладовщик</div>
          )}
          {item.discrepancyComment && <div style={{ color: '#888' }}>Комментарий: {item.discrepancyComment}</div>}
        </>
      ) : editable ? (
        <>
          <ErrorAlert error={error} style={{ marginBottom: 12 }} />
          {noTarget ? (
            <Alert type="warning" showIcon message="Кладовщик ещё не назначил секцию для этой позиции" />
          ) : (
            <Form
              form={form}
              layout="vertical"
              requiredMark={false}
              initialValues={{ quantity: item.quantity }}
              onFinish={(v) => confirm.mutate(v)}
            >
              <Form.Item
                name="quantity"
                label="Количество"
                rules={[
                  { required: true, type: 'number', message: 'Укажите количество' },
                  { type: 'integer', message: 'Только целое количество штук' },
                ]}
              >
                <InputNumber size="large" inputMode="numeric" min={1} precision={0} style={{ width: '100%' }} addonAfter={PCS} />
              </Form.Item>
              {differs && (
                <>
                  <Alert
                    type="warning"
                    showIcon
                    style={{ marginBottom: 12 }}
                    message="Количество отличается от плана"
                    description="Опишите, почему количество другое: расхождение разберёт кладовщик."
                  />
                  <Form.Item name="comment" label="Комментарий" rules={[{ max: 1000, message: 'Не длиннее 1000 символов' }]}>
                    <Input.TextArea rows={2} maxLength={1000} showCount />
                  </Form.Item>
                </>
              )}
              <Button type="primary" size="large" block htmlType="submit" loading={confirm.isPending}>
                Подтвердить размещение
              </Button>
            </Form>
          )}
        </>
      ) : (
        <Typography.Text type="secondary">{awaitingAccept ? 'Сначала примите задание' : 'Задание завершено'}</Typography.Text>
      )}
    </Card>
  );
}
