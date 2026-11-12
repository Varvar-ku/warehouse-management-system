import { lazy, Suspense } from 'react';
import { Result, Spin } from 'antd';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { RequireAuth, RequireRole } from './auth/guards';
import { homePath } from './auth/roles';
import AppLayout from './layout/AppLayout';
import { useArrowNav } from './shared/useArrowNav';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';

// Страницы разделов грузятся по требованию: начальный пакет содержит только вход и каркас
const RequestListPage = lazy(() => import('./features/requests/RequestListPage'));
const RequestFormPage = lazy(() => import('./features/requests/RequestFormPage'));
const RequestDetailPage = lazy(() => import('./features/requests/RequestDetailPage'));
const PickingListPage = lazy(() => import('./features/picking/PickingListPage'));
const MyTasksPage = lazy(() => import('./features/tasks/MyTasksPage'));
const PickingTaskPage = lazy(() => import('./features/tasks/PickingTaskPage'));
const PlacementTaskPage = lazy(() => import('./features/tasks/PlacementTaskPage'));
const ReceiptListPage = lazy(() => import('./features/receipts/ReceiptListPage'));
const ReceiptFormPage = lazy(() => import('./features/receipts/ReceiptFormPage'));
const ReceiptDetailPage = lazy(() => import('./features/receipts/ReceiptDetailPage'));
const StockPage = lazy(() => import('./features/stock/StockPage'));
const WriteOffListPage = lazy(() => import('./features/writeoffs/WriteOffListPage'));
const WriteOffFormPage = lazy(() => import('./features/writeoffs/WriteOffFormPage'));
const WriteOffDetailPage = lazy(() => import('./features/writeoffs/WriteOffDetailPage'));
const IssueListPage = lazy(() => import('./features/issues/IssueListPage'));
const ShortageListPage = lazy(() => import('./features/shortages/ShortageListPage'));
const AdjustmentListPage = lazy(() => import('./features/adjustments/AdjustmentListPage'));
const AdjustmentDetailPage = lazy(() => import('./features/adjustments/AdjustmentDetailPage'));
const ReportsPage = lazy(() => import('./features/reports/ReportsPage'));
const UserListPage = lazy(() => import('./features/admin/users/UserListPage'));
const CatalogPage = lazy(() => import('./features/admin/catalog/CatalogPage'));
const WarehousesPage = lazy(() => import('./features/admin/warehouses/WarehousesPage'));
const RulesPage = lazy(() => import('./features/admin/rules/RulesPage'));
const AuditPage = lazy(() => import('./features/audit/AuditPage'));
const NotificationsPage = lazy(() => import('./features/notifications/NotificationsPage'));

function HomeRedirect() {
  const { roles } = useAuth();
  return <Navigate to={homePath(roles)} replace />;
}

function PageLoader() {
  return (
    <div style={{ display: 'grid', placeItems: 'center', padding: 48 }}>
      <Spin size="large" />
    </div>
  );
}

export default function App() {
  useArrowNav(); // стрелки переводят фокус между кнопками на всех экранах
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<AppLayout />}>
            <Route index element={<HomeRedirect />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="notifications" element={<NotificationsPage />} />
          <Route
            path="requests"
            element={
              <RequireRole roles={['USER', 'STOREKEEPER', 'ADMIN']}>
                <RequestListPage />
              </RequireRole>
            }
          />
          <Route
            path="requests/new"
            element={
              <RequireRole roles={['USER']}>
                <RequestFormPage />
              </RequireRole>
            }
          />
          <Route
            path="requests/:id"
            element={
              <RequireRole roles={['USER', 'STOREKEEPER', 'ADMIN']}>
                <RequestDetailPage />
              </RequireRole>
            }
          />
          <Route
            path="requests/:id/edit"
            element={
              <RequireRole roles={['USER']}>
                <RequestFormPage />
              </RequireRole>
            }
          />
          <Route
            path="picking"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <PickingListPage />
              </RequireRole>
            }
          />
          <Route
            path="tasks"
            element={
              <RequireRole roles={['WAREHOUSE_WORKER']}>
                <MyTasksPage />
              </RequireRole>
            }
          />
          <Route
            path="tasks/picking/:id"
            element={
              <RequireRole roles={['WAREHOUSE_WORKER']}>
                <PickingTaskPage />
              </RequireRole>
            }
          />
          <Route
            path="tasks/placement/:id"
            element={
              <RequireRole roles={['WAREHOUSE_WORKER']}>
                <PlacementTaskPage />
              </RequireRole>
            }
          />
          <Route
            path="receipts"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <ReceiptListPage />
              </RequireRole>
            }
          />
          <Route
            path="receipts/new"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <ReceiptFormPage />
              </RequireRole>
            }
          />
          <Route
            path="receipts/:id"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <ReceiptDetailPage />
              </RequireRole>
            }
          />
          <Route
            path="receipts/:id/edit"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <ReceiptFormPage />
              </RequireRole>
            }
          />
          <Route
            path="stock"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <StockPage />
              </RequireRole>
            }
          />
          <Route
            path="write-offs"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <WriteOffListPage />
              </RequireRole>
            }
          />
          <Route
            path="write-offs/new"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <WriteOffFormPage />
              </RequireRole>
            }
          />
          <Route
            path="write-offs/:id"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <WriteOffDetailPage />
              </RequireRole>
            }
          />
          <Route
            path="issues"
            element={
              <RequireRole roles={['STOREKEEPER']}>
                <IssueListPage />
              </RequireRole>
            }
          />
          <Route
            path="shortages"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <ShortageListPage />
              </RequireRole>
            }
          />
          <Route
            path="adjustments"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <AdjustmentListPage />
              </RequireRole>
            }
          />
          <Route
            path="adjustments/:id"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <AdjustmentDetailPage />
              </RequireRole>
            }
          />
          <Route
            path="reports"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <ReportsPage />
              </RequireRole>
            }
          />
          <Route
            path="reports/:key"
            element={
              <RequireRole roles={['STOREKEEPER', 'ADMIN']}>
                <ReportsPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/users"
            element={
              <RequireRole roles={['ADMIN']}>
                <UserListPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/catalog"
            element={
              <RequireRole roles={['ADMIN']}>
                <CatalogPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/warehouses"
            element={
              <RequireRole roles={['ADMIN']}>
                <WarehousesPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/rules"
            element={
              <RequireRole roles={['ADMIN']}>
                <RulesPage />
              </RequireRole>
            }
          />
          <Route
            path="audit"
            element={
              <RequireRole roles={['ADMIN']}>
                <AuditPage />
              </RequireRole>
            }
          />
            <Route path="403" element={<Result status="403" title="403" subTitle="Недостаточно прав для этого раздела" />} />
            <Route path="*" element={<Result status="404" title="404" subTitle="Страница не найдена" />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
