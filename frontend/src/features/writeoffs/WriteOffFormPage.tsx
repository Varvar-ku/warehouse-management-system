import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, message } from 'antd';
import type { LabeledValue } from 'antd/es/select';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtQty, PCS } from '../../shared/format';
import { applyFieldErrors } from '../../shared/formErrors';
import ProductSelect from '../../shared/ProductSelect';
import WarehouseSelect from '../../shared/WarehouseSelect';
import { listStock, type StockRow } from '../stock/api';
import { createWriteOff } from './api';
import { WRITE_OFF_REASON_OPTIONS } from './statuses';
import type { WriteOffReason } from './types';

interface ItemFormValue {
  product?: LabeledValue;
  stockId?: string;
  quantity?: number;
}

interface FormValues {
  warehouseId?: string;
  reasonCode?: WriteOffReason;
  comment?: string;
  items: ItemFormValue[];
}

/** Строки остатка выбранного товара на складе (только с доступным количеством). */
function StockSelect({
  warehouseId,
  productId,
  value,
  onChange,
  onLoaded,
}: {
  warehouseId?: string;
  productId?: string;
  value?: string;
  onChange?: (id: string) => void;
  onLoaded: (rows: StockRow[]) => void;
}) {
  const q = useQuery({
    queryKey: ['stock', 'for-write-off', warehouseId, productId],
    queryFn: async () =>
      (await listStock({ warehouseId, productId, onlyAvailable: true, includeZero: false, page: 0, size: 100 })).content,
    enabled: !!warehouseId && !!productId,
  });
  useEffect(() => {
    if (q.data) onLoaded(q.data);
  }, [q.data, onLoaded]);
  return (
    <Select
      placeholder={productId ? 'Секция и партия' : 'Сначала выберите товар'}
      disabled={!warehouseId || !productId}
      loading={q.isFetching}
      value={value}
      onChange={onChange}
      notFoundContent="Нет доступного остатка"
      options={(q.data ?? []).map((s) => ({
        value: s.id,
        label: `${s.sectionCode} · партия ${s.batchNumber || '—'} · доступно ${fmtQty(s.availableQuantity)} ${PCS}`,
      }))}
    />
  );
}

/** Новое списание: позиции выбираются из доступных остатков склада. */
export default function WriteOffFormPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const [error, setError] = useState<unknown>(null);
  const [stock, setStock] = useState<Record<string, StockRow>>({});
  const warehouseId = Form.useWatch('warehouseId', form);
  const items = Form.useWatch('items', form);

  const onLoaded = useCallback((rows: StockRow[]) => {
    setStock((prev) => {
      const next = { ...prev };
      for (const r of rows) next[r.id] = r;
      return next;
    });
  }, []);

  const save = useMutation({
    mutationFn: (v: FormValues) =>
      createWriteOff({
        warehouseId: v.warehouseId!,
        reasonCode: v.reasonCode!,
        comment: v.comment?.trim() || undefined,
        items: v.items.map((i) => ({ stockId: i.stockId!, quantity: i.quantity as number })),
      }),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['write-offs'] });
      message.success('Черновик списания создан');
      navigate(`/write-offs/${saved.id}`, { replace: true });
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e);
    },
  });

  return (
    <Card title="Новое списание" style={{ maxWidth: 960 }}>
      <ErrorAlert error={error} style={{ marginBottom: 16 }} />
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ items: [{}] }}
        disabled={save.isPending}
        onFinish={(v) => {
          setError(null);
          save.mutate(v);
        }}
      >
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item name="warehouseId" label="Склад" rules={[{ required: true, message: 'Выберите склад' }]}>
              <WarehouseSelect
                onChange={() => {
                  // остатки другого склада недействительны
                  form.setFieldValue(
                    'items',
                    (form.getFieldValue('items') as ItemFormValue[]).map(() => ({})),
                  );
                }}
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="reasonCode" label="Причина" rules={[{ required: true, message: 'Выберите причину' }]}>
              <Select options={WRITE_OFF_REASON_OPTIONS} placeholder="Причина списания" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          name="comment"
          label="Комментарий"
          dependencies={['reasonCode']}
          rules={[
            ({ getFieldValue }) => ({
              validator: async (_, v: string | undefined) => {
                if (getFieldValue('reasonCode') === 'LOST' && !v?.trim()) throw new Error('Для причины «Утеря» комментарий обязателен');
              },
            }),
            { max: 1000, message: 'Не длиннее 1000 символов' },
          ]}
        >
          <Input.TextArea rows={2} maxLength={1000} showCount />
        </Form.Item>

        <Form.List
          name="items"
          rules={[
            {
              validator: async (_, value: ItemFormValue[] | undefined) => {
                if (!value || value.length === 0) throw new Error('Добавьте хотя бы одну позицию');
                const ids = value.map((i) => i.stockId).filter(Boolean);
                if (new Set(ids).size !== ids.length) throw new Error('Одна и та же строка остатка выбрана дважды');
              },
            },
          ]}
        >
          {(fields, { add, remove }, { errors }) => (
            <>
              {fields.map(({ key, name }) => {
                const pid = items?.[name]?.product?.value;
                const stockId = items?.[name]?.stockId;
                const row = stockId ? stock[stockId] : undefined;
                return (
                  <Row key={key} gutter={8}>
                    <Col xs={24} md={9}>
                      <Form.Item name={[name, 'product']} rules={[{ required: true, message: 'Выберите товар' }]}>
                        <ProductSelect onChange={() => form.setFieldValue(['items', name, 'stockId'], undefined)} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={9}>
                      <Form.Item name={[name, 'stockId']} rules={[{ required: true, message: 'Выберите секцию и партию' }]}>
                        <StockSelect warehouseId={warehouseId} productId={pid !== undefined ? String(pid) : undefined} onLoaded={onLoaded} />
                      </Form.Item>
                    </Col>
                    <Col xs={16} md={4}>
                      <Form.Item
                        name={[name, 'quantity']}
                        dependencies={[['items', name, 'stockId']]}
                        rules={[
                          { required: true, message: 'Количество' },
                          {
                            validator: async (_, v: number | null | undefined) => {
                              if (v === null || v === undefined) return;
                              if (v <= 0) throw new Error('Больше 0');
                              if (row && v > row.availableQuantity) throw new Error(`Не больше ${fmtQty(row.availableQuantity)}`);
                              if (!/^\d{1,14}(\.\d{1,4})?$/.test(String(v))) throw new Error('До 4 знаков после запятой');
                            },
                          },
                        ]}
                      >
                        <InputNumber style={{ width: '100%' }} min={1} precision={0} addonAfter={PCS} placeholder="Кол-во" />
                      </Form.Item>
                    </Col>
                    <Col xs={8} md={2}>
                      <Button danger type="text" disabled={fields.length === 1} onClick={() => remove(name)}>
                        Удалить
                      </Button>
                    </Col>
                  </Row>
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
              Создать черновик
            </Button>
            <Button onClick={() => navigate('/write-offs')}>Отмена</Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  );
}
