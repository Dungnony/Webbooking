import React, { useCallback, useEffect, useState } from 'react';
import { Table, Tag, Button, Spin, Empty, message, Modal, Input, Card, Select } from 'antd';
import { CheckOutlined, CloseOutlined, DollarOutlined } from '@ant-design/icons';
import { adminApi, BookingStatus } from '../../api';
import type { Booking } from '../../api-generated';
import { cachedFetch, invalidateCachePrefix } from '../../utils/apiCache';

const BOOKINGS_CACHE_TTL_MS = 15_000;

const AdminBookings: React.FC = () => {
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
      message.error('Không thể tải danh sách booking');
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [filterStatus]);

  useEffect(() => {
    loadBookings();
  }, [loadBookings]);

  const handleApprove = async (id: number) => {
    try {
      await adminApi.approveBooking(id);
      message.success('Đã duyệt booking thành công');
      invalidateCachePrefix('admin_bookings_'); // Xoá cache để lấy data mới
      loadBookings();
    } catch (error) {
      message.error('Lỗi khi duyệt booking');
    }
  };

  const handleMarkPaid = async (id: number) => {
    try {
      await adminApi.markBookingPaid(id);
      message.success('Đã đánh dấu thanh toán thành công');
      invalidateCachePrefix('admin_bookings_'); // Xoá cache để lấy data mới
      loadBookings();
    } catch (error) {
      message.error('Lỗi khi cập nhật thanh toán');
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
      message.warning('Vui lòng nhập lý do từ chối');
      return;
    }
    try {
      await adminApi.rejectBooking(currentBookingId, rejectReason);
      message.success('Đã từ chối booking');
      setRejectModalOpen(false);
      invalidateCachePrefix('admin_bookings_'); // Xoá cache để lấy data mới
      loadBookings();
    } catch (error) {
      message.error('Lỗi khi từ chối booking');
    }
  };

  const getStatusTag = (status?: string) => {
    switch (status) {
      case 'PENDING': return <Tag color="orange" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Chờ duyệt</Tag>;
      case 'CONFIRMED': return <Tag color="green" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Đã duyệt</Tag>;
      case 'REJECTED': return <Tag color="red" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Từ chối</Tag>;
      case 'CANCELLED': return <Tag color="default" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Đã hủy</Tag>;
      default: return <Tag style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{status}</Tag>;
    }
  };

  const getPaymentStatusTag = (status?: string) => {
    switch (status) {
      case 'UNPAID': return <Tag color="volcano" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Chưa thanh toán</Tag>;
      case 'PAID': return <Tag color="cyan" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Đã thanh toán</Tag>;
      case 'REFUNDED': return <Tag color="default" style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>Đã hoàn tiền</Tag>;
      default: return <Tag style={{ borderRadius: 12, padding: '2px 10px', fontWeight: 600 }}>{status}</Tag>;
    }
  };

  const columns = [
    { title: <span style={{ color: '#595959', fontWeight: 700 }}>ID</span>, dataIndex: 'id', key: 'id', width: 60 },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>Khách hàng</span>,
      key: 'user',
      render: (_: unknown, record: Booking) => <span style={{ fontWeight: 600, color: '#1a1a1a' }}>{record.user?.username || 'N/A'}</span>
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>Khách sạn</span>,
      key: 'hotel',
      render: (_: unknown, record: Booking) => <span style={{ color: '#1a1a1a', fontWeight: 500 }}>{record.hotel?.name || 'N/A'}</span>
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>Thời gian</span>,
      key: 'dates',
      render: (_: unknown, record: Booking) => (
        <div style={{ fontSize: 13, color: '#595959' }}>
          <div><span style={{ color: '#8c8c8c' }}>In:</span> <b style={{ color: '#1a1a1a' }}>{record.checkInDate}</b></div>
          <div><span style={{ color: '#8c8c8c' }}>Out:</span> <b style={{ color: '#1a1a1a' }}>{record.checkOutDate}</b></div>
        </div>
      )
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>Tổng tiền</span>,
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      render: (v: number) => <span style={{ color: '#006ce4', fontWeight: 800, fontSize: 15 }}>{v?.toLocaleString('vi-VN')}₫</span>
    },
    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>Trạng thái</span>,
      key: 'status',
      render: (_: unknown, record: Booking) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-start' }}>
          {getStatusTag(record.status)}
          {getPaymentStatusTag(record.paymentStatus)}
        </div>
      )
    },

    {
      title: <span style={{ color: '#595959', fontWeight: 700 }}>Hành động</span>,
      key: 'action',
      render: (_: unknown, record: Booking) => {
        const bookingId = record.id;
        if (bookingId === undefined) return null;
        return (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {record.status === 'PENDING' && (
              <>
                <Button size="small" type="primary" style={{ borderRadius: 6, fontWeight: 600, background: '#1890ff' }} icon={<CheckOutlined />} onClick={() => handleApprove(bookingId)}>Duyệt</Button>
                <Button size="small" danger style={{ borderRadius: 6, fontWeight: 600 }} icon={<CloseOutlined />} onClick={() => openRejectModal(bookingId)}>Từ chối</Button>
              </>
            )}
            {record.paymentStatus === 'UNPAID' && record.status !== 'CANCELLED' && record.status !== 'REJECTED' && (
              <Button size="small" icon={<DollarOutlined />} style={{ borderRadius: 6, fontWeight: 600, color: '#52c41a', borderColor: '#b7eb8f', background: '#f6ffed' }} onClick={() => handleMarkPaid(bookingId)}>
                Đã thu tiền
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
        <h2 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: '#1a1a1a' }}>Quản lý Đặt phòng</h2>
        <Select
          style={{ width: 150 }}
          placeholder="Lọc theo trạng thái"
          allowClear
          value={filterStatus}
          onChange={setFilterStatus}
        >
          <Select.Option value="PENDING">Chờ duyệt</Select.Option>
          <Select.Option value="CONFIRMED">Đã duyệt</Select.Option>
          <Select.Option value="REJECTED">Từ chối</Select.Option>
          <Select.Option value="CANCELLED">Đã hủy</Select.Option>
        </Select>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
      ) : bookings.length === 0 ? (
        <Empty description="Không có booking nào" />
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
        title="Từ chối Đặt phòng"
        open={rejectModalOpen}
        onOk={submitReject}
        onCancel={() => setRejectModalOpen(false)}
        okText="Xác nhận từ chối"
        okButtonProps={{ danger: true }}
      >
        <p>Vui lòng nhập lý do từ chối để thông báo cho khách hàng:</p>
        <Input.TextArea
          rows={4}
          value={rejectReason}
          onChange={e => setRejectReason(e.target.value)}
          placeholder="Khách sạn đã hết phòng trống..."
        />
      </Modal>
    </div>
  );
};

export default AdminBookings;
