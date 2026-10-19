import React, { Suspense, lazy, useState } from 'react';
import { Spin } from 'antd';
import {
  UserOutlined,
  DashboardOutlined,
  BookOutlined,
  BankOutlined,
  ReconciliationOutlined,
  StarOutlined,
  CustomerServiceOutlined
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import Header from '../components/Header';

// Each tab is its own chunk: the charts library alone is several hundred KB and only the overview needs it.
const AdminOverview = lazy(() => import('../components/admin/AdminOverview'));
const AdminUsers = lazy(() => import('../components/admin/AdminUsers'));
const AdminBookings = lazy(() => import('../components/admin/AdminBookings'));
const AdminBookingRequests = lazy(() => import('../components/admin/AdminBookingRequests'));
const AdminHotels = lazy(() => import('../components/admin/AdminHotels'));
const AdminReviews = lazy(() => import('../components/admin/AdminReviews'));
const AdminSupportChat = lazy(() => import('../components/admin/AdminSupportChat'));

type TabKey = 'overview' | 'users' | 'hotels' | 'bookings' | 'requests' | 'reviews' | 'chat';

const MENU: ReadonlyArray<{ key: TabKey; icon: React.ReactNode }> = [
  { key: 'overview', icon: <DashboardOutlined /> },
  { key: 'users', icon: <UserOutlined /> },
  { key: 'hotels', icon: <BankOutlined /> },
  { key: 'bookings', icon: <BookOutlined /> },
  { key: 'requests', icon: <ReconciliationOutlined /> },
  { key: 'reviews', icon: <StarOutlined /> },
  { key: 'chat', icon: <CustomerServiceOutlined /> },
];

const TAB_CONTENT: Record<TabKey, React.LazyExoticComponent<React.FC>> = {
  overview: AdminOverview,
  users: AdminUsers,
  hotels: AdminHotels,
  bookings: AdminBookings,
  requests: AdminBookingRequests,
  reviews: AdminReviews,
  chat: AdminSupportChat,
};

// Access is restricted to admins by <AuthGuard requiredRole="ADMIN"> in App.tsx.
const AdminDashboard: React.FC = () => {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<TabKey>('overview');
  const Content = TAB_CONTENT[activeTab];

  return (
    <div className="page-wrapper" style={{ background: '#f5f5f5', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header fullWidth />

      <div className="admin-layout" style={{ flex: 1, display: 'flex' }}>
        {/* Sidebar */}
        <aside className="admin-sidebar" style={{ width: 250, background: '#fff', borderRight: '1px solid #e8e8e8', padding: '20px 0' }}>
          <div className="admin-sidebar-logo" style={{ padding: '0 20px', fontSize: 18, fontWeight: 700, color: '#1a1a1a', marginBottom: 20 }}>
            Admin Console
          </div>

          {MENU.map(item => {
            const active = activeTab === item.key;
            return (
              <div
                key={item.key}
                className={`admin-menu-item ${active ? 'active' : ''}`}
                onClick={() => setActiveTab(item.key)}
                style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: active ? '#e6f7ff' : 'transparent', color: active ? '#1890ff' : '#333', borderRight: active ? '3px solid #1890ff' : 'none' }}
              >
                {item.icon} {t(`admin.menu.${item.key}`)}
              </div>
            );
          })}
        </aside>

        {/* Content */}
        <main className="admin-content" style={{ flex: 1, padding: '24px', overflowY: 'auto' }}>
          <Suspense fallback={<div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>}>
            <Content />
          </Suspense>
        </main>
      </div>
    </div>
  );
};

export default AdminDashboard;
