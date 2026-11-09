import { useState } from 'react';
import { Alert, Form, Input, Modal, Radio, Typography, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtQty, PCS } from '../../shared/format';
import SectionSelect from '../tasks/components/SectionSelect';
import type { PlacementItem, PlacementTask } from '../tasks/api';
import { resolveDiscrepancy, type ResolveDecision } from './api';

interface Props {
  task: PlacementTask;
  item: PlacementItem | null;
  onClose: () => void;
}

interface FormValues {
  decision: ResolveDecision;
  newTargetSectionId?: string;
  comment: string;
}

/**
 * Решение кладовщика по позиции размещения. Расхождение: принять фактическое, вернуть на размещение или отклонить
 * позицию. Позицию, которую ещё не разместили (PENDING), можно только отклонить: товар повреждён или потерян.
 */
export default function ResolveModal({ task, item, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  const decision = Form.useWatch('decision', form);
  const pendingOnly = item?.placementStatus === 'PENDING';

  const resolve = useMutation({
    mutationFn: (v: FormValues) =>
      resolveDiscrepancy(task.id, item!.itemId, {
        decision: pendingOnly ? 'REJECT_LINE' : v.decision,
        newTargetSectionId: v.decision === 'RETURN_TO_PLACEMENT' ? v.newTargetSectionId : undefined,
        comment: v.comment.trim(),
      }),
    onSuccess: () => {
      message.success('Решение сохранено');
      onClose();
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['placement'] }),
        queryClient.invalidateQueries({ queryKey: ['receipts'] }),
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
      ]);
    },
    onError: setError,
  });

  return (
    <Modal
      title={pendingOnly ? 'Отклонить позицию' : 'Расхождение при размещении'}
      open={!!item}
      okText={pendingOnly ? 'Отклонить позицию' : 'Применить решение'}
      cancelText="Назад"
      okButtonProps={{ loading: resolve.isPending }}
      onOk={() => {
        setError(null);
        form
          .validateFields()
          .then((v) => resolve.mutate(v))
          .catch(() => undefined);
      }}
      onCancel={onClose}
      afterClose={() => {
        form.resetFields();
        setError(null);
      }}
    >
      {item && (
        <Typography.Paragraph>
          {item.productSku} — {item.productName}
          <br />
          План: {fmtQty(item.quantity)} {PCS}, секция {item.targetSectionCode ?? '—'}
          <br />
          {item.placedQuantity !== null && (
            <>
              Факт: {fmtQty(item.placedQuantity)} {PCS}, секция {item.placedSectionCode ?? '—'}
            </>
          )}
          {item.discrepancyComment && (
            <>
              <br />
              Комментарий работника: {item.discrepancyComment}
            </>
          )}
        </Typography.Paragraph>
      )}
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ decision: 'ACCEPT_ACTUAL' }}
      >
        {!pendingOnly && (
          <Form.Item name="decision" label="Решение">
            <Radio.Group>
              <Radio value="ACCEPT_ACTUAL">Принять фактическое</Radio>
              <Radio value="RETURN_TO_PLACEMENT">Вернуть на размещение</Radio>
              <Radio value="REJECT_LINE">Отклонить позицию</Radio>
            </Radio.Group>
          </Form.Item>
        )}
        {(pendingOnly || decision === 'REJECT_LINE') && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="Позиция будет закрыта с нулевым приходом"
            description="Остаток не изменится. Причину (брак, потеря, недостача) укажите в комментарии: она попадёт в журнал."
          />
        )}
        {decision === 'RETURN_TO_PLACEMENT' && (
          <Form.Item name="newTargetSectionId" label="Новая целевая секция (необязательно)">
            <SectionSelect placeableOnly warehouseId={task.warehouseId} />
          </Form.Item>
        )}
        <Form.Item
          name="comment"
          label="Комментарий"
          rules={[
            { required: true, whitespace: true, message: 'Укажите комментарий' },
            { max: 1000, message: 'Не длиннее 1000 символов' },
          ]}
        >
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
