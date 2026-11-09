import { useMemo, useState } from 'react';
import { Alert, Button, Card, Checkbox, DatePicker, Form, Grid, Input, Menu, Select, Space, Table, message, type TableColumnsType } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import WarehouseSelect from '../../shared/WarehouseSelect';
import { useWorkers } from '../directory/hooks';
import { fetchReport, type ReportParams } from './api';
import { REPORTS, findReport } from './config';
import { downloadCsv, toCsv } from './csv';
import type { FilterDef, ReportDef, Row } from './types';

const UUID_RE = /^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/;
/** Предел выгрузки в CSV: клиент собирает строки страницами по 500, поэтому ограничиваем объём. */
const EXPORT_LIMIT = 5000;
const PAGE_LIMIT = 500;

type FormValues = Record<string, unknown> & { range?: [Dayjs | null, Dayjs | null] | null };

const DATE = 'YYYY-MM-DD';

/** Значения формы в параметры запроса: даты yyyy-MM-dd, пустые поля опускаются. */
function toParams(def: ReportDef, v: FormValues): ReportParams {
  const p: ReportParams = {};
  for (const f of def.filters) {
    if (f.kind === 'range') {
      if (v.range?.[0]) p.from = v.range[0].format(DATE);
      if (v.range?.[1]) p.to = v.range[1].format(DATE);
      continue;
    }
    const raw = v[f.name];
    if (f.kind === 'date') {
      if (raw) p[f.name] = (raw as Dayjs).format(DATE);
    } else if (f.kind === 'bool') {
      if (raw) p[f.name] = true;
    } else if (typeof raw === 'string' && raw.trim() !== '') {
      p[f.name] = raw.trim();
    }
  }
  return p;
}

function FilterField({ f }: { f: FilterDef }) {
  const workers = useWorkers(f.kind === 'worker');
  switch (f.kind) {
    case 'range':
      return (
        <Form.Item name="range" label="Период">
          <DatePicker.RangePicker format="DD.MM.YYYY" allowEmpty={[true, true]} />
        </Form.Item>
      );
    case 'date':
      return (
        <Form.Item name={f.name} label={f.label}>
          <DatePicker format="DD.MM.YYYY" disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
        </Form.Item>
      );
    case 'text':
      return (
        <Form.Item name={f.name} label={f.label}>
          <Input allowClear style={{ width: f.width ?? 170 }} />
        </Form.Item>
      );
    case 'uuid':
      return (
        <Form.Item
          name={f.name}
          label={f.label}
          rules={[{ validator: (_, v?: string) => (!v || UUID_RE.test(v.trim()) ? Promise.resolve() : Promise.reject(new Error('Нужен UUID'))) }]}
        >
          <Input allowClear style={{ width: 290 }} />
        </Form.Item>
      );
    case 'select':
      return (
        <Form.Item name={f.name} label={f.label}>
          <Select allowClear style={{ width: 190 }} options={f.options} />
        </Form.Item>
      );
    case 'bool':
      return (
        <Form.Item name={f.name} valuePropName="checked" label=" " colon={false}>
          <Checkbox>{f.label}</Checkbox>
        </Form.Item>
      );
    case 'warehouse':
      return (
        <Form.Item name={f.name} label="Склад">
          <WarehouseSelect allowClear style={{ width: 220 }} />
        </Form.Item>
      );
    case 'worker':
      return (
        <Form.Item name={f.name} label="Работник">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 230 }}
            loading={workers.isLoading}
            options={(workers.data ?? []).map((w) => ({ value: w.id, label: w.fullName }))}
          />
        </Form.Item>
      );
  }
}

/** Один отчёт: форма фильтров, таблица с серверной пагинацией и выгрузка в CSV. Колонки и фильтры берутся из config.ts. */
function ReportView({ def }: { def: ReportDef }) {
  const [form] = Form.useForm<FormValues>();
  const [params, setParams] = useState<ReportParams>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [exporting, setExporting] = useState(false);

  const q = useQuery({
    queryKey: ['reports', def.key, params, page, size],
    queryFn: () => fetchReport(def.path, { ...params, page, size }),
    placeholderData: keepPreviousData,
  });

  const columns: TableColumnsType<Row> = useMemo(
    () => def.columns.map((c) => ({ title: c.title, align: c.align, render: (_: unknown, r: Row) => c.value(r) })),
    [def],
  );

  const run = () =>
    form.validateFields().then((v) => {
      setPage(0);
      setParams(toParams(def, v));
    });

  const exportCsv = async () => {
    setExporting(true);
    try {
      const rows: Row[] = [];
      let total = 0;
      for (let p = 0; rows.length < EXPORT_LIMIT; p++) {
        const res = await fetchReport(def.path, { ...params, page: p, size: PAGE_LIMIT });
        rows.push(...res.content);
        total = res.totalElements;
        if (p + 1 >= res.totalPages) break;
      }
      const cut = rows.slice(0, EXPORT_LIMIT);
      downloadCsv(`${def.key}-${dayjs().format('YYYY-MM-DD')}.csv`, toCsv(def.columns, cut));
      if (total > cut.length) message.warning(`Выгружено ${cut.length} из ${total} строк: сузьте период или фильтры`);
      else message.success(`Выгружено строк: ${cut.length}`);
    } catch (e) {
      message.error(e instanceof Error ? e.message : 'Не удалось выгрузить отчёт');
    } finally {
      setExporting(false);
    }
  };

  return (
    <Card title={def.title}>
      {def.description && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={def.description} />}
      <Form form={form} layout="inline" style={{ rowGap: 8, marginBottom: 16 }} onFinish={run}>
        {def.filters.map((f, i) => (
          <FilterField key={f.kind + ('name' in f ? f.name : i)} f={f} />
        ))}
        <Form.Item label=" " colon={false}>
          <Space>
            <Button type="primary" htmlType="submit">
              Сформировать
            </Button>
            <Button
              onClick={() => {
                form.resetFields();
                setParams({});
                setPage(0);
              }}
            >
              Сбросить
            </Button>
            <Button loading={exporting} disabled={!q.data || q.data.totalElements === 0} onClick={() => void exportCsv()}>
              Выгрузить CSV
            </Button>
          </Space>
        </Form.Item>
      </Form>
      {q.isError && <ErrorAlert error={q.error} onRetry={() => q.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Row>
        size="small"
        columns={columns}
        dataSource={q.data?.content}
        rowKey={(_, i) => `${page}-${i}`}
        loading={q.isFetching}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: q.data?.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100, 500],
          showTotal: (t) => `Всего: ${t}`,
          onChange: (p, s) => {
            setPage(p - 1);
            setSize(s);
          },
        }}
      />
    </Card>
  );
}

/** Раздел «Отчёты» (STOREKEEPER, ADMIN): слева список отчётов, справа выбранный. */
export default function ReportsPage() {
  const { key } = useParams();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const def = findReport(key);

  const groups = [...new Set(REPORTS.map((r) => r.group))];
  const menuItems = groups.map((g) => ({
    type: 'group' as const,
    key: g,
    label: g,
    children: REPORTS.filter((r) => r.group === g).map((r) => ({ key: r.key, label: r.title })),
  }));

  return (
    <div style={{ display: 'flex', gap: 16, flexDirection: screens.md ? 'row' : 'column', alignItems: 'flex-start' }}>
      <Card styles={{ body: { padding: 8 } }} style={{ width: screens.md ? 230 : '100%', flex: 'none' }}>
        <Menu
          mode={screens.md ? 'inline' : 'horizontal'}
          selectedKeys={[def.key]}
          items={screens.md ? menuItems : REPORTS.map((r) => ({ key: r.key, label: r.title }))}
          onClick={(e) => navigate(`/reports/${e.key}`)}
          style={{ border: 'none' }}
        />
      </Card>
      <div style={{ flex: 1, minWidth: 0, width: '100%' }}>
        <ReportView key={def.key} def={def} />
      </div>
    </div>
  );
}
