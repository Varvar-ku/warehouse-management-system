import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Grid, Row, Select, Space, Spin, message } from 'antd';
import type { LabeledValue } from 'antd/es/select';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { describeError } from '../../api/errors';
import ErrorAlert from '../../shared/ErrorAlert';
import { applyFieldErrors } from '../../shared/formErrors';
import { useDebouncedValue } from '../../shared/useDebouncedValue';
import { useIdempotencyKey } from '../../shared/useIdempotencyKey';
import { searchProducts } from '../reference/api';
import { useWarehouses } from '../reference/hooks';
import { createRequest, getRequest, submitRequest, updateRequest } from './api';
import type { ItemInput, RequestResponse } from './types';
import { PCS } from '../../shared/format';

interface ItemFormValue {
  product?: LabeledValue;
  quantity?: number;
  comment?: string;
}

interface FormValues {
  warehouseId?: string;
  priority?: number;
  comment?: string;
  items: ItemFormValue[];
}

/** Поиск товара по названию или артикулу с задержкой ввода. */
function ProductSelect({ value, onChange }: { value?: LabeledValue; onChange?: (v: LabeledValue) => void }) {
  const [text, setText] = useState('');
  const q = useDebouncedValue(text.trim(), 300);
  const res = useQuery({ queryKey: ['products', 'search', q], queryFn: () => searchProducts(q), staleTime: 60_000 });

  return (
    <Select
      showSearch
      labelInValue
      filterOption={false}
      placeholder="Товар: название или артикул"
      value={value}
      onChange={onChange}
      onSearch={setText}
      loading={res.isFetching}
      notFoundContent={res.isFetching ? <Spin size="small" /> : 'Ничего не найдено'}
      options={(res.data?.content ?? []).map((p) => ({ value: p.id, label: `${p.sku} — ${p.name}` }))}
    />
  );
}

function RequestEditor({ initial }: { initial?: RequestResponse }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  // На узком экране каждая позиция оформляется отдельной рамкой, иначе поля соседних позиций сливаются
  const narrow = !Grid.useBreakpoint().md;
  const warehouses = useWarehouses();
  const [error, setError] = useState<unknown>(null);
  const sendAfterSave = useRef(false);

  // Склад пользователь не выбирает: заявка создаётся на единственный активный склад
  const activeWarehouse = (warehouses.data ?? []).find((w) => w.active);
  useEffect(() => {
    if (!initial && activeWarehouse && !form.getFieldValue('warehouseId')) {
      form.setFieldValue('warehouseId', activeWarehouse.id);
    }
  }, [initial, activeWarehouse, form]);

  const initialValues: FormValues = initial
    ? {
        warehouseId: initial.warehouseId,
        priority: initial.priority,
        comment: initial.comment ?? '',
        items: initial.items.map((i) => ({
          product: { value: i.productId, label: `${i.productSku} — ${i.productName}` },
          quantity: i.requestedQuantity,
          comment: i.comment ?? '',
        })),
      }
    : { priority: 0, items: [{}] };

  // Ключи идемпотентности: двойной клик или повтор после сбоя сети не создадут второй черновик и не отправят заявку дважды
  const createKey = useIdempotencyKey();
  const submitKey = useIdempotencyKey();

  const save = useMutation({
    mutationFn: async ({ values, send }: { values: FormValues; send: boolean }) => {
      const itemsBody: ItemInput[] = values.items.map((i) => ({
        productId: String(i.product!.value),
        quantity: i.quantity as number,
        comment: i.comment?.trim() || undefined,
      }));
      const createBody = {
        warehouseId: values.warehouseId!,
        priority: values.priority ?? 0,
        comment: values.comment?.trim() || undefined,
        items: itemsBody,
      };
      const saved = initial
        ? await updateRequest(initial.id, {
            priority: values.priority ?? 0,
            comment: values.comment?.trim() ?? '',
            items: itemsBody,
          })
        : await createRequest(createBody, createKey.forBody(createBody));
      if (!initial) createKey.reset(); // черновик создан: следующее создание не должно получить этот ответ из кэша
      if (!send) return { saved, submitError: null as unknown };
      try {
        await submitRequest(saved.id, submitKey.forBody(saved.id));
        submitKey.reset();
        return { saved, submitError: null as unknown };
      } catch (e) {
        // черновик уже сохранён: не теряем его и не создаём дубликат при повторе
        return { saved, submitError: e as unknown };
      }
    },
    onSuccess: ({ saved, submitError }, { send }) => {
      queryClient.invalidateQueries({ queryKey: ['requests'] });
      if (submitError) {
        message.warning(`Черновик сохранён, но отправить не удалось: ${describeError(submitError).message}`, 6);
      } else {
        message.success(send ? 'Заявка отправлена' : 'Черновик сохранён');
      }
      // после сохранения возвращаем на главную страницу раздела (список заявок);
      // если отправить не удалось, открываем сохранённый черновик, чтобы можно было повторить
      navigate(submitError ? `/requests/${saved.id}` : '/requests', { replace: true });
    },
    onError: (e) => {
      setError(e);
      applyFieldErrors(form, e, { productId: 'product' });
    },
  });

  const onFinish = (values: FormValues) => {
    setError(null);
    const send = sendAfterSave.current;
    sendAfterSave.current = false;
    save.mutate({ values, send });
  };

  return (
    <Card title={initial ? `Заявка ${initial.number}: изменение` : 'Новая заявка'} style={{ maxWidth: 960 }}>
      <ErrorAlert error={error} style={{ marginBottom: 16 }} />
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={initialValues}
        disabled={save.isPending}
        onFinish={onFinish}
        onFinishFailed={() => {
          sendAfterSave.current = false;
        }}
      >
        <Form.Item name="warehouseId" hidden rules={[{ required: true, message: 'Нет доступного склада' }]}>
          <Input />
        </Form.Item>
        {!initial && warehouses.isSuccess && !activeWarehouse && (
          <Alert type="error" showIcon style={{ marginBottom: 16 }} message="Нет активного склада: обратитесь к администратору" />
        )}
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item name="priority" label="Приоритет (0–9, больше — срочнее)">
              <InputNumber min={0} max={9} precision={0} style={{ width: 120 }} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="comment" label="Комментарий" rules={[{ max: 2000, message: 'Не длиннее 2000 символов' }]}>
          <Input.TextArea rows={2} maxLength={2000} showCount />
        </Form.Item>

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
                  <Row
                    key={key}
                    gutter={8}
                    style={
                      narrow
                        ? { padding: '12px 8px 0', marginBottom: 12, border: '1px solid #f0f0f0', borderRadius: 8 }
                        : undefined
                    }
                  >
                    <Col xs={24} md={9}>
                      <Form.Item name={[name, 'product']} rules={[{ required: true, message: 'Выберите товар' }]}>
                        <ProductSelect />
                      </Form.Item>
                    </Col>
                    <Col xs={12} md={5}>
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
                    <Col xs={12} md={7}>
                      <Form.Item name={[name, 'comment']} rules={[{ max: 1000, message: 'Не длиннее 1000 символов' }]}>
                        <Input placeholder="Комментарий к позиции" maxLength={1000} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={3} style={{ textAlign: 'right' }}>
                      <Button danger type="text" disabled={fields.length === 1} onClick={() => remove(name)}>
                        Удалить
                      </Button>
                    </Col>
                  </Row>
                );
              })}
              <Form.Item>
                <Button onClick={() => add({})} disabled={fields.length >= 200}>
                  Добавить позицию
                </Button>
                <Form.ErrorList errors={errors} />
              </Form.Item>
            </>
          )}
        </Form.List>

        <Space wrap>
          <Button
            type="primary"
            htmlType="submit"
            onClick={() => {
              sendAfterSave.current = false;
            }}
          >
            Сохранить черновик
          </Button>
          <Button
            onClick={() => {
              sendAfterSave.current = true;
              form.submit();
            }}
          >
            Сохранить и отправить
          </Button>
          <Button onClick={() => navigate(initial ? `/requests/${initial.id}` : '/requests')}>Отмена</Button>
        </Space>
      </Form>
    </Card>
  );
}

export default function RequestFormPage() {
  const { id } = useParams();
  const q = useQuery({
    queryKey: ['requests', 'detail', id],
    queryFn: () => getRequest(id!),
    enabled: !!id,
  });

  if (!id) return <RequestEditor />;
  if (q.isLoading) return <Spin />;
  if (q.isError) return <ErrorAlert error={q.error} onRetry={() => q.refetch()} />;
  if (q.data && q.data.status !== 'DRAFT') {
    return (
      <Alert
        type="warning"
        showIcon
        message="Изменять можно только черновик"
        action={<Link to={`/requests/${id}`}>К заявке</Link>}
      />
    );
  }
  return <RequestEditor key={id} initial={q.data} />;
}
