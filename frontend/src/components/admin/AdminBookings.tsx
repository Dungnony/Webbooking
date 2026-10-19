import React, { useCallback, useEffect, useState } from 'react';
import { Table, Tag, Button, Spin, Empty, message, Modal, Input, Card, Select } from 'antd';
import { CheckOutlined, CloseOutlined, DollarOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { adminApi, BookingStatus } from '../../api';
import { getErrorMessage } from '../../utils/errors';
import type { Booking } from '../../api-generated';
import { cachedFetch, invalidateCachePrefix } from '../../utils/apiCache';

const BOOKINGS_CACHE_TTL_MS = 15_000;

const AdminBookings: React.FC = () => {
  const { t } = useTranslation();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterStatus, setFilterStatus] = useState<BookingStatus | undefined>(undefined);

  // Modal từ chối
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [currentBookingId, setCurrentBookingId] = useState<number | null>(null);

  const loadBookings = useCallback(async () => {
    setLoading(true);
    try {
      const data = await cachedFetch(
        `admin_bookings_${filterStatus || 'all'}`,
        () => adminApi.getBookings(filterStatus),
        BOOKINGS_CACHE_TTL_MS
      );
      setBookings(data || []);
    } catch (err) {
      message.error(t('admin.bookings.loadFailed'));
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [filterStatus, t]);

  useEffect(() => {
    loadBookings();
  }, [loadBookings]);

  const handleApprove = async (id: number) => {
    try {
      await adminApi.approveBooking(id);
      message.success(t('admin.bookings.approved'));
      invalidateCachePrefix('admin_bookings_'); // Xoá cache để lấy data mới
      loadBookings();
    } catch (error) {
      message.error(getErrorMessage(error, t('admin.bookings.approveFailed')));
    }
  };

  const handleMarkPaid = async (id: number) => {
    try {
      await adminApi.markBookingPaid(id);
      message.success(t('admin.bookings.markedPaid'));
      invalidateCachePrefix('admin_bookings_'); // Xoá cache để lấy data mới
      loadBookings();
    } catch (error) {
      message.error(getErrorMessage(error, t('admin.bookings.markPaidFailed')));
    }
  };

  const openRejectModal = (id: number) => {
    setCurrentBookingId(id);
    setRejectReason('');
    setRejectModalOpen(true);
  };

  const submitReject = async () => {
    if (!currentBookingId) return;
    if (!rejectReason.trim()) {
      message.warning(t('admin.common.reasonRequired'));
      return;
    }
    try {
      await adminApi.rejectBooking(currentBookingId, rejectReason);
      message.success(t('admin.bookings.rejected'));
      setRejectModalOpen(false);
      invalidateCachePrefix('admin_bookings_'); // Xoá cache để lấy data mới
      loadBookings();
    } catch (error) {
      message.error(getErrorMessage(error, t('admin.bookings.rejectFailed')));
    }
  };

  const getStatusTag = (status?: string) => {
    switch (status) {
      case 'PENDING': return <Tag color="orange" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.status.pending')}</Tag>;
      case 'CONFIRMED': return <Tag color="green" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.status.confirmed')}</Tag>;
      case 'REJECTED': return <Tag color="red" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.status.rejected')}</Tag>;
      case 'CANCELLED': return <Tag color="default" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.status.cancelled')}</Tag>;
      default: return <Tag style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{status}</Tag>;
    }
  };

  const getPaymentStatusTag = (status?: string) => {
    switch (status) {
      case 'UNPAID': return <Tag color="volcano" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.payment.unpaid')}</Tag>;
      case 'PAID': return <Tag color="cyan" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.payment.paid')}</Tag>;
      case 'REFUNDED': return <Tag color="default" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{t('admin.payment.refunded')}</Tag>;
      default: return <Tag style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{status}</Tag>;
    }
  };

  const columns = [
    { title: <span style={{ color: '#595959', fontWeight: 700 }}>ID</span>, dataIndex: 'id', key: 'id', width: 60 },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>{t('admin.common.guest')}</span>,
      key: 'user',
      render: (_: unknown, record: Booking) => <span style={{ fontWeight: 600, color: '#1a1a1a' }}>{record.user?.username || 'N/A'}</span>
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>{t('admin.common.hotel')}</span>,
      key: 'hotel',
      render: (_: unknown, record: Booking) => <span style={{ color: '#1a1a1a', fontWeight: 500 }}>{record.hotel?.name || 'N/A'}</span>
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>{t('admin.bookings.dates')}</span>,
      key: 'dates',
      render: (_: unknown, record: Booking) => (
        <div style={{ fontSize: 13, color: '#595959' }}>
          <div><span style={{ color: '#8c8c8c' }}>In:</span> <b style={{ color: '#1a1a1a' }}>{record.checkInDate}</b></div>
          <div><span style={{ color: '#8c8c8c' }}>Out:</span> <b style={{ color: '#1a1a1a' }}>{record.checkOutDate}</b></div>
        </div>
      )
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>{t('admin.bookings.total')}</span>,
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      render: (v: number) => <span style={{ color: '#006ce4', fontWeight: 800, fontSize: 15 }}>{v?.toLocaleString('vi-VN')}₫</span>
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>{t('admin.common.status')}</span>,
      key: 'status',
      render: (_: unknown, record: Booking) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-start' }}>
          {getStatusTag(record.status)}
          {getPaymentStatusTag(record.paymentStatus)}
        </div>
      )
    },

    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>{t('admin.common.actions')}</span>,
      key: 'action',
      render: (_: unknown, record: Booking) => {
        const bookingId = record.id;
        if (bookingId === undefined) return null;
        return (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {record.status === 'PENDING' && (
              <>
                <Button size="small" type="primary" style={{ borderRadius: 6, fontWeight: 600, background: '#1890ff' }} icon={<CheckOutlined />} onClick={() => handleApprove(bookingId)}>{t('admin.common.approve')}</Button>
                <Button size="small" danger style={{ borderRadius: 6, fontWeight: 600 }} icon={<CloseOutlined />} onClick={() => openRejectModal(bookingId)}>{t('admin.common.reject')}</Button>
              </>
            )}
            {record.paymentStatus === 'UNPAID' && record.status !== 'CANCELLED' && record.status !== 'REJECTED' && (
              <Button size="small" icon={<DollarOutlined />} style={{ borderRadius: 6, fontWeight: 600, color: '#52c41a', borderColor: '#b7eb8f', background: '#f6ffed' }} onClick={() => handleMarkPaid(bookingId)}>
                {t('admin.bookings.markPaid')}
              </Button>
            )}
          </div>
        );
      }
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>{t('admin.menu.bookings')}</h2>
        <Select
          style={{ width: 150 }}
          placeholder={t('admin.common.filterByStatus')}
          allowClear
          value={filterStatus}
          onChange={setFilterStatus}
        >
          <Select.Option value="PENDING">{t('admin.status.pending')}</Select.Option>
          <Select.Option value="CONFIRMED">{t('admin.status.confirmed')}</Select.Option>
          <Select.Option value="REJECTED">{t('admin.status.rejected')}</Select.Option>
          <Select.Option value="CANCELLED">{t('admin.status.cancelled')}</Select.Option>
        </Select>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
      ) : bookings.length === 0 ? (
        <Empty description={t('admin.bookings.empty')} />
      ) : (
        <Card style={{ padding: 0 }} bodyStyle={{ padding: 0 }}>
          <Table
            dataSource={bookings}
            columns={columns}
            rowKey="id"
            pagination={{ pageSize: 10 }}
            scroll={{ x: 900 }}
          />
        </Card>
      )}

      {/* Modal từ chối booking */}
      <Modal
        title={t('admin.bookings.rejectTitle')}
        open={rejectModalOpen}
        onOk={submitReject}
        onCancel={() => setRejectModalOpen(false)}
        okText={t('admin.common.confirmReject')}
        okButtonProps={{ danger: true }}
      >
        <p>{t('admin.common.reasonPrompt')}</p>
        <Input.TextArea
          rows={4}
          value={rejectReason}
          onChange={e => setRejectReason(e.target.value)}
          placeholder={t('admin.bookings.rejectPlaceholder')}
        />
      </Modal>
    </div>
  );
};

export default AdminBookings;
