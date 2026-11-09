import { useState } from 'react';
import { Checkbox, Form, Modal, Select, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { useRefreshAll } from '../../../shared/useRefreshAll';
import { useWorkers } from '../../directory/hooks';
import { addWorkers } from '../api';
import type { PickingTask } from '../types';

interface Props {
  task: PickingTask;
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  workers: string[];
  autoAssign: boolean;
}

export default function AddWorkersModal({ task, open, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const refresh = useRefreshAll();
  const workers = useWorkers(open);
  const [error, setError] = useState<unknown>(null);

  const already = new Set(task.workers.map((w) => w.workerId));
  const options = (workers.data ?? [])
    .filter((w) => !already.has(w.id))
    .map((w) => ({ value: w.id, label: `${w.fullName} (${w.login})` }));

  const add = useMutation({
    mutationFn: (v: FormValues) =>
      addWorkers(task.id, {
        workers: v.workers.map((workerId) => ({ workerId })),
        autoAssignRemaining: !!v.autoAssign,
      }),
    onSuccess: () => {
      message.success('Работники добавлены');
      onClose();
      return refresh();
    },
    onError: setError,
  });

  return (
    <Modal
      title="Добавить работников"
      open={open}
      okText="Добавить"
      cancelText="Назад"
      okButtonProps={{ loading: add.isPending }}
      onOk={() => {
        setError(null);
        form
          .validateFields()
          .then((v) => add.mutate(v))
          .catch(() => undefined);
      }}
      onCancel={onClose}
      afterClose={() => {
        form.resetFields();
        setError(null);
      }}
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ workers: [], autoAssign: false }}>
        <Form.Item
          name="workers"
          label="Работники"
          rules={[{ required: true, type: 'array', min: 1, message: 'Выберите хотя бы одного работника' }]}
        >
          <Select
            mode="multiple"
            placeholder="Выберите работников"
            loading={workers.isLoading}
            optionFilterProp="label"
            options={options}
            notFoundContent="Нет свободных работников"
          />
        </Form.Item>
        <Form.Item name="autoAssign" valuePropName="checked" style={{ marginBottom: 0 }}>
          <Checkbox>Назначить нераспределённые строки (если в задании окажется один работник)</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
}
