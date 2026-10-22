import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Segmented, Spin, message, Divider } from 'antd';
import { BankOutlined, CheckCircleOutlined, CopyOutlined, LoadingOutlined, QrcodeOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import BankAppPicker from '../components/BankAppPicker';
import { paymentApi, bookingApi, PaymentQr } from '../api';
import type { Booking } from '../api-generated';
import type { BookingFlowState } from '../types/bookingFlow';
import { getErrorMessage } from '../utils/errors';
import { buildBankAppLink, detectMobilePlatform, type BankApp } from '../utils/bankApps';

const PAYMENT_POLL_INTERVAL_MS = 3000;
// Must match BookingExpiryService.PAYMENT_WINDOW on the backend.
const PAYMENT_WINDOW_MINUTES = 30;
const DAY_MS = 24 * 60 * 60 * 1000;

type PayMethod = 'qr' | 'bankApp';

const nightsBetween = (from?: string, to?: string) =>
  from && to ? Math.round((Date.parse(to) - Date.parse(from)) / DAY_MS) : undefined;

const PaymentPage: React.FC = () => {
  const location = useLocation();
  const state = location.state as BookingFlowState | null;
  // Quay lại từ app ngân hàng (tham số url của deeplink): không có router state, chỉ có ?bookingId=
  const queryBookingId = Number(new URLSearchParams(location.search).get('bookingId')) || undefined;
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [qrData, setQrData] = useState<PaymentQr | null>(null);
  const [loadingQr, setLoadingQr] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [currentBooking, setCurrentBooking] = useState<Booking | undefined>(state?.booking);
  const [method, setMethod] = useState<PayMethod>('qr');
  const [pickerOpen, setPickerOpen] = useState(false);
  const platform = useMemo(() => detectMobilePlatform(navigator.userAgent), []);

  const booking = currentBooking;
  const bookingId = booking?.id ?? queryBookingId;
  const hotel = state?.hotel ?? booking?.hotel;
  const checkIn = state?.checkIn ?? booking?.checkInDate;
  const checkOut = state?.checkOut ?? booking?.checkOutDate;
  const nights = state?.nights ?? nightsBetween(booking?.checkInDate, booking?.checkOutDate);
  const totalPrice = state?.totalPrice ?? booking?.totalPrice;

  // Mở lại trang bằng link ?bookingId= → tải booking của khách
  useEffect(() => {
    if (currentBooking || queryBookingId === undefined) return;
    bookingApi.getMy()
      .then(res => {
        const found = res.data?.find(b => b.id === queryBookingId);
        if (found) setCurrentBooking(found);
        else navigate('/my-bookings');
      })
      .catch(() => navigate('/my-bookings'));
  }, [currentBooking, queryBookingId, navigate]);

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
    let done = false;

    const checkPayment = async () => {
      if (done) return;
      try {
        const res = await bookingApi.getMy();
        const updated = res.data?.find(b => b.id === bookingId);
        if (!updated || done) return;

        // Cập nhật trạng thái booking
        setCurrentBooking(updated);

        // Nếu đã thanh toán → chuyển trang xác nhận
        if (updated.paymentStatus === 'PAID') {
          done = true;
          message.success(t('payment.paidAutomatically'));
          if (state) navigate('/booking/confirmation', { state: { ...state, booking: updated } });
          else navigate('/my-bookings');
        }
      } catch (e) {
        console.warn('Payment status poll failed, retrying', e);
      }
    };

    const interval = setInterval(checkPayment, PAYMENT_POLL_INTERVAL_MS);
    // Khách quay lại từ app ngân hàng → kiểm tra ngay, không đợi vòng kế tiếp
    const onVisible = () => {
      if (document.visibilityState === 'visible') void checkPayment();
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      done = true;
      clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [bookingId, navigate, state, t]);

  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => message.success(t('payment.copied', { label })));
  };

  const handleSelectBankApp = (app: BankApp) => {
    if (!qrData || bookingId === undefined) return;
    setPickerOpen(false);
    const returnUrl = `${window.location.origin}/booking/payment?bookingId=${bookingId}`;
    const link = buildBankAppLink(qrData.bankAppDeeplink, app.appId, returnUrl);

    if (!app.autofill) {
      // App chỉ được mở lên: chép sẵn nội dung "DH <mã>" (Sepay dựa vào nội dung này để xác nhận đơn)
      navigator.clipboard.writeText(qrData.transferContent).catch(() => undefined);
      message.info(t('payment.bankApp.manualFill', {
        content: qrData.transferContent,
        account: qrData.accountNumber,
      }), 8);
    }

    if (platform) {
      window.location.href = link;
    } else {
      window.open(link, '_blank', 'noopener');
    }
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

        {/* Chọn cách thanh toán */}
        <Segmented
          block
          size="large"
          value={method}
          onChange={value => setMethod(value as PayMethod)}
          options={[
            { label: t('payment.method.qr'), value: 'qr', icon: <QrcodeOutlined /> },
            { label: t('payment.method.bankApp'), value: 'bankApp', icon: <BankOutlined /> },
          ]}
          style={{ marginBottom: 16 }}
        />

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
          {method === 'qr' ? (
            /* QR Code */
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
          ) : (
            /* App ngân hàng: mở app với thông tin chuyển khoản điền sẵn */
            <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 12, padding: 24, textAlign: 'center' }}>
              <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 12, color: '#1a1a1a' }}>{t('payment.bankApp.title')}</div>
              <BankOutlined style={{ fontSize: 40, color: '#006ce4', marginBottom: 12 }} />
              <p style={{ fontSize: 13, color: '#595959', marginBottom: 16 }}>{t('payment.bankApp.description')}</p>
              {!platform && (
                <Alert
                  type="info"
                  showIcon
                  message={t('payment.bankApp.desktopHint')}
                  style={{ marginBottom: 16, textAlign: 'left', fontSize: 12 }}
                />
              )}
              <Button
                type="primary"
                size="large"
                block
                icon={<BankOutlined />}
                disabled={!qrData}
                onClick={() => setPickerOpen(true)}
                style={{ background: '#006ce4', fontWeight: 700 }}
              >
                {t('payment.bankApp.choose')}
              </Button>
              <div style={{ marginTop: 12, fontSize: 12, color: '#008234', fontWeight: 600 }}>
                {t('payment.bankApp.autoConfirm')}
              </div>
            </div>
          )}

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
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 20 }}>
          <Button
            size="large"
            style={{ flex: '1 1 140px', height: 52 }}
            onClick={() => navigate('/my-bookings')}
          >
            {t('payment.payLater')}
          </Button>
          <Button
            type="primary"
            size="large"
            icon={<CheckCircleOutlined />}
            loading={confirming}
            style={{ flex: '2 1 240px', height: 52, fontSize: 15, fontWeight: 700, background: '#006ce4' }}
            onClick={handleConfirmPayment}
          >
            {t('payment.manualConfirm')}
          </Button>
        </div>
      </div>

      <BankAppPicker
        open={pickerOpen}
        platform={platform ?? 'android'}
        onClose={() => setPickerOpen(false)}
        onSelect={handleSelectBankApp}
      />

      <Footer />
    </div>
  );
};

export default PaymentPage;
