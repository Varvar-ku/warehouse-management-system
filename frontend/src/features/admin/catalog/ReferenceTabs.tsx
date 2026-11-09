import { useEffect, useState, type ReactNode } from 'react';
import { Button, Form, Input, Modal, Select, Space, Switch, Table, Tag, message, type TableColumnsType } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { applyFieldErrors } from '../../../shared/formErrors';
import { createAttributeDefinition, createProductType, createUnit, updateProductType } from './api';
import { useAttributeDefinitions, useProductTypes, useUnits } from './hooks';
import type { AttributeDataType, AttributeDefinition, ProductType, Unit } from './types';

const DATA_TYPE_LABEL: Record<AttributeDataType, string> = {
  STRING: 'Строка',
  NUMBER: 'Число',
  BOOLEAN: 'Да/нет',
  REFERENCE: 'Выбор из списка',
};

/** Модальная форма «создать или изменить» с общей обработкой ошибок. */
function FormModal<V extends object>({
  open,
  title,
  onClose,
  onSubmit,
  initial,
  children,
  invalidate,
  successText,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: (v: V) => Promise<unknown>;
  initial?: Partial<V>;
  children: ReactNode;
  invalidate: string;
  successText: string;
}) {
  const [form] = Form.useForm<V>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<unknown>(null);
  // при каждом открытии форма заполняется заново: начальными значениями или данными редактируемой записи
  useEffect(() => {
    if (!open) return;
    setError(null);
    form.resetFields();
    if (initial) form.setFieldsValue(initial as never);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const save = useMutation({
    mutationFn: onSubmit,
    onSuccess: () => {
      message.success(successText);
      void queryClient.invalidateQueries({ queryKey: ['catalog', invalidate] });
      onClose();
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });
  return (
    <Modal
      open={open}
      title={title}
      okText="Сохранить"
      cancelText="Отмена"
      confirmLoading={save.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => save.mutate(v))}
      forceRender
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      <Form form={form} layout="vertical" requiredMark={false}>
        {children}
      </Form>
    </Modal>
  );
}

const codeRules = (max: number) => [{ required: true, whitespace: true, message: 'Укажите код' }, { max, message: `Не длиннее ${max} символов` }];

export function TypesTab() {
  const types = useProductTypes();
  const [target, setTarget] = useState<ProductType | 'new' | null>(null);
  const editing = target !== 'new' ? target : null;

  const columns: TableColumnsType<ProductType> = [
    { title: 'Код', dataIndex: 'code' },
    { title: 'Название', dataIndex: 'name' },
    { title: 'Описание', dataIndex: 'description', render: (d: string | null) => d || '—' },
    { title: 'Состояние', dataIndex: 'active', render: (a: boolean) => (a ? <Tag color="success">Активен</Tag> : <Tag>Отключён</Tag>) },
    { title: '', render: (_, t) => <Button size="small" onClick={() => setTarget(t)}>Изменить</Button> },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setTarget('new')}>
          Новый тип
        </Button>
      </Space>
      {types.isError && <ErrorAlert error={types.error} onRetry={() => types.refetch()} style={{ marginBottom: 16 }} />}
      <Table<ProductType> rowKey="id" size="middle" columns={columns} dataSource={types.data} loading={types.isFetching} pagination={false} />
      <FormModal<{ code: string; name: string; description?: string; active?: boolean }>
        open={!!target}
        title={editing ? `Тип ${editing.code}` : 'Новый тип товара'}
        invalidate="types"
        successText="Тип сохранён"
        onClose={() => setTarget(null)}
        initial={editing ? { code: editing.code, name: editing.name, description: editing.description ?? undefined, active: editing.active } : { active: true }}
        onSubmit={(v) =>
          editing
            ? updateProductType(editing.id, { name: v.name.trim(), description: v.description?.trim() ?? '', active: v.active })
            : createProductType({ code: v.code.trim(), name: v.name.trim(), description: v.description?.trim() || undefined })
        }
      >
        <Form.Item name="code" label="Код" extra={editing ? 'Код после создания не меняется' : 'Например ARMATURE'} rules={codeRules(50)}>
          <Input disabled={!!editing} />
        </Form.Item>
        <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 150 }]}>
          <Input />
        </Form.Item>
        <Form.Item name="description" label="Описание">
          <Input.TextArea rows={2} />
        </Form.Item>
        {editing && (
          <Form.Item name="active" label="Активен" valuePropName="checked" extra="Отключённый тип нельзя выбрать для новых товаров и правил">
            <Switch />
          </Form.Item>
        )}
      </FormModal>
    </>
  );
}

export function UnitsTab() {
  const units = useUnits();
  const [open, setOpen] = useState(false);

  const columns: TableColumnsType<Unit> = [
    { title: 'Код', dataIndex: 'code' },
    { title: 'Название', dataIndex: 'name' },
    { title: 'Дробные количества', dataIndex: 'allowFractions', render: (a: boolean) => (a ? 'Да' : 'Только целые') },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setOpen(true)}>
          Новая единица
        </Button>
      </Space>
      <ErrorAlert error={units.isError ? units.error : null} onRetry={() => units.refetch()} style={{ marginBottom: 16 }} />
      <Table<Unit> rowKey="id" size="middle" columns={columns} dataSource={units.data} loading={units.isFetching} pagination={false} />
      <FormModal<{ code: string; name: string; allowFractions: boolean }>
        open={open}
        title="Новая единица измерения"
        invalidate="units"
        successText="Единица создана"
        onClose={() => setOpen(false)}
        initial={{ allowFractions: true }}
        onSubmit={(v) => createUnit({ code: v.code.trim(), name: v.name.trim(), allowFractions: v.allowFractions })}
      >
        <Form.Item name="code" label="Код" extra="Единицы нельзя изменить или удалить после создания" rules={codeRules(20)}>
          <Input />
        </Form.Item>
        <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 50 }]}>
          <Input />
        </Form.Item>
        <Form.Item name="allowFractions" label="Дробные количества" valuePropName="checked">
          <Switch />
        </Form.Item>
      </FormModal>
    </>
  );
}

export function AttributesTab() {
  const defs = useAttributeDefinitions();
  const [open, setOpen] = useState(false);

  const columns: TableColumnsType<AttributeDefinition> = [
    { title: 'Код', dataIndex: 'code' },
    { title: 'Название', dataIndex: 'name' },
    { title: 'Тип', dataIndex: 'dataType', render: (t: AttributeDataType) => DATA_TYPE_LABEL[t] ?? t },
    {
      title: 'Варианты',
      dataIndex: 'options',
      render: (o: string[] | null) =>
        o && o.length > 0 ? (
          <Space size={4} wrap>
            {o.map((x) => (
              <Tag key={x}>{x}</Tag>
            ))}
          </Space>
        ) : (
          '—'
        ),
    },
    { title: 'Состояние', dataIndex: 'active', render: (a: boolean) => (a ? <Tag color="success">Активен</Tag> : <Tag>Отключён</Tag>) },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setOpen(true)}>
          Новый атрибут
        </Button>
      </Space>
      <ErrorAlert error={defs.isError ? defs.error : null} onRetry={() => defs.refetch()} style={{ marginBottom: 16 }} />
      <Table<AttributeDefinition> rowKey="id" size="middle" columns={columns} dataSource={defs.data} loading={defs.isFetching} pagination={false} />
      <FormModal<{ code: string; name: string; dataType: AttributeDataType; options?: string[] }>
        open={open}
        title="Новый атрибут"
        invalidate="attributes"
        successText="Атрибут создан"
        onClose={() => setOpen(false)}
        initial={{ dataType: 'STRING' }}
        onSubmit={(v) =>
          createAttributeDefinition({
            code: v.code.trim(),
            name: v.name.trim(),
            dataType: v.dataType,
            options: v.dataType === 'REFERENCE' ? (v.options ?? []).map((o) => o.trim()).filter(Boolean) : undefined,
          })
        }
      >
        <Form.Item name="code" label="Код" extra="Например LENGTH. Атрибуты нельзя изменить после создания" rules={codeRules(100)}>
          <Input />
        </Form.Item>
        <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 150 }]}>
          <Input />
        </Form.Item>
        <Form.Item name="dataType" label="Тип значения" rules={[{ required: true }]}>
          <Select options={(Object.keys(DATA_TYPE_LABEL) as AttributeDataType[]).map((t) => ({ value: t, label: DATA_TYPE_LABEL[t] }))} />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(a, b) => a.dataType !== b.dataType}>
          {({ getFieldValue }) =>
            getFieldValue('dataType') === 'REFERENCE' ? (
              <Form.Item
                name="options"
                label="Допустимые значения"
                rules={[{ required: true, type: 'array', min: 1, message: 'Укажите хотя бы одно значение' }]}
              >
                <Select mode="tags" tokenSeparators={[',']} placeholder="Введите значение и нажмите Enter" open={false} />
              </Form.Item>
            ) : null
          }
        </Form.Item>
      </FormModal>
    </>
  );
}
