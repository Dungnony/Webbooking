import React, { useState } from 'react';
import {
  UserOutlined,
  DashboardOutlined, 
  BookOutlined,
  BankOutlined,
  ReconciliationOutlined,
  StarOutlined
} from '@ant-design/icons';
import Header from '../components/Header';

// Nhập các component con
import AdminOverview from '../components/admin/AdminOverview';
import AdminUsers from '../components/admin/AdminUsers';
import AdminBookings from '../components/admin/AdminBookings';
import AdminBookingRequests from '../components/admin/AdminBookingRequests';
import AdminHotels from '../components/admin/AdminHotels';
import AdminReviews from '../components/admin/AdminReviews';

type TabKey = 'overview' | 'users' | 'hotels' | 'bookings' | 'requests' | 'reviews';

// Access is restricted to admins by <AuthGuard requiredRole="ADMIN"> in App.tsx.
const AdminDashboard: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabKey>('overview');

  const renderContent = () => {
    switch (activeTab) {
      case 'overview':
        return <AdminOverview />;
      case 'users':
        return <AdminUsers />;
      case 'hotels':
        return <AdminHotels />;
      case 'bookings':
        return <AdminBookings />;
      case 'requests':
        return <AdminBookingRequests />;
      case 'reviews':
        return <AdminReviews />;
      default:
        return <AdminOverview />;
    }
  };

  return (
    <div className="page-wrapper" style={{ background: '#f5f5f5', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header fullWidth />

      <div className="admin-layout" style={{ flex: 1, display: 'flex' }}>
        {/* Sidebar */}
        <aside className="admin-sidebar" style={{ width: 250, background: '#fff', borderRight: '1px solid #e8e8e8', padding: '20px 0' }}>
          <div className="admin-sidebar-logo" style={{ padding: '0 20px', fontSize: 18, fontWeight: 700, color: '#1a1a1a', marginBottom: 20 }}>
            Admin Console
          </div>
          
          <div 
            className={`admin-menu-item ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
            style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: activeTab === 'overview' ? '#e6f7ff' : 'transparent', color: activeTab === 'overview' ? '#1890ff' : '#333', borderRight: activeTab === 'overview' ? '3px solid #1890ff' : 'none' }}
          >
            <DashboardOutlined /> Tổng quan
          </div>

          <div 
            className={`admin-menu-item ${activeTab === 'users' ? 'active' : ''}`}
            onClick={() => setActiveTab('users')}
            style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: activeTab === 'users' ? '#e6f7ff' : 'transparent', color: activeTab === 'users' ? '#1890ff' : '#333', borderRight: activeTab === 'users' ? '3px solid #1890ff' : 'none' }}
          >
            <UserOutlined /> Quản lý Người dùng
          </div>

          <div 
            className={`admin-menu-item ${activeTab === 'hotels' ? 'active' : ''}`}
            onClick={() => setActiveTab('hotels')}
            style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: activeTab === 'hotels' ? '#e6f7ff' : 'transparent', color: activeTab === 'hotels' ? '#1890ff' : '#333', borderRight: activeTab === 'hotels' ? '3px solid #1890ff' : 'none' }}
          >
            <BankOutlined /> Quản lý Khách sạn
          </div>

          <div 
            className={`admin-menu-item ${activeTab === 'bookings' ? 'active' : ''}`}
            onClick={() => setActiveTab('bookings')}
            style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: activeTab === 'bookings' ? '#e6f7ff' : 'transparent', color: activeTab === 'bookings' ? '#1890ff' : '#333', borderRight: activeTab === 'bookings' ? '3px solid #1890ff' : 'none' }}
          >
            <BookOutlined /> Quản lý Đặt phòng
          </div>

          <div 
            className={`admin-menu-item ${activeTab === 'requests' ? 'active' : ''}`}
            onClick={() => setActiveTab('requests')}
            style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: activeTab === 'requests' ? '#e6f7ff' : 'transparent', color: activeTab === 'requests' ? '#1890ff' : '#333', borderRight: activeTab === 'requests' ? '3px solid #1890ff' : 'none' }}
          >
            <ReconciliationOutlined /> Yêu cầu Đặt phòng
          </div>

          <div
            className={`admin-menu-item ${activeTab === 'reviews' ? 'active' : ''}`}
            onClick={() => setActiveTab('reviews')}
            style={{ padding: '12px 20px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, background: activeTab === 'reviews' ? '#e6f7ff' : 'transparent', color: activeTab === 'reviews' ? '#1890ff' : '#333', borderRight: activeTab === 'reviews' ? '3px solid #1890ff' : 'none' }}
          >
            <StarOutlined /> Đánh giá
          </div>

        </aside>

        {/* Content */}
        <main className="admin-content" style={{ flex: 1, padding: '24px', overflowY: 'auto' }}>
          {renderContent()}
        </main>
      </div>
    </div>
  );
};

export default AdminDashboard;
