import { Card, Tabs } from 'antd';
import ProductsTab from './ProductsTab';
import { AttributesTab, TypesTab, UnitsTab } from './ReferenceTabs';

/** Каталог (ADMIN): товары, типы, единицы измерения, определения атрибутов. */
export default function CatalogPage() {
  return (
    <Card title="Каталог">
      <Tabs
        items={[
          { key: 'products', label: 'Товары', children: <ProductsTab /> },
          { key: 'types', label: 'Типы товаров', children: <TypesTab /> },
          { key: 'units', label: 'Единицы', children: <UnitsTab /> },
          { key: 'attributes', label: 'Атрибуты', children: <AttributesTab /> },
        ]}
      />
    </Card>
  );
}
