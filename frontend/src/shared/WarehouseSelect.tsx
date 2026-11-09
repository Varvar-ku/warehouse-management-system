import { Select } from 'antd';
import { useWarehouses } from '../features/reference/hooks';

interface Props {
  value?: string;
  onChange?: (id: string | undefined) => void;
  allowClear?: boolean;
  disabled?: boolean;
  placeholder?: string;
  style?: React.CSSProperties;
}

/** Выбор склада из кэшированного списка (только активные). */
export default function WarehouseSelect({ value, onChange, allowClear, disabled, placeholder, style }: Props) {
  const warehouses = useWarehouses();
  return (
    <Select
      showSearch
      optionFilterProp="label"
      allowClear={allowClear}
      disabled={disabled}
      placeholder={placeholder ?? 'Склад'}
      loading={warehouses.isLoading}
      value={value}
      style={style}
      onChange={(v) => onChange?.(v)}
      options={(warehouses.data ?? [])
        .filter((w) => w.active || w.id === value)
        .map((w) => ({ value: w.id, label: `${w.name} (${w.code})` }))}
    />
  );
}
