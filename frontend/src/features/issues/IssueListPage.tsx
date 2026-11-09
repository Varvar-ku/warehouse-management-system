import { useState } from 'react';
import { Card, Select, Space, Table, Tag, type TableColumnsType } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import ErrorAlert from '../../shared/ErrorAlert';
import { fmtDateTime, fmtQty, PCS } from '../../shared/format';
import { nameOf, useUserNames } from '../directory/hooks';
import { useWarehouses } from '../reference/hooks';
import { listIssues, type Issue, type IssueItem, type IssueStatus } from './api';

const STATUS: Record<IssueStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  CONFIRMED: { label: 'Выдано', color: 'success' },
  CANCELLED: { label: 'Отменено', color: 'default' },
};
const STATUS_OPTIONS = (Object.keys(STATUS) as IssueStatus[]).map((s) => ({ value: s, label: STATUS[s].label }));

/** Документы выдачи (просмотр). Новая выдача оформляется в карточке заявки. */
export default function IssueListPage() {
  const warehouses = useWarehouses();
  const [status, setStatus] = useState<IssueStatus>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const list = useQuery({
    queryKey: ['issues', 'list', { status, page, size }],
    queryFn: () => listIssues({ status, page, size }),
    placeholderData: keepPreviousData,
  });
  const rows = list.data?.content ?? [];
  const names = useUserNames(
    rows.flatMap((i) => [i.issuedTo, i.issuedBy]),
    rows.length > 0,
  );

  const columns: TableColumnsType<Issue> = [
    { title: 'Номер', dataIndex: 'number' },
    { title: 'Статус', dataIndex: 'status', render: (s: IssueStatus) => <Tag color={STATUS[s]?.color}>{STATUS[s]?.label ?? s}</Tag> },
    { title: 'Заявка', dataIndex: 'requestId', render: (id: string) => <Link to={`/requests/${id}`}>Открыть заявку</Link> },
    { title: 'Склад', dataIndex: 'warehouseId', render: (id: string) => warehouses.data?.find((w) => w.id === id)?.name ?? '—' },
    { title: 'Получатель', dataIndex: 'issuedTo', render: (id: string) => nameOf(names, id) },
    { title: 'Выдал', dataIndex: 'issuedBy', render: (id: string) => nameOf(names, id) },
    { title: 'Выдано', dataIndex: 'issuedAt', render: fmtDateTime },
  ];

  const itemColumns: TableColumnsType<IssueItem> = [
    { title: 'Товар', render: (_, i) => `${i.productSku} — ${i.productName}` },
    { title: 'Секция', dataIndex: 'sectionCode' },
    { title: 'Партия', dataIndex: 'batchNumber', render: (b: string | null) => b || '—' },
    { title: 'Количество', align: 'right', render: (_, i) => `${fmtQty(i.quantity)} ${PCS}` },
  ];

  return (
    <Card title="Документы выдачи">
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Статус"
          style={{ width: 200 }}
          options={STATUS_OPTIONS}
          onChange={(v?: IssueStatus) => {
            setStatus(v);
            setPage(0);
          }}
        />
      </Space>
      {list.isError && <ErrorAlert error={list.error} onRetry={() => list.refetch()} style={{ marginBottom: 16 }} />}
      <Table<Issue>
        rowKey="id"
        columns={columns}
        dataSource={rows}
        loading={list.isFetching}
        scroll={{ x: 'max-content' }}
        expandable={{
          expandedRowRender: (issue) => (
            <>
              {issue.comment && <p style={{ marginBottom: 8 }}>Комментарий: {issue.comment}</p>}
              <Table<IssueItem> rowKey="id" size="small" columns={itemColumns} dataSource={issue.items} pagination={false} />
            </>
          ),
        }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: list.data?.totalElements ?? 0,
          showSizeChanger: true,
          showTotal: (total) => `Всего: ${total}`,
          onChange: (p, s) => {
            setPage(p - 1);
            setSize(s);
          },
        }}
      />
    </Card>
  );
}
