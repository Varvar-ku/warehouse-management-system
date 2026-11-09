import { useState } from 'react';
import { Button, Checkbox, Input, Select, Space, Table, Tag, message, type TableColumnsType } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ConfirmModal from '../../../shared/ConfirmModal';
import ErrorAlert from '../../../shared/ErrorAlert';
import { useDebouncedValue } from '../../../shared/useDebouncedValue';
import { archiveProduct, restoreProduct, searchCatalogProducts } from './api';
import { useProductTypes } from './hooks';
import ProductModal from './ProductModal';
import type { CatalogProduct } from './types';

export default function ProductsTab() {
  const queryClient = useQueryClient();
  const types = useProductTypes();
  const [q, setQ] = useState('');
  const [productTypeId, setProductTypeId] = useState<string>();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [target, setTarget] = useState<CatalogProduct | 'new' | null>(null);
  const [confirm, setConfirm] = useState<{ product: CatalogProduct; action: 'archive' | 'restore' } | null>(null);
  const [error, setError] = useState<unknown>(null);
  const dq = useDebouncedValue(q, 300);

  const list = useQuery({
    queryKey: ['catalog', 'products', { q: dq, productTypeId, includeArchived, page, size }],
    queryFn: () => searchCatalogProducts({ q: dq || undefined, productTypeId, includeArchived, page, size }),
    placeholderData: keepPreviousData,
  });

  const toggle = useMutation({
    mutationFn: ({ product, action }: { product: CatalogProduct; action: 'archive' | 'restore' }) =>
      action === 'archive' ? archiveProduct(product.id) : restoreProduct(product.id),
    onSuccess: (_, { action }) => {
      setError(null);
      setConfirm(null);
      message.success(action === 'archive' ? 'Товар перенесён в архив' : 'Товар восстановлен');
      void queryClient.invalidateQueries({ queryKey: ['catalog', 'products'] });
      void queryClient.invalidateQueries({ queryKey: ['products'] });
    },
    onError: (e) => {
      setConfirm(null);
      setError(e);
    },
  });

  const columns: TableColumnsType<CatalogProduct> = [
    { title: 'Артикул', dataIndex: 'sku' },
    { title: 'Название', dataIndex: 'name' },
    { title: 'Тип', dataIndex: 'productTypeCode' },
    { title: 'Состояние', dataIndex: 'active', render: (a: boolean) => (a ? <Tag color="success">Активен</Tag> : <Tag>В архиве</Tag>) },
    {
      title: '',
      render: (_, p) => (
        <Space>
          <Button size="small" onClick={() => setTarget(p)}>
            Изменить
          </Button>
          {p.active ? (
            <Button size="small" danger onClick={() => setConfirm({ product: p, action: 'archive' })}>
              В архив
            </Button>
          ) : (
            <Button size="small" onClick={() => setConfirm({ product: p, action: 'restore' })}>
              Восстановить
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search allowClear placeholder="Название или артикул" style={{ width: 260 }} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <Select
          allowClear
          placeholder="Тип товара"
          style={{ width: 220 }}
          options={(types.data ?? []).map((t) => ({ value: t.id, label: t.name }))}
          onChange={(v?: string) => {
            setProductTypeId(v);
            setPage(0);
          }}
        />
        <Checkbox checked={includeArchived} onChange={(e) => { setIncludeArchived(e.target.checked); setPage(0); }}>
          с архивными
        </Checkbox>
        <Button type="primary" onClick={() => setTarget('new')}>
          Новый товар
        </Button>
      </Space>
      <ErrorAlert error={error} style={{ marginBottom: 16 }} />
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<CatalogProduct>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={list.data?.content}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: list.data?.totalElements ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `Всего: ${t}`,
          onChange: (p, s) => {
            setPage(p - 1);
            setSize(s);
          },
        }}
      />
      <ProductModal target={target} onClose={() => setTarget(null)} />
      <ConfirmModal
        open={!!confirm}
        title={confirm?.action === 'archive' ? 'Перенести товар в архив?' : 'Восстановить товар?'}
        description={
          confirm?.action === 'archive'
            ? `${confirm.product.sku}: архивный товар нельзя выбрать в новых заявках и поступлениях. Бэкенд откажет, если по нему есть остаток или открытые документы.`
            : confirm
              ? `${confirm.product.sku} снова станет доступен для выбора.`
              : ''
        }
        okText={confirm?.action === 'archive' ? 'В архив' : 'Восстановить'}
        loading={toggle.isPending}
        onConfirm={() => {
          setError(null);
          if (confirm) toggle.mutate(confirm);
        }}
        onCancel={() => setConfirm(null)}
      />
    </>
  );
}
