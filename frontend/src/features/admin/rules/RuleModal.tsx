import { useEffect, useState } from 'react';
import { Alert, Button, Col, Form, Input, InputNumber, Modal, Radio, Row, Select, Space, Switch, Typography, message } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { applyFieldErrors } from '../../../shared/formErrors';
import WarehouseSelect from '../../../shared/WarehouseSelect';
import { useProductTypes, useUnits } from '../catalog/hooks';
import SectionSelect from '../../tasks/components/SectionSelect';
import { createRule, updateRule } from './api';
import { fromConditions, toConditions, type NumericRow } from './conditions';
import { NUMERIC_ATTRIBUTES, type PlacementRule } from './types';

interface Values {
  warehouseId?: string;
  name: string;
  priority?: number;
  fallback?: boolean;
  active?: boolean;
  targetSectionId?: string;
  productTypeCodes?: string[];
  materials?: string[];
  numeric?: NumericRow[];
}

interface Props {
  target: PlacementRule | 'new' | null;
  defaultWarehouseId?: string;
  onClose: () => void;
}

/** Создание и правка правила размещения. Любое реальное изменение увеличивает версию правила. */
export default function RuleModal({ target, defaultWarehouseId, onClose }: Props) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const types = useProductTypes();
  const units = useUnits();
  const [error, setError] = useState<unknown>(null);
  const editing = target !== 'new' && target !== null ? target : null;
  const warehouseId: string | undefined = Form.useWatch('warehouseId', form);
  const fallback: boolean | undefined = Form.useWatch('fallback', form);

  useEffect(() => {
    if (!target) return;
    setError(null);
    form.resetFields();
    if (target === 'new') {
      form.setFieldsValue({ warehouseId: defaultWarehouseId, priority: 100, fallback: false });
      return;
    }
    form.setFieldsValue({
      warehouseId: target.warehouseId,
      name: target.name,
      priority: target.priority,
      fallback: target.fallback,
      active: target.active,
      targetSectionId: target.targetSectionId,
      productTypeCodes: target.conditions.productTypeCodes ?? [],
      materials: target.conditions.materials ?? [],
      numeric: fromConditions(target.conditions),
    });
  }, [target, defaultWarehouseId, form]);

  const save = useMutation({
    mutationFn: (v: Values) => {
      const conditions = toConditions(v);
      return editing
        ? updateRule(editing.id, {
            name: v.name.trim(),
            priority: v.priority,
            active: v.active,
            fallback: v.fallback,
            conditions,
            targetSectionId: v.targetSectionId,
          })
        : createRule({
            name: v.name.trim(),
            priority: v.priority,
            fallback: v.fallback,
            conditions,
            targetSectionId: v.targetSectionId!,
          });
    },
    onSuccess: () => {
      message.success(editing ? 'Правило сохранено' : 'Правило создано');
      void queryClient.invalidateQueries({ queryKey: ['admin', 'rules'] });
      onClose();
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });

  const unitOptions = (units.data ?? []).map((u) => ({ value: u.code, label: u.code }));

  return (
    <Modal
      open={!!target}
      title={editing ? `Правило «${editing.name}»` : 'Новое правило размещения'}
      okText="Сохранить"
      cancelText="Отмена"
      width={760}
      confirmLoading={save.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => save.mutate(v))}
      forceRender
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      {editing && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={`Текущая версия: ${editing.ruleVersion}. Сохранение с изменениями создаст новую версию; ранее принятые приёмки сохранят решение по прежней версии.`}
        />
      )}
      <Form form={form} layout="vertical" requiredMark={false}>
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item name="warehouseId" label="Склад" extra="Нужен только для выбора секции: склад правила определяется целевой секцией">
              <WarehouseSelect disabled={!!editing} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="targetSectionId" label="Целевая секция" rules={[{ required: true, message: 'Выберите секцию' }]}>
              <SectionSelect warehouseId={warehouseId ?? ''} disabled={!warehouseId} target={editing ? { id: editing.targetSectionId, code: editing.targetSectionCode } : undefined} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 255 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item name="priority" label="Приоритет" extra="Меньше значит раньше">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item name="fallback" label="Резервное" valuePropName="checked" extra="Если подошедших правил нет">
              <Switch />
            </Form.Item>
          </Col>
          {editing && (
            <Col xs={24}>
              <Form.Item name="active" label="Активно" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          )}
        </Row>

        <Typography.Title level={5}>Условия (все заданные должны выполняться)</Typography.Title>
        {fallback && (
          <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Для резервного правила условия обычно не нужны: оно ловит всё, что не подошло остальным." />
        )}
        <Form.Item name="productTypeCodes" label="Типы товаров">
          <Select
            mode="multiple"
            optionFilterProp="label"
            placeholder="Любые"
            options={(types.data ?? []).filter((t) => t.active).map((t) => ({ value: t.code, label: `${t.name} (${t.code})` }))}
          />
        </Form.Item>
        <Form.Item name="materials" label="Материалы" extra="Регистр не важен: METAL, WOOD, PLASTIC">
          <Select mode="tags" tokenSeparators={[',']} placeholder="Любые" open={false} />
        </Form.Item>

        <Form.List name="numeric">
          {(fields, { add, remove }) => (
            <>
              {fields.map(({ key, name, ...rest }) => (
                <Space key={key} align="start" wrap style={{ display: 'flex', marginBottom: 4 }}>
                  <Form.Item {...rest} name={[name, 'attr']} rules={[{ required: true, message: 'Атрибут' }]}>
                    <Select placeholder="Атрибут" style={{ width: 130 }} options={NUMERIC_ATTRIBUTES} />
                  </Form.Item>
                  <Form.Item {...rest} name={[name, 'mode']} initialValue="eq">
                    <Radio.Group
                      optionType="button"
                      options={[
                        { value: 'eq', label: 'Равно' },
                        { value: 'range', label: 'Диапазон' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item noStyle shouldUpdate={(a, b) => a.numeric?.[name]?.mode !== b.numeric?.[name]?.mode}>
                    {({ getFieldValue }) =>
                      getFieldValue(['numeric', name, 'mode']) === 'range' ? (
                        <Space align="start">
                          <Form.Item
                            {...rest}
                            name={[name, 'min']}
                            dependencies={[['numeric', name, 'max']]}
                            rules={[
                              ({ getFieldValue: g }) => ({
                                validator: (_, v: number | null) => {
                                  const max = g(['numeric', name, 'max']) as number | null | undefined;
                                  if ((v === null || v === undefined) && (max === null || max === undefined)) {
                                    return Promise.reject(new Error('Укажите границу'));
                                  }
                                  if (v !== null && v !== undefined && max !== null && max !== undefined && v > max) {
                                    return Promise.reject(new Error('От больше До'));
                                  }
                                  return Promise.resolve();
                                },
                              }),
                            ]}
                          >
                            <InputNumber placeholder="от" style={{ width: 100 }} />
                          </Form.Item>
                          <Form.Item {...rest} name={[name, 'max']}>
                            <InputNumber placeholder="до" style={{ width: 100 }} />
                          </Form.Item>
                        </Space>
                      ) : (
                        <Form.Item {...rest} name={[name, 'eq']} rules={[{ required: true, message: 'Значение' }]}>
                          <InputNumber placeholder="значение" style={{ width: 120 }} />
                        </Form.Item>
                      )
                    }
                  </Form.Item>
                  <Form.Item {...rest} name={[name, 'unit']}>
                    <Select allowClear placeholder="Ед. (по умолч.)" style={{ width: 130 }} options={unitOptions} />
                  </Form.Item>
                  <Button type="text" danger icon={<MinusCircleOutlined />} onClick={() => remove(name)} aria-label="Убрать условие" />
                </Space>
              ))}
              <Form.Item
                shouldUpdate
                rules={[
                  {
                    validator: () => {
                      const attrs = ((form.getFieldValue('numeric') as NumericRow[] | undefined) ?? []).map((r) => r?.attr).filter(Boolean);
                      return new Set(attrs).size === attrs.length ? Promise.resolve() : Promise.reject(new Error('Атрибут указан дважды'));
                    },
                  },
                ]}
              >
                <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ mode: 'eq' })} disabled={fields.length >= NUMERIC_ATTRIBUTES.length}>
                  Условие по размеру
                </Button>
              </Form.Item>
            </>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}
