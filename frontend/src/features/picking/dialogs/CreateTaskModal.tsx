import { useState } from 'react';
import { Checkbox, Form, Modal, Select, Typography, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { useRefreshAll } from '../../../shared/useRefreshAll';
import { useWorkers } from '../../directory/hooks';
import type { RequestResponse } from '../../requests/types';
import { createPickingTask } from '../api';

interface Props {
  request: RequestResponse;
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  workers: string[];
  autoAssign: boolean;
}

/** Создание задания комплектации по резервам заявки. */
export default function CreateTaskModal({ request, open, onClose }: Props) {
  const [form] = Form.useForm<FormValues>();
  const refresh = useRefreshAll();
  const workers = useWorkers(open);
  const [error, setError] = useState<unknown>(null);

  const create = useMutation({
    mutationFn: (v: FormValues) =>
      createPickingTask({
        requestId: request.id,
        workers: (v.workers ?? []).map((workerId) => ({ workerId })),
        autoAssignRemaining: !!v.autoAssign,
      }),
    onSuccess: () => {
      message.success('Задание создано');
      onClose();
      return refresh();
    },
    onError: setError,
  });

  return (
    <Modal
      title={`Задание комплектации: заявка ${request.number}`}
      open={open}
      okText="Создать задание"
      cancelText="Назад"
      okButtonProps={{ loading: create.isPending }}
      onOk={() => {
        setError(null);
        form
          .validateFields()
          .then((v) => create.mutate(v))
          .catch(() => undefined);
      }}
      onCancel={onClose}
      afterClose={() => {
        form.resetFields();
        setError(null);
      }}
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ workers: [], autoAssign: true }}>
        <Form.Item name="workers" label="Работники">
          <Select
            mode="multiple"
            allowClear
            placeholder="Выберите работников"
            loading={workers.isLoading}
            optionFilterProp="label"
            options={(workers.data ?? []).map((w) => ({ value: w.id, label: `${w.fullName} (${w.login})` }))}
          />
        </Form.Item>
        <Form.Item name="autoAssign" valuePropName="checked" style={{ marginBottom: 4 }}>
          <Checkbox>Если работник один, сразу назначить ему все строки</Checkbox>
        </Form.Item>
        <Typography.Text type="secondary">
          Работников можно добавить и строки распределить позже, уже в самом задании.
        </Typography.Text>
      </Form>
    </Modal>
  );
}
