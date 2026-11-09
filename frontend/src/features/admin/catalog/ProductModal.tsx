import { useEffect, useState } from 'react';
import { Alert, Form, Input, InputNumber, Modal, Select, Space, Switch, Typography, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { applyFieldErrors } from '../../../shared/formErrors';
import { createProduct, updateProduct } from './api';
import { useAttributeDefinitions, useProductTypes, useUnits } from './hooks';
import { buildAttributes, type AttrField } from './attributes';
import type { AttributeDefinition, CatalogProduct } from './types';

interface Values {
  sku: string;
  name: string;
  productTypeId: string;
  description?: string;
  attrs?: Record<string, AttrField>;
}

interface Props {
  /** 'new' для создания, товар для правки, null: закрыто. */
  target: CatalogProduct | 'new' | null;
  onClose: () => void;
}

/** Создание и правка товара. Форма атрибутов строится по определениям из справочника. */
export default function ProductModal({ target, onClose }: Props) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const types = useProductTypes();
  const units = useUnits();
  const defs = useAttributeDefinitions();
  const [error, setError] = useState<unknown>(null);
  const editing = target !== 'new' && target !== null ? target : null;
  const activeDefs = (defs.data ?? []).filter((d) => d.active);

  useEffect(() => {
    if (!target) return;
    setError(null);
    form.resetFields();
    if (target === 'new') return;
    const attrs: Record<string, AttrField> = {};
    for (const a of target.attributes) {
      const def = (defs.data ?? []).find((d) => d.code === a.attributeCode);
      const value = def?.dataType === 'NUMBER' ? a.valueNumber : def?.dataType === 'BOOLEAN' ? a.valueBoolean : a.valueString;
      attrs[a.attributeCode] = { value, unitId: a.valueUnitId ?? undefined };
    }
    form.setFieldsValue({
      sku: target.sku,
      name: target.name,
      productTypeId: target.productTypeId,
      description: target.description ?? undefined,
      attrs,
    });
    // defs подгружаются асинхронно: при их появлении значения атрибутов нужно разложить заново
  }, [target, defs.data, form]);

  const save = useMutation({
    mutationFn: (v: Values) => {
      const attributes = buildAttributes(activeDefs, v.attrs);
      return editing
        ? updateProduct(editing.id, {
            name: v.name.trim(),
            productTypeId: v.productTypeId,
            description: v.description?.trim() ?? '',
            attributes,
          })
        : createProduct({
            sku: v.sku.trim(),
            name: v.name.trim(),
            productTypeId: v.productTypeId,
            unitId: pieceUnit!.id,
            description: v.description?.trim() || undefined,
            attributes,
          });
    },
    onSuccess: () => {
      message.success(editing ? 'Товар сохранён' : 'Товар создан');
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
      void queryClient.invalidateQueries({ queryKey: ['products'] });
      onClose();
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });

  // Товар учитывается только в штуках: единицу выбирать нельзя, берётся «Штука» (PCS)
  const pieceUnit = (units.data ?? []).find((u) => u.code === 'PCS');
  const unitOptions = (units.data ?? []).map((u) => ({ value: u.id, label: `${u.name} (${u.code})` }));

  const renderAttr = (d: AttributeDefinition) => {
    const name = ['attrs', d.code, 'value'];
    switch (d.dataType) {
      case 'NUMBER':
        return (
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name={name} noStyle>
              <InputNumber style={{ width: '60%' }} precision={4} />
            </Form.Item>
            <Form.Item name={['attrs', d.code, 'unitId']} noStyle>
              <Select allowClear placeholder="Единица" style={{ width: '40%' }} options={unitOptions} />
            </Form.Item>
          </Space.Compact>
        );
      case 'BOOLEAN':
        return (
          <Form.Item name={name} noStyle>
            <Select
              allowClear
              placeholder="Не задано"
              options={[
                { value: true, label: 'Да' },
                { value: false, label: 'Нет' },
              ]}
            />
          </Form.Item>
        );
      case 'REFERENCE':
        return (
          <Form.Item name={name} noStyle>
            <Select allowClear placeholder="Не задано" options={(d.options ?? []).map((o) => ({ value: o, label: o }))} />
          </Form.Item>
        );
      default:
        return (
          <Form.Item name={name} noStyle>
            <Input maxLength={500} />
          </Form.Item>
        );
    }
  };

  return (
    <Modal
      open={!!target}
      title={editing ? `Товар ${editing.sku}` : 'Новый товар'}
      okText="Сохранить"
      cancelText="Отмена"
      width={640}
      confirmLoading={save.isPending}
      onCancel={onClose}
      okButtonProps={{ disabled: !editing && units.isSuccess && !pieceUnit }}
      onOk={() => form.validateFields().then((v) => save.mutate(v))}
      forceRender
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      {!editing && units.isSuccess && !pieceUnit && (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} message="В справочнике нет единицы «Штука» (код PCS): создайте её на вкладке «Единицы»" />
      )}
      <Form form={form} layout="vertical" requiredMark={false}>
        <Form.Item
          name="sku"
          label="Артикул (SKU)"
          extra={editing ? 'После создания артикул изменить нельзя' : undefined}
          rules={[{ required: true, whitespace: true, message: 'Укажите артикул' }, { max: 100 }]}
        >
          <Input disabled={!!editing} />
        </Form.Item>
        <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 255 }]}>
          <Input />
        </Form.Item>
        <Space style={{ width: '100%' }} size={12} align="start" wrap>
          <Form.Item name="productTypeId" label="Тип товара" rules={[{ required: true, message: 'Выберите тип' }]} style={{ minWidth: 260 }}>
            <Select
              showSearch
              optionFilterProp="label"
              loading={types.isLoading}
              options={(types.data ?? [])
                .filter((t) => t.active || t.id === editing?.productTypeId)
                .map((t) => ({ value: t.id, label: `${t.name} (${t.code})` }))}
            />
          </Form.Item>
        </Space>
        <Form.Item name="description" label="Описание">
          <Input.TextArea rows={2} />
        </Form.Item>
        {activeDefs.length > 0 && (
          <>
            <Typography.Title level={5}>Характеристики</Typography.Title>
            <Typography.Paragraph type="secondary">
              Длина, материал и другие атрибуты используются правилами размещения. Некорректная единица длины отправит товар в
              «Нераспределено», поэтому выбирайте единицу из списка.
            </Typography.Paragraph>
            {activeDefs.map((d) => (
              <Form.Item key={d.code} label={`${d.name} (${d.code})`}>
                {renderAttr(d)}
              </Form.Item>
            ))}
          </>
        )}
        {editing && (
          <Form.Item label="Состояние">
            <Switch checked={editing.active} disabled checkedChildren="Активен" unCheckedChildren="В архиве" />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}
