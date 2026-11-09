import { useMemo, useState } from 'react';
import { Alert, Button, Card, Select, Space, Table, Tag, Typography, type TableColumnsType } from 'antd';
import { fmtQty, PCS } from '../../shared/format';
import { PLACEMENT_STRATEGY } from './statuses';
import type { ItemDecision, ItemRecommendation, Receipt } from './types';

interface Props {
  receipt: Receipt;
  recommendations: ItemRecommendation[];
  confirming: boolean;
  onConfirm: (decisions: ItemDecision[]) => void;
  onClose: () => void;
}

/** Секция найдена автоматически (PlacementStatus.ASSIGNED). Иначе бэкенд предлагает зону «Нераспределено». */
const isAssigned = (r: ItemRecommendation) => r.status === 'ASSIGNED' && !r.requiresStorekeeperAction;

/**
 * Результат подбора секции по позициям. Кладовщик может принять рекомендацию или выбрать другую допустимую секцию
 * (кандидаты приходят с бэкенда). Если секция не найдена, нужно явно подтвердить зону «Нераспределено»:
 * бэкенд не примет приёмку без решения по такой позиции.
 */
export default function RecommendationPanel({ receipt, recommendations, confirming, onConfirm, onClose }: Props) {
  const [chosen, setChosen] = useState<Record<string, string | undefined>>({});
  const itemsById = useMemo(() => new Map(receipt.items.map((i) => [i.id, i])), [receipt.items]);

  // По позиции без найденной секции выбор по умолчанию не подставляется: решение должно быть осознанным.
  const selected = (r: ItemRecommendation) => chosen[r.itemId] ?? (isAssigned(r) ? (r.sectionId ?? undefined) : undefined);
  const missing = recommendations.filter((r) => !selected(r));
  const needAction = recommendations.filter((r) => r.requiresStorekeeperAction);

  // Решение отправляется, если выбрана не рекомендованная секция, а также всегда для позиций без найденной секции.
  const decisions: ItemDecision[] = recommendations
    .filter((r) => {
      const s = selected(r);
      return !!s && (s !== r.sectionId || !isAssigned(r));
    })
    .map((r) => ({ itemId: r.itemId, targetSectionId: selected(r)! }));

  const columns: TableColumnsType<ItemRecommendation> = [
    { title: '№', dataIndex: 'lineNo', width: 50 },
    {
      title: 'Товар',
      render: (_, r) => {
        const i = itemsById.get(r.itemId);
        return i ? (
          <>
            <div>
              {i.productSku} — {i.productName}
            </div>
            <Typography.Text type="secondary">
              {fmtQty(i.quantity)} {PCS}
            </Typography.Text>
          </>
        ) : (
          '—'
        );
      },
    },
    {
      title: 'Основание',
      render: (_, r) => (
        <>
          <div>{r.strategy ? (PLACEMENT_STRATEGY[r.strategy] ?? r.strategy) : '—'}</div>
          {r.ruleName && (
            <Typography.Text type="secondary">
              {r.ruleName}
              {r.ruleVersion !== null ? ` (версия ${r.ruleVersion})` : ''}
            </Typography.Text>
          )}
        </>
      ),
    },
    {
      title: 'Рекомендация',
      render: (_, r) => (
        <Space direction="vertical" size={2}>
          {isAssigned(r) ? (
            <Tag color="green">{r.sectionCode}</Tag>
          ) : (
            <Tag color="orange">подходящей секции нет{r.sectionCode ? `: ${r.sectionCode}` : ''}</Tag>
          )}
          {r.requiresStorekeeperAction && <Tag color="red">нужно решение</Tag>}
        </Space>
      ),
    },
    {
      title: 'Секция для размещения',
      width: 280,
      render: (_, r) => {
        const options = new Map<string, string>();
        if (r.sectionId && r.sectionCode) {
          options.set(r.sectionId, isAssigned(r) ? `${r.sectionCode} (рекомендована)` : `${r.sectionCode}: зона «Нераспределено»`);
        }
        for (const c of r.candidates ?? []) {
          if (!options.has(c.sectionId)) {
            options.set(c.sectionId, `${c.sectionCode}${c.freeCapacity !== null ? `: свободно ${fmtQty(c.freeCapacity)}` : ''}`);
          }
        }
        return (
          <Select
            style={{ width: '100%' }}
            placeholder="Выберите секцию"
            status={selected(r) ? undefined : 'error'}
            value={selected(r)}
            onChange={(v) => setChosen((prev) => ({ ...prev, [r.itemId]: v }))}
            options={[...options].map(([value, label]) => ({ value, label }))}
            notFoundContent="Нет допустимых секций"
          />
        );
      },
    },
    {
      title: 'Пометки',
      render: (_, r) =>
        r.notes && r.notes.length > 0 ? (
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            {r.notes.map((n, idx) => (
              <li key={idx}>{n}</li>
            ))}
          </ul>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <Card
      title="Подбор размещения"
      extra={
        <Button size="small" onClick={onClose}>
          Скрыть
        </Button>
      }
    >
      {needAction.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Позиций, требующих вашего решения: ${needAction.length}`}
          description="Подходящей секции нет. Подтвердите зону «Нераспределено» или выберите секцию из списка допустимых, иначе приёмка не будет подтверждена."
        />
      )}
      <Table<ItemRecommendation>
        rowKey="itemId"
        size="small"
        columns={columns}
        dataSource={recommendations}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
      <Space style={{ marginTop: 16 }}>
        <Button
          type="primary"
          loading={confirming}
          disabled={missing.length > 0}
          onClick={() => onConfirm(decisions)}
        >
          Подтвердить приёмку
        </Button>
        {missing.length > 0 && <Typography.Text type="danger">Выберите секцию для всех позиций ({missing.length})</Typography.Text>}
      </Space>
    </Card>
  );
}
