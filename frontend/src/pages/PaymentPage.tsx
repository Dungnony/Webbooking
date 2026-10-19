import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button, Spin, message, Divider } from 'antd';
import { CheckCircleOutlined, CopyOutlined, LoadingOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { paymentApi, bookingApi, PaymentQr } from '../api';
import type { Booking } from '../api-generated';
import type { BookingFlowState } from '../types/bookingFlow';
import { getErrorMessage } from '../utils/errors';

const PAYMENT_POLL_INTERVAL_MS = 3000;
// Must match BookingExpiryService.PAYMENT_WINDOW on the backend.
const PAYMENT_WINDOW_MINUTES = 30;

const PaymentPage: React.FC = () => {
  const state = useLocation().state as BookingFlowState | null;
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [qrData, setQrData] = useState<PaymentQr | null>(null);
  const [loadingQr, setLoadingQr] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [currentBooking, setCurrentBooking] = useState<Booking | undefined>(state?.booking);

  const booking = currentBooking;
  const bookingId = booking?.id;
  const hotel = state?.hotel;
  const checkIn = state?.checkIn;
  const checkOut = state?.checkOut;
  const nights = state?.nights;
  const totalPrice = state?.totalPrice;

  // Tải QR thanh toán
  useEffect(() => {
    if (bookingId === undefined) {
      navigate('/');
      return;
    }
    setLoadingQr(true);
    paymentApi.getPaymentQr(bookingId)
      .then(data => setQrData(data))
      .catch(e => {
        message.error(t('payment.qrLoadFailed', { reason: getErrorMessage(e, t('payment.unknownError')) }));
      })
      .finally(() => setLoadingQr(false));
  }, [bookingId, navigate, t]);

  // Tự động kiểm tra thanh toán (polling 3s)
  useEffect(() => {
    if (bookingId === undefined) return;

    const interval = setInterval(async () => {
      try {
        const res = await bookingApi.getMy();
        const updated = res.data?.find(b => b.id === bookingId);
        if (!updated) return;

        // Cập nhật trạng thái booking
        setCurrentBooking(updated);

        // Nếu đã thanh toán → chuyển trang xác nhận
        if (updated.paymentStatus === 'PAID') {
          clearInterval(interval);
          message.success(t('payment.paidAutomatically'));
          navigate('/booking/confirmation', { state: { ...state, booking: updated } });
        }
      } catch (e) {
        console.warn('Payment status poll failed, retrying', e);
      }
    }, PAYMENT_POLL_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [bookingId, navigate, state, t]);

  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => message.success(t('payment.copied', { label })));
  };

  const handleConfirmPayment = async () => {
    if (bookingId === undefined) return;
    setConfirming(true);
    try {
      await paymentApi.confirmPayment(bookingId);
      message.success(t('payment.confirmationSent'));
      navigate('/booking/confirmation', { state });
    } catch (e) {
      message.error(getErrorMessage(e, t('payment.confirmFailed')));
    } finally {
      setConfirming(false);
    }
  };

  if (!booking) return null;

  const isPending = booking.status === 'PENDING';
  const isConfirmed = booking.status === 'CONFIRMED';

  return (
    <div className="page-wrapper" style={{ background: '#f5f5f5', minHeight: '100vh' }}>
      <Header />

      <div style={{ maxWidth: 720, margin: '0 auto', padding: '24px 24px 48px' }}>
        {/* Steps indicator */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 24, flexWrap: 'wrap' }}>
          {[t('booking.steps.guest'), t('booking.steps.payment'), t('booking.steps.confirmation')].map((step, i) => (
            <div key={step} style={{
              padding: '6px 16px',
              borderRadius: 20,
              fontSize: 13,
              fontWeight: 600,
              background: i === 1 ? '#003b95' : '#fff',
              color: i === 1 ? '#fff' : '#929292',
              border: '1px solid #e7e7e7',
            }}>
              {step}
            </div>
          ))}
        </div>

        {/* Header card */}
        <div style={{
          background: '#fff',
          border: '1px solid #e7e7e7',
          borderRadius: 12,
          padding: '24px 28px',
          marginBottom: 20,
          borderTop: '4px solid #006ce4',
        }}>
          <h2 style={{ fontSize: 20, fontWeight: 800, marginBottom: 4 }}>{t('payment.title')}</h2>
          <p style={{ color: '#595959', fontSize: 14, margin: 0 }}>
            {t('payment.intro')} <strong>{hotel?.name}</strong>
          </p>
        </div>

        {/* Thông báo đặt phòng */}
        <div style={{
          background: isPending ? '#e6f4ff' : '#f6ffed',
          border: `1px solid ${isPending ? '#91caff' : '#b7eb8f'}`,
          borderRadius: 10,
          padding: '16px 20px',
          marginBottom: 20,
          display: 'flex',
          alignItems: 'flex-start',
          gap: 12,
        }}>
          {isPending ? (
            <LoadingOutlined style={{ fontSize: 22, color: '#1677ff', marginTop: 2 }} />
          ) : (
            <CheckCircleOutlined style={{ fontSize: 22, color: '#52c41a', marginTop: 2 }} />
          )}
          <div>
            <div style={{ fontWeight: 700, color: isPending ? '#003eb3' : '#135200', fontSize: 15, marginBottom: 4 }}>
              {isPending ? t('payment.awaitingPayment') : t('payment.bookingConfirmed')}
            </div>
            <div style={{ color: isPending ? '#0958d9' : '#237804', fontSize: 13 }}>
              {t('payment.instructions')}
            </div>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          {/* QR Code */}
          <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 12, padding: 24, textAlign: 'center' }}>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 16, color: '#1a1a1a' }}>{t('payment.qrTitle')}</div>
            {loadingQr ? (
              <div style={{ padding: '40px 0' }}>
                <Spin indicator={<LoadingOutlined style={{ fontSize: 36 }} spin />} />
                <div style={{ marginTop: 12, color: '#595959', fontSize: 13 }}>{t('payment.qrLoading')}</div>
              </div>
            ) : qrData?.qrCodeUrl ? (
              <>
                <img
                  src={qrData.qrCodeUrl}
                  alt={t('payment.qrTitle')}
                  style={{ width: '100%', maxWidth: 220, borderRadius: 8, border: '1px solid #e0e0e0' }}
                />
                <div style={{ marginTop: 12, fontSize: 12, color: '#008234', fontWeight: 600 }}>
                  {t('payment.anyBankApp')}
                </div>
              </>
            ) : (
              <div style={{ padding: '20px 0', color: '#ff4d4f' }}>{t('payment.qrUnavailable')}</div>
            )}
          </div>

          {/* Transfer info */}
          <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 12, padding: 24 }}>
            <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 16, color: '#1a1a1a' }}>{t('payment.transferInfo')}</div>

            {loadingQr ? (
              <Spin size="small" />
            ) : qrData ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {[
                  { label: t('payment.bank'), value: qrData.bankName },
                  { label: t('payment.accountNumber'), value: qrData.accountNumber, copyable: true },
                  { label: t('payment.accountName'), value: qrData.accountName },
                  { label: t('payment.transferContent'), value: qrData.transferContent, copyable: true },
                ].map(({ label, value, copyable }) => (
                  <div key={label}>
                    <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>{label}</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: '#1a1a1a', wordBreak: 'break-all' }}>{value}</span>
                      {copyable && (
                        <Button
                          size="small"
                          type="text"
                          icon={<CopyOutlined />}
                          onClick={() => handleCopy(value, label)}
                          style={{ color: '#006ce4', flexShrink: 0 }}
                        />
                      )}
                    </div>
                  </div>
                ))}

                <Divider style={{ margin: '4px 0' }} />
                <div>
                  <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>{t('payment.amount')}</div>
                  <div style={{ fontSize: 22, fontWeight: 800, color: '#006ce4' }}>
                    {(qrData.amount || totalPrice || 0).toLocaleString('vi-VN')}₫
                  </div>
                </div>
              </div>
            ) : null}
          </div>
        </div>

        {/* Booking summary */}
        <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 12, padding: 20, marginTop: 16 }}>
          <div style={{ fontWeight: 700, marginBottom: 12 }}>{t('payment.summary')}</div>
          {[
            [t('booking.bookingCode'), `BK${String(booking.id).padStart(6, '0')}`],
            [t('payment.status'), isPending ? t('payment.statusPending') : isConfirmed ? t('payment.statusConfirmed') : booking.status],
            [t('hotel.breadcrumbHotels'), hotel?.name],
            [t('search.checkIn'), checkIn],
            [t('search.checkOut'), checkOut],
            [t('booking.nights'), t('booking.nightsValue', { count: Math.max(1, nights || 1) })],
          ].map(([label, value]) => (
            <div key={label} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #f5f5f5', fontSize: 13 }}>
              <span style={{ color: '#595959' }}>{label}</span>
              <span style={{ fontWeight: 600 }}>{value}</span>
            </div>
          ))}
        </div>

        {/* Notice */}
        <div style={{ background: '#e6f7ff', border: '1px solid #91d5ff', borderRadius: 8, padding: 14, marginTop: 16, fontSize: 13, color: '#0050b3', display: 'flex', alignItems: 'center', gap: 10 }}>
          <Spin indicator={<LoadingOutlined style={{ fontSize: 18 }} spin />} />
          <span><strong>{t('payment.checkingTitle')}</strong> {t('payment.checkingBody', { minutes: PAYMENT_WINDOW_MINUTES })}</span>
        </div>

        {/* Action buttons */}
        <div style={{ display: 'flex', gap: 12, marginTop: 20 }}>
          <Button
            size="large"
            style={{ flex: 1, height: 52 }}
            onClick={() => navigate('/my-bookings')}
          >
            {t('payment.payLater')}
          </Button>
          <Button
            type="primary"
            size="large"
            icon={<CheckCircleOutlined />}
            loading={confirming}
            style={{ flex: 2, height: 52, fontSize: 15, fontWeight: 700, background: '#006ce4' }}
            onClick={handleConfirmPayment}
          >
            {t('payment.manualConfirm')}
          </Button>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default PaymentPage;
