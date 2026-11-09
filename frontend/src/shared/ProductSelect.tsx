import { useEffect, useState } from 'react';
import { Select, Spin } from 'antd';
import type { LabeledValue } from 'antd/es/select';
import { useQuery } from '@tanstack/react-query';
import { searchProducts } from '../features/reference/api';
import type { Product } from '../features/reference/types';
import { useDebouncedValue } from './useDebouncedValue';

interface Props {
  value?: LabeledValue;
  onChange?: (v: LabeledValue) => void;
  /** Вызывается с загруженными товарами: так форма узнаёт единицу измерения и допустимость дробей. */
  onLoaded?: (products: Product[]) => void;
  allowClear?: boolean;
  placeholder?: string;
}

/** Поиск товара по названию или артикулу с задержкой ввода. */
export default function ProductSelect({ value, onChange, onLoaded, allowClear, placeholder }: Props) {
  const [text, setText] = useState('');
  const q = useDebouncedValue(text.trim(), 300);
  const res = useQuery({ queryKey: ['products', 'search', q], queryFn: () => searchProducts(q), staleTime: 60_000 });

  useEffect(() => {
    if (res.data && onLoaded) onLoaded(res.data.content);
  }, [res.data, onLoaded]);

  return (
    <Select
      showSearch
      labelInValue
      allowClear={allowClear}
      filterOption={false}
      placeholder={placeholder ?? 'Товар: название или артикул'}
      value={value}
      onChange={onChange as never}
      onSearch={setText}
      loading={res.isFetching}
      notFoundContent={res.isFetching ? <Spin size="small" /> : 'Ничего не найдено'}
      options={(res.data?.content ?? []).map((p) => ({ value: p.id, label: `${p.sku} — ${p.name}` }))}
    />
  );
}
