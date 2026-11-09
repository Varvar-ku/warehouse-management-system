import { useState } from 'react';
import { Button, Card, Descriptions, Drawer, Select, Space, Spin, Table, Tag, Timeline, Typography, type TableColumnsType } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import ErrorAlert from '../../../shared/ErrorAlert';
import { fmtDateTime } from '../../../shared/format';
import WarehouseSelect from '../../../shared/WarehouseSelect';
import { nameOf, useUserNames } from '../../directory/hooks';
import { listRuleVersions, listRules } from './api';
import { describeConditions } from './conditions';
import RuleModal from './RuleModal';
import type { PlacementRule, RuleVersion } from './types';

function VersionsDrawer({ rule, onClose }: { rule: PlacementRule | null; onClose: () => void }) {
  const q = useQuery({ queryKey: ['admin', 'rules', 'versions', rule?.id], queryFn: () => listRuleVersions(rule!.id), enabled: !!rule });
  const people = useUserNames((q.data ?? []).map((v) => v.changedBy), !!q.data);
  return (
    <Drawer open={!!rule} onClose={onClose} width={520} title={rule ? `История версий: ${rule.name}` : ''}>
      {q.isLoading && <Spin />}
      {q.isError && <ErrorAlert error={q.error} onRetry={() => q.refetch()} />}
      <Timeline
        items={(q.data ?? []).map((v: RuleVersion) => ({
          children: (
            <Descriptions size="small" column={1} title={`Версия ${v.version}`} extra={<Typography.Text type="secondary">{fmtDateTime(v.changedAt)}</Typography.Text>}>
              <Descriptions.Item label="Изменил">{nameOf(people, v.changedBy)}</Descriptions.Item>
              <Descriptions.Item label="Название">{v.snapshot.name}</Descriptions.Item>
              <Descriptions.Item label="Приоритет">{v.snapshot.priority}</Descriptions.Item>
              <Descriptions.Item label="Состояние">
                {v.snapshot.active ? 'активно' : 'отключено'}
                {v.snapshot.fallback ? ', резервное' : ''}
              </Descriptions.Item>
              <Descriptions.Item label="Условия">{describeConditions(v.snapshot.conditions)}</Descriptions.Item>
            </Descriptions>
          ),
        }))}
      />
    </Drawer>
  );
}

/** Правила размещения (ADMIN правит, STOREKEEPER смотрит). */
export default function RulesPage({ readOnly = false }: { readOnly?: boolean }) {
  const [warehouseId, setWarehouseId] = useState<string>();
  const [active, setActive] = useState<boolean>();
  const [fallback, setFallback] = useState<boolean>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [target, setTarget] = useState<PlacementRule | 'new' | null>(null);
  const [history, setHistory] = useState<PlacementRule | null>(null);

  const list = useQuery({
    queryKey: ['admin', 'rules', 'list', { warehouseId, active, fallback, page, size }],
    queryFn: () => listRules({ warehouseId, active, fallback, page, size }),
    placeholderData: keepPreviousData,
  });
  const reset = () => setPage(0);

  const columns: TableColumnsType<PlacementRule> = [
    { title: 'Приоритет', dataIndex: 'priority', width: 100, align: 'right' },
    {
      title: 'Название',
      dataIndex: 'name',
      render: (n: string, r) => (
        <Space size={4} wrap>
          {n}
          {r.fallback && <Tag color="purple">резервное</Tag>}
        </Space>
      ),
    },
    { title: 'Условия', render: (_, r) => describeConditions(r.conditions) },
    { title: 'Секция', dataIndex: 'targetSectionCode' },
    { title: 'Версия', dataIndex: 'ruleVersion', align: 'right', width: 90 },
    { title: 'Состояние', dataIndex: 'active', render: (a: boolean) => (a ? <Tag color="success">Активно</Tag> : <Tag>Отключено</Tag>) },
    {
      title: '',
      render: (_, r) => (
        <Space>
          {!readOnly && (
            <Button size="small" onClick={() => setTarget(r)}>
              Изменить
            </Button>
          )}
          <Button size="small" onClick={() => setHistory(r)}>
            Версии
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Правила размещения"
      extra={
        !readOnly && (
          <Button type="primary" onClick={() => setTarget('new')}>
            Новое правило
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <WarehouseSelect allowClear style={{ width: 220 }} value={warehouseId} onChange={(v) => { setWarehouseId(v); reset(); }} />
        <Select
          allowClear
          placeholder="Состояние"
          style={{ width: 160 }}
          options={[{ value: true, label: 'Активные' }, { value: false, label: 'Отключённые' }]}
          onChange={(v?: boolean) => { setActive(v); reset(); }}
        />
        <Select
          allowClear
          placeholder="Вид"
          style={{ width: 160 }}
          options={[{ value: false, label: 'Обычные' }, { value: true, label: 'Резервные' }]}
          onChange={(v?: boolean) => { setFallback(v); reset(); }}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<PlacementRule>
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
          onChange: (p, s) => { setPage(p - 1); setSize(s); },
        }}
      />
      <RuleModal target={target} defaultWarehouseId={warehouseId} onClose={() => setTarget(null)} />
      <VersionsDrawer rule={history} onClose={() => setHistory(null)} />
    </Card>
  );
}
