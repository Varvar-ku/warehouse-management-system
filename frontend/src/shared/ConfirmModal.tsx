import type { ReactNode } from 'react';
import { Modal } from 'antd';

interface Props {
  open: boolean;
  title: string;
  description?: ReactNode;
  okText: string;
  danger?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Единое окно подтверждения действия. Вместо разнородных Popconfirm и отдельных Modal
 * все подтверждения выглядят одинаково: вопрос в заголовке, пояснение в тексте, кнопки «Назад» и действие.
 */
export default function ConfirmModal({ open, title, description, okText, danger, loading, onConfirm, onCancel }: Props) {
  return (
    <Modal
      title={title}
      open={open}
      okText={okText}
      cancelText="Назад"
      okButtonProps={{ danger, loading }}
      cancelButtonProps={{ disabled: loading }}
      onOk={onConfirm}
      onCancel={onCancel}
    >
      {description}
    </Modal>
  );
}
