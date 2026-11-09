import { useState } from 'react';
import { Alert, Checkbox, Form, Input, Modal, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { fmtQty } from '../../../shared/format';
import { round4 } from '../../../shared/math';
import { useRefreshAll } from '../../../shared/useRefreshAll';
import { completeTask } from '../api';
import type { PickingTask } from '../types';

interface Props {
  task: PickingTask;
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  confirmShortage: boolean;
  comment?: string;
}

/** Явное завершение задания. При недоборе нужны подтверждение и комментарий. */
export default function CompleteTaskModal({ task, open, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const refresh = useRefreshAll();
  const [error, setError] = useState<unknown>(null);

  const total = task.progress?.totalQuantity ?? 0;
  const picked = task.progress?.pickedQuantity ?? 0;
  const shortage = round4(Math.max(0, total - picked));
  const hasShortage = shortage > 0;

  const complete = useMutation({
    mutationFn: (v: FormValues) =>
      completeTask(task.id, { confirmShortage: hasShortage && !!v.confirmShortage, comment: v.comment?.trim() || undefined }),
    onSuccess: () => {
      message.success('Задание завершено');
      onClose();
      return refresh();
    },
    onError: setError,
  });

  return (
    <Modal
      title={`Завершить задание ${task.number}`}
      open={open}
      okText="Завершить"
      cancelText="Назад"
      okButtonProps={{ loading: complete.isPending }}
      onOk={() => {
        setError(null);
        form
          .validateFields()
          .then((v) => complete.mutate(v))
          .catch(() => undefined);
      }}
      onCancel={onClose}
      afterClose={() => {
        form.resetFields();
        setError(null);
      }}
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      {hasShortage ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`Собрано ${fmtQty(picked)} из ${fmtQty(total)}. Недобор: ${fmtQty(shortage)}`}
          description="Завершение с недобором нужно подтвердить и пояснить."
        />
      ) : (
        <Alert type="success" showIcon style={{ marginBottom: 12 }} message="Все позиции собраны полностью" />
      )}
      <Form form={form} layout="vertical" requiredMark={false}>
        {hasShortage && (
          <Form.Item
            name="confirmShortage"
            valuePropName="checked"
            rules={[
              {
                validator: (_, v) => (v ? Promise.resolve() : Promise.reject(new Error('Подтвердите завершение с недобором'))),
              },
            ]}
          >
            <Checkbox>Подтверждаю завершение с недобором</Checkbox>
          </Form.Item>
        )}
        <Form.Item
          name="comment"
          label="Комментарий"
          rules={[
            ...(hasShortage ? [{ required: true, whitespace: true, message: 'Укажите причину недобора' }] : []),
            { max: 1000, message: 'Не длиннее 1000 символов' },
          ]}
        >
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
