import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { dateLocale } from '../i18n';
import { Tabs, Button, Empty, message, Modal, Spin } from 'antd';
import { CalendarOutlined, CloseCircleOutlined, EyeOutlined, FilePdfOutlined, LoadingOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import BookingDetailModal from '../components/BookingDetailModal';
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

type StatusLabelKey = 'myBookings.status.pending' | 'myBookings.status.confirmed' | 'myBookings.status.cancelled';

const statusConfig: Record<string, { labelKey: StatusLabelKey; color: string; className: string }> = {
  PENDING: { labelKey: 'myBookings.status.pending', color: 'warning', className: 'status-pending' },
  CONFIRMED: { labelKey: 'myBookings.status.confirmed', color: 'success', className: 'status-confirmed' },
  CANCELLED: { labelKey: 'myBookings.status.cancelled', color: 'error', className: 'status-cancelled' },
};

const statusOf = (booking: Booking) => (booking.status && statusConfig[booking.status]) || statusConfig.PENDING;

type PaymentLabelKey = 'confirmation.paid' | 'myBookings.refunded' | 'myBookings.unpaid';

const paymentLabelKey = (booking: Booking): PaymentLabelKey => {
  if (booking.paymentStatus === 'PAID') return 'confirmation.paid';
  if (booking.paymentStatus === 'REFUNDED') return 'myBookings.refunded';
  return 'myBookings.unpaid';
};

// Route is wrapped in <AuthGuard> in App.tsx.
const MyBookingsPage: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('all');
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [viewingBooking, setViewingBooking] = useState<Booking | null>(null);

  const [paymentModalVisible, setPaymentModalVisible] = useState(false);
  const [paymentQrData, setPaymentQrData] = useState<PaymentQr | null>(null);
  const [payingBookingId, setPayingBookingId] = useState<number | null>(null);
  const [paymentLoading, setPaymentLoading] = useState(false);

  const fetchBookings = useCallback(() => {
    setLoading(true);
    bookingApi.getMy()
      .then(res => setBookings(res.data || []))
      .catch(err => message.error(getErrorMessage(err, t('myBookings.loadFailed'))))
      .finally(() => setLoading(false));
  }, [t]);

  useEffect(() => { fetchBookings(); }, [fetchBookings]);

  const handleCancel = (bookingId: number) => {
    Modal.confirm({
      title: t('myBookings.cancelTitle'),
      content: t('myBookings.cancelBody'),
      okText: t('myBookings.cancelOk'),
      cancelText: t('common.keep'),
      okType: 'danger',
      onOk: async () => {
        setCancellingId(bookingId);
        try {
          await bookingApi.cancel(bookingId);
          message.success(t('myBookings.cancelled'));
          fetchBookings();
        } catch (err) {
          message.error(getErrorMessage(err, t('myBookings.cancelFailed')));
        } finally {
          setCancellingId(null);
        }
      },
    });
  };

  const handleRequestCancel = (bookingId: number) => {
    Modal.confirm({
      title: t('myBookings.requestCancelTitle'),
      content: t('myBookings.requestCancelBody'),
      okText: t('myBookings.requestCancelOk'),
      cancelText: t('common.keep'),
      okType: 'danger',
      onOk: async () => {
        setCancellingId(bookingId);
        try {
          // The reason is read by Vietnamese-speaking admins, so it stays in Vietnamese.
          await bookingApi.createRequest(bookingId, { type: 'CANCEL', reason: 'Khách yêu cầu hủy và hoàn tiền' });
          message.success(t('myBookings.requestCancelSent'));
        } catch (err) {
          message.error(getErrorMessage(err, t('myBookings.requestCancelFailed')));
        } finally {
          setCancellingId(null);
        }
      },
    });
  };

  const handleDownloadInvoice = async (bookingId: number) => {
    try {
      const pdf = await bookingApi.downloadInvoice(bookingId);
      const url = URL.createObjectURL(pdf);
      const link = document.createElement('a');
      link.href = url;
      link.download = `hoa-don-${bookingId}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      message.error(getErrorMessage(err, t('myBookings.invoiceFailed')));
    }
  };

  const handleOpenPayment = async (bookingId: number) => {
    setPaymentLoading(true);
    try {
      const data = await paymentApi.getPaymentQr(bookingId);
      setPaymentQrData(data);
      setPayingBookingId(bookingId);
      setPaymentModalVisible(true);
    } catch (e) {
      message.error(getErrorMessage(e, t('myBookings.paymentInfoFailed')));
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
          message.success(t('payment.paidAutomatically'));
          setPaymentModalVisible(false);
          fetchBookings(); // Reload list
        }
      } catch (e) {
        console.warn('Payment status poll failed, retrying', e);
      }
    }, PAYMENT_POLL_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [paymentModalVisible, payingBookingId, fetchBookings, t]);

  const handleConfirmPayment = async () => {
    if (!payingBookingId) return;
    setPaymentLoading(true);
    try {
      await paymentApi.confirmPayment(payingBookingId);
      message.success(t('payment.confirmationSent'));
      setPaymentModalVisible(false);
      fetchBookings();
    } catch (e) {
      message.error(getErrorMessage(e, t('payment.confirmFailed')));
    } finally {
      setPaymentLoading(false);
    }
  };

  const filtered = bookings.filter(b => {
    if (activeTab === 'all') return true;
    return b.status === activeTab;
  });

  const BookingCard = ({ booking }: { booking: Booking }) => {
    const config = statusOf(booking);
    const checkIn = booking.checkInDate ? new Date(booking.checkInDate).toLocaleDateString(dateLocale()) : '—';
    const checkOut = booking.checkOutDate ? new Date(booking.checkOutDate).toLocaleDateString(dateLocale()) : '—';
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
              {booking.hotel?.name || t('hotel.fallbackName')}
            </div>
            <div className="booking-card-dates">
              <CalendarOutlined /> {checkIn} → {checkOut}
            </div>
            <div style={{ marginBottom: 8 }}>
              <span className={`status-badge ${config.className}`}>{t(config.labelKey)}</span>
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
                {t(paymentLabelKey(booking))}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8 }}>
              {booking.status === 'PENDING' && booking.paymentStatus !== 'PAID' && (
                <Button
                  size="small"
                  type="primary"
                  onClick={() => handleOpenPayment(bookingId)}
                >
                  {t('myBookings.payQr')}
                </Button>
              )}
              <Button
                size="small"
                icon={<EyeOutlined />}
                onClick={() => setViewingBooking(booking)}
              >
                {t('myBookings.view')}
              </Button>
              {booking.paymentStatus === 'PAID' && (
                <Button size="small" icon={<FilePdfOutlined />} onClick={() => handleDownloadInvoice(bookingId)}>
                  {t('myBookings.invoice')}
                </Button>
              )}
              {canCancelDirectly(booking) && (
                <Button
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  loading={cancellingId === bookingId}
                  onClick={() => handleCancel(bookingId)}
                >
                  {t('myBookings.cancel')}
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
                  {t('myBookings.requestCancel')}
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
              {t('common.backToHome')}
            </button>
            <h1 style={{ color: '#fff', fontSize: 24, fontWeight: 800, marginBottom: 4 }}>
              {t('myBookings.title')}
            </h1>
            <p style={{ color: 'rgba(255,255,255,0.8)', fontSize: 14 }}>
              {t('myBookings.subtitle')}
            </p>
          </div>
        </div>

        <div style={{ background: '#f5f5f5', flex: 1 }}>
          <div className="container" style={{ padding: '24px 24px 48px' }}>
            <Tabs activeKey={activeTab} onChange={setActiveTab} style={{ marginBottom: 20 }}>
              <Tabs.TabPane tab={t('myBookings.tabAll', { count: bookings.length })} key="all" />
              <Tabs.TabPane tab={t('myBookings.tabPending', { count: bookings.filter(b => b.status === 'PENDING').length })} key="PENDING" />
              <Tabs.TabPane tab={t('myBookings.tabConfirmed', { count: bookings.filter(b => b.status === 'CONFIRMED').length })} key="CONFIRMED" />
              <Tabs.TabPane tab={t('myBookings.tabCancelled', { count: bookings.filter(b => b.status === 'CANCELLED').length })} key="CANCELLED" />
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
                      <div style={{ fontSize: 16, fontWeight: 700 }}>{t('myBookings.emptyTitle')}</div>
                      <div style={{ color: '#929292', marginTop: 4 }}>
                        {activeTab === 'all' ? t('myBookings.emptyAll') : t('myBookings.emptyFiltered')}
                      </div>
                    </div>
                  }
                >
                  <Button type="primary" onClick={() => navigate('/')}>
                    {t('myBookings.findHotel')}
                  </Button>
                </Empty>
              </div>
            ) : (
              filtered.map(booking => <BookingCard key={booking.id} booking={booking} />)
            )}
          </div>
        </div>

        <Footer />

        <BookingDetailModal
          booking={viewingBooking}
          statusLabel={viewingBooking ? t(statusOf(viewingBooking).labelKey) : ''}
          paymentLabel={viewingBooking ? t(paymentLabelKey(viewingBooking)) : ''}
          onClose={() => setViewingBooking(null)}
        />

        <Modal
          title={t('myBookings.qrModalTitle')}
          open={paymentModalVisible}
          onCancel={() => setPaymentModalVisible(false)}
          footer={null}
          centered
        >
          {paymentQrData && (
            <div style={{ textAlign: 'center' }}>
              <img src={paymentQrData.qrCodeUrl} alt="QR Code" style={{ maxWidth: '100%', maxHeight: 300, marginBottom: 16, borderRadius: 8, border: '1px solid #e0e0e0' }} />
              <div style={{ textAlign: 'left', background: '#f5f5f5', padding: 16, borderRadius: 8, marginBottom: 16 }}>
                <p><strong>{t('payment.bank')}:</strong> {paymentQrData.bankName}</p>
                <p><strong>{t('payment.accountNumber')}:</strong> {paymentQrData.accountNumber}</p>
                <p><strong>{t('payment.accountName')}:</strong> {paymentQrData.accountName}</p>
                <p><strong>{t('payment.amount')}:</strong> <span style={{ color: '#006ce4', fontWeight: 'bold' }}>{paymentQrData.amount?.toLocaleString('vi-VN')}₫</span></p>
                <p><strong>{t('payment.transferContent')}:</strong> {paymentQrData.transferContent}</p>
              </div>

              <div style={{ background: '#e6f7ff', border: '1px solid #91d5ff', borderRadius: 8, padding: 12, marginBottom: 16, fontSize: 13, color: '#0050b3', display: 'flex', alignItems: 'center', gap: 10, textAlign: 'left' }}>
                <Spin indicator={<LoadingOutlined style={{ fontSize: 18 }} spin />} />
                <span><strong>{t('myBookings.waitingTitle')}</strong> {t('myBookings.waitingBody')}</span>
              </div>

              <Button 
                type="default" 
                size="large" 
                block 
                onClick={handleConfirmPayment}
                loading={paymentLoading}
              >
                {t('payment.manualConfirm')}
              </Button>
            </div>
          )}
        </Modal>
      </div>
    </>
  );
};

export default MyBookingsPage;
