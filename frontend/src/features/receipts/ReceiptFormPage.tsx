import { useState } from 'react';
import {
  Alert,
  AutoComplete,
  Button,
  Card,
  Col,
  Collapse,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Result,
  Row,
  Select,
  Space,
  Spin,
  message,
} from 'antd';
import type { LabeledValue } from 'antd/es/select';
import dayjs, { type Dayjs } from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { applyFieldErrors } from '../../shared/formErrors';
import ProductSelect from '../../shared/ProductSelect';
import WarehouseSelect from '../../shared/WarehouseSelect';
import { createReceipt, getReceipt, updateReceipt } from './api';
import type { Characteristics, Receipt, ReceiptItemInput } from './types';
import { PCS } from '../../shared/format';

/** Числовые характеристики, которые использует подбор секции (атрибуты каталога). */
const NUMERIC_ATTRS: { code: string; label: string }[] = [
  { code: 'LENGTH', label: 'Длина' },
  { code: 'WIDTH', label: 'Ширина' },
  { code: 'HEIGHT', label: 'Высота' },
  { code: 'DIAMETER', label: 'Диаметр' },
];
const MATERIAL_HINTS = ['METAL', 'WOOD', 'PLASTIC'].map((value) => ({ value }));

interface NumericField {
  value?: number | null;
  unit?: string;
}

interface ItemFormValue {
  product?: LabeledValue;
  quantity?: number;
  batchNumber?: string;
  expiryAt?: Dayjs | null;
  comment?: string;
  material?: string;
  num?: Record<string, NumericField>;
}

interface FormValues {
  warehouseId?: string;
  supplierName?: string;
  externalDocumentNumber?: string;

  items: ItemFormValue[];
}

function toCharacteristics(i: ItemFormValue): Characteristics | undefined {
  const material = i.material?.trim();
  const numeric: NonNullable<Characteristics['numeric']> = {};
  for (const [code, f] of Object.entries(i.num ?? {})) {
    if (f?.value !== null && f?.value !== undefined) numeric[code] = { value: f.value, unitCode: f.unit ?? 'MM' };
  }
  const hasNumeric = Object.keys(numeric).length > 0;
  if (!material && !hasNumeric) return undefined;
  return { material: material || undefined, numeric: hasNumeric ? numeric : undefined };
}

function ReceiptEditor({ initial }: { initial?: Receipt }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const [error, setError] = useState<unknown>(null);

  const initialValues: FormValues = initial
    ? {
        warehouseId: initial.warehouseId,
        supplierName: initial.supplierName,
        externalDocumentNumber: initial.externalDocumentNumber ?? '',

        items: initial.items.map((i) => ({
          product: { value: i.productId, label: `${i.productSku} — ${i.productName}` },
          quantity: i.quantity,
          batchNumber: i.batchNumber ?? '',
          expiryAt: i.expiryAt ? dayjs(i.expiryAt) : null,
          comment: i.comment ?? '',
        })),
      }
    : { items: [{}] };

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const itemsBody: ReceiptItemInput[] = values.items.map((i) => ({
        productId: String(i.product!.value),
        quantity: i.quantity as number,
        batchNumber: i.batchNumber?.trim() || undefined,
        expiryAt: i.expiryAt ? i.expiryAt.toISOString() : undefined,
        comment: i.comment?.trim() || undefined,
        characteristics: toCharacteristics(i),
      }));

      return initial
        ? updateReceipt(initial.id, {
            supplierName: values.supplierName?.trim(),
            externalDocumentNumber: values.externalDocumentNumber?.trim() ?? '',

            items: itemsBody,
          })
        : createReceipt({
            warehouseId: values.warehouseId!,
            supplierName: values.supplierName!.trim(),
            externalDocumentNumber: values.externalDocumentNumber?.trim() || undefined,

            items: itemsBody,
          });
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['receipts'] });
      message.success(initial ? 'Черновик сохранён' : 'Поступление создано');
      navigate(`/receipts/${saved.id}`, { replace: true });
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e, { productId: 'product' });
    },
  });

  return (
    <Card title={initial ? `Поступление ${initial.number}: изменение` : 'Новое поступление'} style={{ maxWidth: 1040 }}>
      <ErrorAlert error={error} style={{ marginBottom: 16 }} />
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={initialValues}
        disabled={save.isPending}
        onFinish={(v) => {
          setError(null);
          save.mutate(v);
        }}
      >
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item name="warehouseId" label="Склад" rules={[{ required: true, message: 'Выберите склад' }]}>
              <WarehouseSelect disabled={!!initial} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="supplierName"
              label="Поставщик"
              rules={[
                { required: true, whitespace: true, message: 'Укажите поставщика' },
                { max: 255, message: 'Не длиннее 255 символов' },
              ]}
            >
              <Input maxLength={255} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="externalDocumentNumber" label="Номер документа поставщика" rules={[{ max: 100, message: 'Не длиннее 100 символов' }]}>
              <Input maxLength={100} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
          </Col>
        </Row>

        {initial && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Уточнения характеристик позиций в черновике не отображаются"
            description="Если вы вводили фактическую длину, ширину, высоту, диаметр или материал, укажите их заново: при сохранении список позиций заменяется целиком."
          />
        )}

        <Form.List
          name="items"
          rules={[
            {
              validator: async (_, value: ItemFormValue[] | undefined) => {
                if (!value || value.length === 0) throw new Error('Добавьте хотя бы одну позицию');
                if (value.length > 200) throw new Error('Не больше 200 позиций');
              },
            },
          ]}
        >
          {(fields, { add, remove }, { errors }) => (
            <>
              {fields.map(({ key, name }) => {
                return (
                  <Card key={key} size="small" style={{ marginBottom: 12 }}>
                    <Row gutter={8}>
                      <Col xs={24} md={10}>
                        <Form.Item name={[name, 'product']} rules={[{ required: true, message: 'Выберите товар' }]}>
                          <ProductSelect />
                        </Form.Item>
                      </Col>
                      <Col xs={12} md={6}>
                        <Form.Item
                          name={[name, 'quantity']}
                          dependencies={[['items', name, 'product']]}
                          rules={[
                            { required: true, message: 'Укажите количество' },
                            {
                              validator: async (_, v: number | null | undefined) => {
                                if (v === null || v === undefined) return;
                                if (v <= 0) throw new Error('Должно быть больше 0');
                                if (!Number.isInteger(v)) throw new Error('Только целое количество штук');
                                if (v > 99_999_999) throw new Error('Слишком большое количество');
                              },
                            },
                          ]}
                        >
                          <InputNumber
                            style={{ width: '100%' }}
                            min={1}
                            precision={0}
                            addonAfter={PCS}
                            placeholder="Кол-во"
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={12} md={6}>
                        <Form.Item name={[name, 'batchNumber']} rules={[{ max: 100, message: 'Не длиннее 100 символов' }]}>
                          <Input placeholder="Партия: если пусто, присвоится автоматически" maxLength={100} />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={2}>
                        <Button danger type="text" disabled={fields.length === 1} onClick={() => remove(name)}>
                          Удалить
                        </Button>
                      </Col>
                      <Col xs={24} md={8}>
                        <Form.Item
                          name={[name, 'expiryAt']}
                          extra="Просроченный товар не принимается; «годен до» включительно"
                        >
                          <DatePicker
                            placeholder="Срок годности"
                            format="DD.MM.YYYY"
                            style={{ width: '100%' }}
                            disabledDate={(d) => d.isBefore(dayjs().startOf('day'))}
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={16}>
                        <Form.Item name={[name, 'comment']} rules={[{ max: 1000, message: 'Не длиннее 1000 символов' }]}>
                          <Input placeholder="Комментарий к позиции" maxLength={1000} />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Collapse
                      ghost
                      size="small"
                      items={[
                        {
                          key: 'chars',
                          label: 'Фактические характеристики партии (необязательно)',
                          forceRender: true,
                          children: (
                            <Row gutter={8}>
                              <Col xs={24} md={8}>
                                <Form.Item name={[name, 'material']} label="Материал" rules={[{ max: 150, message: 'Не длиннее 150 символов' }]}>
                                  <AutoComplete options={MATERIAL_HINTS} placeholder="METAL, WOOD, PLASTIC…" />
                                </Form.Item>
                              </Col>
                              {NUMERIC_ATTRS.map((a) => (
                                <Col xs={24} md={8} key={a.code}>
                                  <Form.Item label={a.label} style={{ marginBottom: 8 }}>
                                    <Space.Compact style={{ width: '100%' }}>
                                      <Form.Item name={[name, 'num', a.code, 'value']} noStyle>
                                        <InputNumber min={0} style={{ width: '65%' }} />
                                      </Form.Item>
                                      <Form.Item name={[name, 'num', a.code, 'unit']} noStyle initialValue="MM">
                                        <Select style={{ width: '35%' }} options={[{ value: 'MM', label: 'мм' }, { value: 'M', label: 'м' }]} />
                                      </Form.Item>
                                    </Space.Compact>
                                  </Form.Item>
                                </Col>
                              ))}
                            </Row>
                          ),
                        },
                      ]}
                    />
                  </Card>
                );
              })}
              <Form.ErrorList errors={errors} />
              <Form.Item>
                <Button type="dashed" onClick={() => add({})} disabled={fields.length >= 200}>
                  + Добавить позицию
                </Button>
              </Form.Item>
            </>
          )}
        </Form.List>

        <Form.Item style={{ marginBottom: 0 }}>
          <Space size={12}>
            <Button type="primary" htmlType="submit" loading={save.isPending}>
              Сохранить
            </Button>
            <Button onClick={() => navigate(initial ? `/receipts/${initial.id}` : '/receipts')}>Отмена</Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  );
}

/** /receipts/new и /receipts/:id/edit (изменять можно только черновик). */
export default function ReceiptFormPage() {
  const { id } = useParams();
  const q = useQuery({ queryKey: ['receipts', 'detail', id], queryFn: () => getReceipt(id!), enabled: !!id });
  if (!id) return <ReceiptEditor />;
  if (q.isLoading) return <Spin />;
  if (q.isError) return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  if (!q.data) return null;
  if (q.data.status !== 'DRAFT') {
    return (
      <Result
        status="warning"
        title="Изменять можно только черновик"
        extra={<Link to={`/receipts/${q.data.id}`}>К поступлению {q.data.number}</Link>}
      />
    );
  }
  return <ReceiptEditor initial={q.data} />;
}
