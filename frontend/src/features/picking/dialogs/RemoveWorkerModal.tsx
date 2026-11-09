import { useState } from 'react';
import { Form, Input, Modal, Select, Typography, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { useRefreshAll } from '../../../shared/useRefreshAll';
import { nameOf, useWorkers } from '../../directory/hooks';
import { removeWorker } from '../api';
import type { PickingTask, PickingWorker } from '../types';

interface Props {
  task: PickingTask;
  worker: PickingWorker | null;
  names: Map<string, string>;
  onClose: () => void;
}

interface FormValues {
  replaceWith?: string;
  reason: string;
}

/** Замена работника (с переносом его строк) или снятие. Причина обязательна, она попадает в журнал переназначений. */
export default function RemoveWorkerModal({ task, worker, names, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const refresh = useRefreshAll();
  const open = !!worker;
  const workers = useWorkers(open);
  const [error, setError] = useState<unknown>(null);

  const remove = useMutation({
    mutationFn: (v: FormValues) => removeWorker(task.id, worker!.workerId, v.reason.trim(), v.replaceWith),
    onSuccess: (_, v) => {
      message.success(v.replaceWith ? 'Работник заменён' : 'Работник снят с задания');
      onClose();
      return refresh();
    },
    onError: setError,
  });

  const options = (workers.data ?? [])
    .filter((w) => w.id !== worker?.workerId)
    .map((w) => ({ value: w.id, label: `${w.fullName} (${w.login})` }));

  return (
    <Modal
      title={`Заменить или снять: ${nameOf(names, worker?.workerId)}`}
      open={open}
      okText="Применить"
      cancelText="Назад"
      okButtonProps={{ loading: remove.isPending }}
      onOk={() => {
        setError(null);
        form
          .validateFields()
          .then((v) => remove.mutate(v))
          .catch(() => undefined);
      }}
      onCancel={onClose}
      afterClose={() => {
        form.resetFields();
        setError(null);
      }}
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      <Form form={form} layout="vertical" requiredMark={false}>
        <Form.Item name="replaceWith" label="Кем заменить">
          <Select
            allowClear
            placeholder="Не выбрано: строки останутся нераспределёнными"
            loading={workers.isLoading}
            optionFilterProp="label"
            options={options}
          />
        </Form.Item>
        <Form.Item
          name="reason"
          label="Причина"
          rules={[
            { required: true, whitespace: true, message: 'Укажите причину' },
            { max: 1000, message: 'Не длиннее 1000 символов' },
          ]}
        >
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
        <Typography.Text type="secondary">
          Если заменяющий не выбран, работник снимается с задания без замены.
        </Typography.Text>
      </Form>
    </Modal>
  );
}
