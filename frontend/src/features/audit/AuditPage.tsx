import { useState } from 'react';
import { Button, Card, DatePicker, Input, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import type { Dayjs } from 'dayjs';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import ErrorAlert from '../../shared/ErrorAlert';
import { rangeToQuery } from '../../shared/dateRange';
import { fmtDateTime } from '../../shared/format';
import { nameOf, useUserNames } from '../directory/hooks';
import { listAuditEvents, type AuditEvent } from './api';

const UUID_RE = /^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/;

interface Filters {
  entityType: string;
  entityId: string;
  actorId: string;
  action: string;
  range: [Dayjs | null, Dayjs | null] | null;
}
const EMPTY: Filters = { entityType: '', entityId: '', actorId: '', action: '', range: null };

/** Журнал аудита (только ADMIN). События неизменяемы; новые сверху. */
export default function AuditPage() {
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [applied, setApplied] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);

  const actorInvalid = draft.actorId.trim() !== '' && !UUID_RE.test(draft.actorId.trim());
  const { from, to } = rangeToQuery(applied.range, true);

  const list = useQuery({
    queryKey: ['audit', 'events', { ...applied, range: undefined, from, to, page, size }],
    queryFn: () =>
      listAuditEvents({
        entityType: applied.entityType.trim() || undefined,
        entityId: applied.entityId.trim() || undefined,
        actorId: applied.actorId.trim() || undefined,
        action: applied.action.trim() || undefined,
        from,
        to,
        page,
        size,
      }),
    placeholderData: keepPreviousData,
  });
  const rows = list.data?.items ?? [];
  const people = useUserNames(rows.map((e) => e.actorId));

  const apply = () => {
    setPage(0);
    setApplied(draft);
  };

  const columns: TableColumnsType<AuditEvent> = [
    { title: 'Время', dataIndex: 'occurredAt', render: fmtDateTime, width: 170 },
    { title: 'Сервис', dataIndex: 'service', width: 120 },
    { title: 'Действие', dataIndex: 'action', render: (a: string) => <Tag>{a}</Tag> },
    {
      title: 'Объект',
      render: (_, e) => (
        <Space direction="vertical" size={0}>
          <span>{e.entityType}</span>
          <Typography.Text type="secondary" copyable={{ text: e.entityId }} style={{ fontSize: 12 }}>
            {e.entityId.length > 14 ? `${e.entityId.slice(0, 8)}…` : e.entityId}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Исполнитель',
      render: (_, e) => (e.actorId ? `${nameOf(people, e.actorId)}${e.actorRole ? ` (${e.actorRole})` : ''}` : e.actorRole ?? 'система'),
    },
    {
      title: 'trace_id',
      dataIndex: 'traceId',
      render: (t: string | null) => (t ? <Typography.Text copyable={{ text: t }}>{t.slice(0, 8)}…</Typography.Text> : '—'),
    },
  ];

  return (
    <Card title="Журнал аудита">
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          placeholder="Действие, например request.*"
          style={{ width: 230 }}
          value={draft.action}
          onChange={(e) => setDraft({ ...draft, action: e.target.value })}
          onPressEnter={apply}
        />
        <Input
          allowClear
          placeholder="Тип объекта"
          style={{ width: 160 }}
          value={draft.entityType}
          onChange={(e) => setDraft({ ...draft, entityType: e.target.value })}
          onPressEnter={apply}
        />
        <Input
          allowClear
          placeholder="Идентификатор объекта"
          style={{ width: 260 }}
          value={draft.entityId}
          onChange={(e) => setDraft({ ...draft, entityId: e.target.value })}
          onPressEnter={apply}
        />
        <Input
          allowClear
          placeholder="UUID исполнителя"
          status={actorInvalid ? 'error' : undefined}
          style={{ width: 280 }}
          value={draft.actorId}
          onChange={(e) => setDraft({ ...draft, actorId: e.target.value })}
          onPressEnter={apply}
        />
        <DatePicker.RangePicker
          showTime
          format="DD.MM.YYYY HH:mm"
          value={draft.range as [Dayjs, Dayjs] | null}
          onChange={(v) => setDraft({ ...draft, range: v })}
        />
        <Button type="primary" disabled={actorInvalid} onClick={apply}>
          Найти
        </Button>
        <Button
          onClick={() => {
            setDraft(EMPTY);
            setApplied(EMPTY);
            setPage(0);
          }}
        >
          Сбросить
        </Button>
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<AuditEvent>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        expandable={{
          rowExpandable: (e) => e.details !== null && e.details !== undefined,
          expandedRowRender: (e) => (
            <pre style={{ margin: 0, maxHeight: 320, overflow: 'auto', fontSize: 12 }}>{JSON.stringify(e.details, null, 2)}</pre>
          ),
        }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: list.data?.totalItems ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
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
