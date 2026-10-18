import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Tabs, Button, Empty, message, Modal, Spin } from 'antd';
import { CalendarOutlined, CloseCircleOutlined, EyeOutlined, LoadingOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { getHotelImage } from '../utils/imageUtils';
import { bookingApi, paymentApi, PaymentQr } from '../api';
import type { Booking } from '../api-generated';
import { getErrorMessage } from '../utils/errors';

const PAYMENT_POLL_INTERVAL_MS = 3000;

const ACTIVE_STATUSES: ReadonlyArray<Booking['status']> = ['PENDING', 'CONFIRMED'];

/** Mirrors the backend rule: cancelling is only possible before the check-in date. */
const isBeforeCheckIn = (booking: Booking) => {
  if (!booking.checkInDate) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(`${booking.checkInDate}T00:00:00`) > today;
};

// Unpaid bookings are cancelled directly; paid ones need an admin to approve and refund.
const canCancelDirectly = (booking: Booking) =>
  ACTIVE_STATUSES.includes(booking.status) && booking.paymentStatus !== 'PAID' && isBeforeCheckIn(booking);

const canRequestCancel = (booking: Booking) =>
  ACTIVE_STATUSES.includes(booking.status) && booking.paymentStatus === 'PAID' && isBeforeCheckIn(booking);

const statusConfig: Record<string, { label: string; color: string; className: string }> = {
  PENDING: { label: 'Chờ xác nhận', color: 'warning', className: 'status-pending' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'success', className: 'status-confirmed' },
  CANCELLED: { label: 'Đã hủy', color: 'error', className: 'status-cancelled' },
};

// Route is wrapped in <AuthGuard> in App.tsx.
const MyBookingsPage: React.FC = () => {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('all');
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  const [paymentModalVisible, setPaymentModalVisible] = useState(false);
  const [paymentQrData, setPaymentQrData] = useState<PaymentQr | null>(null);
  const [payingBookingId, setPayingBookingId] = useState<number | null>(null);
  const [paymentLoading, setPaymentLoading] = useState(false);

  const fetchBookings = useCallback(() => {
    setLoading(true);
    bookingApi.getMy()
      .then(res => setBookings(res.data || []))
      .catch(err => message.error(getErrorMessage(err, 'Không thể tải lịch sử đặt phòng!')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchBookings(); }, [fetchBookings]);

  const handleCancel = (bookingId: number) => {
    Modal.confirm({
      title: 'Hủy đặt phòng?',
      content: 'Bạn có chắc muốn hủy đặt phòng này không? Thao tác này không thể hoàn tác.',
      okText: 'Xác nhận hủy',
      cancelText: 'Giữ lại',
      okType: 'danger',
      onOk: async () => {
        setCancellingId(bookingId);
        try {
          await bookingApi.cancel(bookingId);
          message.success('Đã hủy đặt phòng thành công!');
          fetchBookings();
        } catch (err) {
          message.error(getErrorMessage(err, 'Hủy đặt phòng thất bại!'));
        } finally {
          setCancellingId(null);
        }
      },
    });
  };

  const handleRequestCancel = (bookingId: number) => {
    Modal.confirm({
      title: 'Gửi yêu cầu hủy?',
      content: 'Booking đã thanh toán. Admin sẽ xem xét yêu cầu hủy và liên hệ hoàn tiền cho bạn.',
      okText: 'Gửi yêu cầu',
      cancelText: 'Giữ lại',
      okType: 'danger',
      onOk: async () => {
        setCancellingId(bookingId);
        try {
          await bookingApi.createRequest(bookingId, { type: 'CANCEL', reason: 'Khách yêu cầu hủy và hoàn tiền' });
          message.success('Đã gửi yêu cầu hủy. Vui lòng chờ admin xử lý.');
        } catch (err) {
          message.error(getErrorMessage(err, 'Gửi yêu cầu hủy thất bại!'));
        } finally {
          setCancellingId(null);
        }
      },
    });
  };

  const handleOpenPayment = async (bookingId: number) => {
    setPaymentLoading(true);
    try {
      const data = await paymentApi.getPaymentQr(bookingId);
      setPaymentQrData(data);
      setPayingBookingId(bookingId);
      setPaymentModalVisible(true);
    } catch (e) {
      message.error(getErrorMessage(e, 'Không thể tải thông tin thanh toán. Booking phải ở trạng thái Chờ xác nhận mới thanh toán được!'));
    } finally {
      setPaymentLoading(false);
    }
  };

  // Tự động kiểm tra trạng thái thanh toán khi mở Modal
  useEffect(() => {
    if (!paymentModalVisible || !payingBookingId) return;

    const interval = setInterval(async () => {
      try {
        const res = await bookingApi.getMy();
        const currentBooking = res.data?.find(b => b.id === payingBookingId);
        if (currentBooking && currentBooking.paymentStatus === 'PAID') {
          clearInterval(interval);
          message.success('Thanh toán thành công! Hệ thống đã tự động xác nhận.');
          setPaymentModalVisible(false);
          fetchBookings(); // Reload list
        }
      } catch (e) {
        console.warn('Payment status poll failed, retrying', e);
      }
    }, PAYMENT_POLL_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [paymentModalVisible, payingBookingId, fetchBookings]);

  const handleConfirmPayment = async () => {
    if (!payingBookingId) return;
    setPaymentLoading(true);
    try {
      await paymentApi.confirmPayment(payingBookingId);
      message.success('Đã gửi thông báo xác nhận thanh toán tới Admin!');
      setPaymentModalVisible(false);
      fetchBookings();
    } catch (e) {
      message.error(getErrorMessage(e, 'Xác nhận thanh toán thất bại'));
    } finally {
      setPaymentLoading(false);
    }
  };

  const filtered = bookings.filter(b => {
    if (activeTab === 'all') return true;
    return b.status === activeTab;
  });

  const BookingCard = ({ booking }: { booking: Booking }) => {
    const config = (booking.status && statusConfig[booking.status]) || statusConfig.PENDING;
    const checkIn = booking.checkInDate ? new Date(booking.checkInDate).toLocaleDateString('vi-VN') : '—';
    const checkOut = booking.checkOutDate ? new Date(booking.checkOutDate).toLocaleDateString('vi-VN') : '—';
    const bookingId = booking.id;
    if (bookingId === undefined) return null;

    return (
      <div className="booking-card">
        <div className="booking-card-img">
          {(() => {
            const imgUrl = getHotelImage(booking.hotel);
            return imgUrl ? (
              <img
                src={imgUrl}
                alt={booking.hotel?.name || 'Hotel'}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                referrerPolicy="no-referrer"
                onError={(e) => {
                  const target = e.target as HTMLImageElement;
                  target.style.display = 'none';
                  if (target.nextElementSibling) {
                    (target.nextElementSibling as HTMLElement).style.display = 'flex';
                  }
                }}
              />
            ) : null;
          })()}
          <div
            style={{ display: getHotelImage(booking.hotel) ? 'none' : 'flex', width: '100%', height: '100%', alignItems: 'center', justifyContent: 'center', fontSize: 40, background: 'linear-gradient(135deg, #e8f0fe, #f0f0f0)' }}
          >
            🏨
          </div>
        </div>

        <div className="booking-card-body">
          <div className="booking-card-info">
            <div className="booking-card-name">
              {booking.hotel?.name || 'Khách sạn'}
            </div>
            <div className="booking-card-dates">
              <CalendarOutlined /> {checkIn} → {checkOut}
            </div>
            <div style={{ marginBottom: 8 }}>
              <span className={`status-badge ${config.className}`}>{config.label}</span>
            </div>
            <div style={{ fontSize: 13, color: '#595959' }}>
              📍 {booking.hotel?.city || booking.hotel?.address || 'Việt Nam'}
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', justifyContent: 'space-between', gap: 12 }}>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: 20, fontWeight: 800 }}>
                {(booking.totalPrice || 0).toLocaleString('vi-VN')}₫
              </div>
              <div style={{ fontSize: 12, color: '#595959' }}>
                {booking.paymentStatus === 'PAID' ? '✅ Đã thanh toán' : '⏳ Thanh toán tại chỗ'}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8 }}>
              {booking.status === 'PENDING' && booking.paymentStatus !== 'PAID' && (
                <Button
                  size="small"
                  type="primary"
                  onClick={() => handleOpenPayment(bookingId)}
                >
                  Thanh toán (QR)
                </Button>
              )}
              <Button
                size="small"
                icon={<EyeOutlined />}
                onClick={() => navigate(`/my-bookings/${bookingId}`)}
              >
                Xem
              </Button>
              {canCancelDirectly(booking) && (
                <Button
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  loading={cancellingId === bookingId}
                  onClick={() => handleCancel(bookingId)}
                >
                  Hủy
                </Button>
              )}
              {canRequestCancel(booking) && (
                <Button
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  loading={cancellingId === bookingId}
                  onClick={() => handleRequestCancel(bookingId)}
                >
                  Yêu cầu hủy
                </Button>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  };

  return (
    <>
      <div className="page-wrapper">
        <Header />

        <div style={{ background: '#003b95', padding: '24px 0' }}>
          <div className="container">
            <button
              className="bk-btn-back"
              onClick={() => navigate('/')}
            >
              ← Quay lại trang chủ
            </button>
            <h1 style={{ color: '#fff', fontSize: 24, fontWeight: 800, marginBottom: 4 }}>
              Đặt chỗ của tôi
            </h1>
            <p style={{ color: 'rgba(255,255,255,0.8)', fontSize: 14 }}>
              Quản lý tất cả các đặt phòng của bạn
            </p>
          </div>
        </div>

        <div style={{ background: '#f5f5f5', flex: 1 }}>
          <div className="container" style={{ padding: '24px 24px 48px' }}>
            <Tabs activeKey={activeTab} onChange={setActiveTab} style={{ marginBottom: 20 }}>
              <Tabs.TabPane tab={`Tất cả (${bookings.length})`} key="all" />
              <Tabs.TabPane tab={`Chờ xác nhận (${bookings.filter(b => b.status === 'PENDING').length})`} key="PENDING" />
              <Tabs.TabPane tab={`Đã xác nhận (${bookings.filter(b => b.status === 'CONFIRMED').length})`} key="CONFIRMED" />
              <Tabs.TabPane tab={`Đã hủy (${bookings.filter(b => b.status === 'CANCELLED').length})`} key="CANCELLED" />
            </Tabs>

            {loading ? (
              [...Array(3)].map((_, i) => (
                <div key={i} style={{ background: '#fff', borderRadius: 12, marginBottom: 12, overflow: 'hidden', display: 'flex', boxShadow: '0 2px 12px rgba(0,0,0,0.07)' }}>
                  {/* Image placeholder */}
                  <div style={{ width: 160, minHeight: 120, flexShrink: 0, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                  <div style={{ flex: 1, padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <div style={{ height: 18, width: '60%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                    <div style={{ height: 13, width: '40%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                    <div style={{ height: 13, width: '30%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                  </div>
                </div>
              ))
            ) : filtered.length === 0 ? (
              <div style={{ background: '#fff', borderRadius: 8, padding: '48px 24px' }}>
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    <div>
                      <div style={{ fontSize: 16, fontWeight: 700 }}>Chưa có đặt phòng nào</div>
                      <div style={{ color: '#929292', marginTop: 4 }}>
                        {activeTab === 'all' ? 'Bạn chưa đặt phòng nào.' : 'Không có đặt phòng nào trong trạng thái này.'}
                      </div>
                    </div>
                  }
                >
                  <Button type="primary" onClick={() => navigate('/')}>
                    Tìm khách sạn ngay
                  </Button>
                </Empty>
              </div>
            ) : (
              filtered.map(booking => <BookingCard key={booking.id} booking={booking} />)
            )}
          </div>
        </div>

        <Footer />

        <Modal
          title="Thanh toán qua QR Code"
          open={paymentModalVisible}
          onCancel={() => setPaymentModalVisible(false)}
          footer={null}
          centered
        >
          {paymentQrData && (
            <div style={{ textAlign: 'center' }}>
              <img src={paymentQrData.qrCodeUrl} alt="QR Code" style={{ maxWidth: '100%', maxHeight: 300, marginBottom: 16, borderRadius: 8, border: '1px solid #e0e0e0' }} />
              <div style={{ textAlign: 'left', background: '#f5f5f5', padding: 16, borderRadius: 8, marginBottom: 16 }}>
                <p><strong>Ngân hàng:</strong> {paymentQrData.bankName}</p>
                <p><strong>Số tài khoản:</strong> {paymentQrData.accountNumber}</p>
                <p><strong>Chủ tài khoản:</strong> {paymentQrData.accountName}</p>
                <p><strong>Số tiền:</strong> <span style={{ color: '#006ce4', fontWeight: 'bold' }}>{paymentQrData.amount?.toLocaleString('vi-VN')}₫</span></p>
                <p><strong>Nội dung:</strong> {paymentQrData.transferContent}</p>
              </div>

              <div style={{ background: '#e6f7ff', border: '1px solid #91d5ff', borderRadius: 8, padding: 12, marginBottom: 16, fontSize: 13, color: '#0050b3', display: 'flex', alignItems: 'center', gap: 10, textAlign: 'left' }}>
                <Spin indicator={<LoadingOutlined style={{ fontSize: 18 }} spin />} />
                <span><strong>Hệ thống đang chờ thanh toán...</strong> Đơn phòng sẽ tự động xác nhận trong 1-3 phút sau khi bạn chuyển khoản thành công.</span>
              </div>

              <Button 
                type="default" 
                size="large" 
                block 
                onClick={handleConfirmPayment}
                loading={paymentLoading}
              >
                Xác nhận thủ công (Nếu lỗi)
              </Button>
            </div>
          )}
        </Modal>
      </div>
    </>
  );
};

export default MyBookingsPage;
