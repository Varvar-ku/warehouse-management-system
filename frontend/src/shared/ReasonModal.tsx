import type { ReactNode } from 'react';
import { Form, Input, Modal } from 'antd';

interface Props {
  open: boolean;
  title: string;
  label: string;
  okText: string;
  description?: ReactNode;
  danger?: boolean;
  /** true (по умолчанию): текст обязателен. false: можно отправить пустым. */
  required?: boolean;
  loading?: boolean;
  onSubmit: (text: string) => void;
  onCancel: () => void;
}

/** Диалог с одним текстовым полем: причина отказа или отмены, необязательный комментарий. */
export default function ReasonModal({
  open,
  title,
  label,
  okText,
  description,
  danger,
  required = true,
  loading,
  onSubmit,
  onCancel,
}: Props) {
  const [form] = Form.useForm<{ text?: string }>();
  return (
    <Modal
      title={title}
      open={open}
      okText={okText}
      cancelText="Назад"
      okButtonProps={{ danger, loading }}
      onOk={() =>
        form
          .validateFields()
          .then((v) => onSubmit((v.text ?? '').trim()))
          .catch(() => undefined)
      }
      onCancel={onCancel}
      afterClose={() => form.resetFields()}
    >
      {description && <div style={{ marginBottom: 12 }}>{description}</div>}
      <Form form={form} layout="vertical" requiredMark={false}>
        <Form.Item
          name="text"
          label={label}
          rules={[
            ...(required ? [{ required: true, whitespace: true, message: 'Заполните поле' }] : []),
            { max: 1000, message: 'Не длиннее 1000 символов' },
          ]}
        >
          <Input.TextArea rows={3} maxLength={1000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}
