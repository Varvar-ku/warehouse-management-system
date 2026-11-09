import { useEffect, useState } from 'react';
import { Alert, Col, Form, Input, InputNumber, Modal, Row, Select, Typography, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { applyFieldErrors } from '../../../shared/formErrors';
import { useProductTypes } from '../catalog/hooks';
import WarehouseSelect from '../../../shared/WarehouseSelect';
import { createSection, updateSection } from './api';
import type { Section, SectionConstraints, SectionStatus } from './types';

type Num = number | null | undefined;

interface Values {
  warehouseId: string;
  code: string;
  name: string;
  sectionType?: string;
  zone?: string;
  rack?: string;
  priority?: number;
  status?: SectionStatus;
  lengthMin?: Num;
  lengthMax?: Num;
  widthMin?: Num;
  widthMax?: Num;
  heightMin?: Num;
  heightMax?: Num;
  maxCapacity?: Num;
  allowedProductTypes?: string[];
  allowedMaterials?: string[];
}

/** Типы хранения. В БД это свободная строка, поэтому значения совпадают с названиями. */
export const SECTION_TYPES = ['Стеллаж', 'Полка', 'Ячейка', 'Пол', 'Паллетное место'];

/** Вместимость секции всегда в штуках: единица не выбирается. */
const CAPACITY_UNIT = 'PCS';

const STATUS_OPTIONS: { value: SectionStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Активна' },
  { value: 'BLOCKED', label: 'Заблокирована' },
  { value: 'ARCHIVED', label: 'В архиве' },
];

const code = (v: string) => v.trim();
const toConstraints = (v: Values): Partial<SectionConstraints> => ({
  lengthMin: v.lengthMin ?? null,
  lengthMax: v.lengthMax ?? null,
  widthMin: v.widthMin ?? null,
  widthMax: v.widthMax ?? null,
  heightMin: v.heightMin ?? null,
  heightMax: v.heightMax ?? null,
  maxCapacity: v.maxCapacity ?? null,
  capacityUnitCode: v.maxCapacity !== null && v.maxCapacity !== undefined ? CAPACITY_UNIT : null,
  allowedProductTypes: v.allowedProductTypes ?? [],
  allowedMaterials: (v.allowedMaterials ?? []).map((m) => m.trim()).filter(Boolean),
});

interface Props {
  target: Section | 'new' | null;
  /** Склад по умолчанию для новой секции (текущий фильтр списка). */
  defaultWarehouseId?: string;
  onClose: () => void;
}

/** Создание и правка секции с ограничениями. Для зоны «Нераспределено» ограничения и статус недоступны. */
export default function SectionModal({ target, defaultWarehouseId, onClose }: Props) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const types = useProductTypes();
  const [error, setError] = useState<unknown>(null);
  const editing = target !== 'new' && target !== null ? target : null;
  const protectedZone = !!editing?.unassignedZone;
  const status: SectionStatus | undefined = Form.useWatch('status', form);

  useEffect(() => {
    if (!target) return;
    setError(null);
    form.resetFields();
    if (target === 'new') {
      form.setFieldsValue({ warehouseId: defaultWarehouseId, priority: 100 });
      return;
    }
    const c = target.constraints;
    form.setFieldsValue({
      warehouseId: target.warehouseId,
      code: target.code,
      name: target.name,
      sectionType: target.sectionType ?? undefined,
      zone: target.zone ?? undefined,
      rack: target.rack ?? undefined,
      priority: target.priority,
      status: target.status,
      lengthMin: c?.lengthMin,
      lengthMax: c?.lengthMax,
      widthMin: c?.widthMin,
      widthMax: c?.widthMax,
      heightMin: c?.heightMin,
      heightMax: c?.heightMax,
      maxCapacity: c?.maxCapacity,
      allowedProductTypes: c?.allowedProductTypes ?? [],
      allowedMaterials: c?.allowedMaterials ?? [],
    });
  }, [target, defaultWarehouseId, form]);

  const save = useMutation({
    mutationFn: (v: Values) => {
      const common = {
        name: v.name.trim(),
        sectionType: v.sectionType?.trim() || undefined,
        zone: v.zone?.trim() || undefined,
        rack: v.rack?.trim() || undefined,
        priority: v.priority ?? undefined,
      };
      if (!editing) return createSection({ ...common, warehouseId: v.warehouseId, code: code(v.code), constraints: toConstraints(v) });
      return updateSection(editing.id, {
        ...common,
        // у служебной зоны ограничения и статус защищены от изменений
        ...(protectedZone ? {} : { status: v.status, constraints: toConstraints(v) }),
      });
    },
    onSuccess: () => {
      message.success(editing ? 'Секция сохранена' : 'Секция создана');
      void queryClient.invalidateQueries({ queryKey: ['admin', 'sections'] });
      void queryClient.invalidateQueries({ queryKey: ['sections'] });
      onClose();
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });

  const num = (name: keyof Values, label: string) => (
    <Col xs={12} md={8}>
      <Form.Item name={name} label={label}>
        <InputNumber min={0} style={{ width: '100%' }} disabled={protectedZone} />
      </Form.Item>
    </Col>
  );

  return (
    <Modal
      open={!!target}
      title={editing ? `Секция ${editing.code}` : 'Новая секция'}
      okText="Сохранить"
      cancelText="Отмена"
      width={760}
      confirmLoading={save.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => save.mutate(v))}
      forceRender
    >
      <ErrorAlert error={error} style={{ marginBottom: 12 }} />
      {protectedZone && (
        <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Служебная зона «Нераспределено»: ограничения и статус менять нельзя" />
      )}
      <Form form={form} layout="vertical" requiredMark={false}>
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item name="warehouseId" label="Склад" rules={[{ required: true, message: 'Выберите склад' }]}>
              <WarehouseSelect disabled={!!editing} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="code"
              label="Код"
              extra={editing ? 'Код не меняется' : 'Буквы одного алфавита, цифры, . _ - (до 50), например MA-2'}
              rules={[
                { required: true, whitespace: true, message: 'Укажите код' },
                { pattern: /^[\p{L}\p{N}._-]{1,50}$/u, message: '1–50 символов: буквы, цифры, . _ -' },
              ]}
            >
              <Input disabled={!!editing} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="name" label="Название" rules={[{ required: true, whitespace: true, message: 'Укажите название' }, { max: 255 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item name="priority" label="Приоритет" extra="Меньше значит выше">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item name="sectionType" label="Тип секции">
              <Select
                allowClear
                placeholder="Выберите тип"
                options={[...new Set([...SECTION_TYPES, ...(editing?.sectionType ? [editing.sectionType] : [])])].map((t) => ({ value: t, label: t }))}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={12}>
            <Form.Item name="zone" label="Зона" rules={[{ max: 100 }]}>
              <Input />
            </Form.Item>
          </Col>
          <Col xs={12} md={12}>
            <Form.Item name="rack" label="Стеллаж" rules={[{ max: 100 }]}>
              <Input />
            </Form.Item>
          </Col>
          {editing && !protectedZone && (
            <Col xs={24}>
              <Form.Item name="status" label="Статус">
                <Select options={STATUS_OPTIONS} />
              </Form.Item>
              {status && status !== 'ACTIVE' && status !== editing.status && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="Блокировка и архивирование"
                  description="Бэкенд откажет, если в секции есть остаток (SECTION_NOT_EMPTY) или она указана в правиле размещения (SECTION_USED_BY_RULE). Сначала переместите товар или поправьте правило."
                />
              )}
            </Col>
          )}
        </Row>

        <Typography.Title level={5}>Ограничения (пусто означает «любой»)</Typography.Title>
        <Row gutter={12}>
          {num('lengthMin', 'Длина от, мм')}
          {num('lengthMax', 'Длина до, мм')}
          <Col xs={0} md={8} />
          {num('widthMin', 'Ширина от, мм')}
          {num('widthMax', 'Ширина до, мм')}
          <Col xs={0} md={8} />
          {num('heightMin', 'Высота от, мм')}
          {num('heightMax', 'Высота до, мм')}
          <Col xs={0} md={8} />
          <Col xs={12} md={8}>
            <Form.Item name="maxCapacity" label="Вместимость">
              <InputNumber min={1} precision={0} style={{ width: '100%' }} disabled={protectedZone} addonAfter="шт" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="allowedProductTypes" label="Допустимые типы товаров">
              <Select
                mode="multiple"
                disabled={protectedZone}
                optionFilterProp="label"
                placeholder="Любые"
                options={(types.data ?? []).map((t) => ({ value: t.code, label: `${t.name} (${t.code})` }))}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="allowedMaterials" label="Допустимые материалы" extra="Значения должны совпадать с материалом в характеристиках товара, например METAL">
              <Select mode="tags" disabled={protectedZone} tokenSeparators={[',']} placeholder="Любые" open={false} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
}
