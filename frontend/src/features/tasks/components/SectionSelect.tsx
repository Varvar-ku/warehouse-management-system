import { useState } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useDebouncedValue } from '../../../shared/useDebouncedValue';
import { searchSections } from '../api';

interface Props {
  warehouseId: string;
  value?: string;
  onChange?: (id: string | undefined) => void;
  allowClear?: boolean;
  disabled?: boolean;
  /** Только секции, куда можно размещать товар: активные и не «Нераспределено». Для фильтров не нужно. */
  placeableOnly?: boolean;
  /** Секция по заявке: показывается в списке, даже если не попала в результаты поиска. */
  target?: { id: string; code: string } | null;
}

/**
 * Выбор секции с удалённым поиском. Внешний сканер вводит код как набор с клавиатуры:
 * код + Enter выбирает секцию, если найдена ровно одна.
 */
export default function SectionSelect({ warehouseId, value, onChange, target, allowClear, disabled, placeableOnly }: Props) {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim(), 300);
  const sections = useQuery({
    queryKey: ['sections', warehouseId, q, !!placeableOnly],
    queryFn: () => searchSections(warehouseId, q, placeableOnly),
    enabled: !!warehouseId,
  });

  const found = sections.data ?? [];
  const options = new Map(found.map((s) => [s.id, s.code]));
  if (target && !options.has(target.id)) options.set(target.id, target.code);
  const list = [...options].map(([id, code]) => ({ value: id, label: code }));

  return (
    <Select
      showSearch
      size="large"
      allowClear={allowClear}
      disabled={disabled}
      style={{ width: '100%' }}
      placeholder="Код секции"
      filterOption={false}
      loading={sections.isFetching}
      value={value}
      options={list}
      onSearch={setSearch}
      onChange={(v) => onChange?.(v)}
      onInputKeyDown={(e) => {
        if (e.key !== 'Enter') return;
        const exact = found.filter((s) => s.code.toLowerCase() === search.trim().toLowerCase());
        const pick = exact.length === 1 ? exact[0] : found.length === 1 ? found[0] : undefined;
        if (pick) onChange?.(pick.id);
      }}
      notFoundContent={sections.isFetching ? 'Поиск…' : 'Секция не найдена'}
    />
  );
}
