import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button, Divider } from 'antd';
import { CheckCircleFilled, PrinterOutlined, HomeOutlined, UnorderedListOutlined, FrownOutlined, ProfileOutlined, BankOutlined, InfoCircleOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import type { BookingFlowState } from '../types/bookingFlow';

const BookingConfirmation: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const state = useLocation().state as BookingFlowState | null;

  if (!state) {
    return (
      <div className="page-wrapper">
        <Header />
        <div style={{ textAlign: 'center', padding: '80px 24px' }}>
          <div style={{ fontSize: 48, color: '#faad14' }}><FrownOutlined /></div>
          <p style={{ fontSize: 16, marginTop: 12 }}>{t('confirmation.notFound')}</p>
          <Button type="primary" onClick={() => navigate('/')} style={{ marginTop: 16 }}>{t('hotel.home')}</Button>
        </div>
        <Footer />
      </div>
    );
  }

  const { booking, hotel, guestInfo, room, checkIn, checkOut, nights, totalPrice } = state;
  const bookingId = booking?.id || Math.floor(Math.random() * 90000 + 10000);

  return (
    <div className="page-wrapper" style={{ background: '#f5f5f5', minHeight: '100vh' }}>
      <Header />

      <div style={{ maxWidth: 720, margin: '32px auto', padding: '0 24px 48px' }}>
        {/* Success banner */}
        <div style={{
          background: '#fff',
          border: '1px solid #e7e7e7',
          borderRadius: 12,
          padding: 32,
          textAlign: 'center',
          marginBottom: 20,
          borderTop: '4px solid #008234',
        }}>
          <CheckCircleFilled style={{ fontSize: 56, color: '#008234', marginBottom: 16 }} />
          <h1 style={{ fontSize: 24, fontWeight: 800, marginBottom: 8 }}>{t('confirmation.title')}</h1>
          <p style={{ color: '#595959', marginBottom: 16 }}>
            {t('confirmation.thanks')}
          </p>
          <div style={{
            background: '#f5f5f5',
            borderRadius: 8,
            padding: '12px 24px',
            display: 'inline-block',
          }}>
            <span style={{ fontSize: 13, color: '#595959' }}>{t('booking.bookingCode')}: </span>
            <span style={{ fontSize: 18, fontWeight: 800, color: '#003b95', letterSpacing: 2 }}>
              BK{String(bookingId).padStart(6, '0')}
            </span>
          </div>
        </div>

        {/* Booking details */}
        <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 12, padding: 24, marginBottom: 16 }}>
          <h3 style={{ fontWeight: 700, marginBottom: 16 }}><ProfileOutlined style={{ marginRight: 8, color: '#006ce4' }} />{t('booking.details')}</h3>

          <div style={{ display: 'flex', gap: 16, marginBottom: 20 }}>
            <div style={{
              width: 80, height: 80, borderRadius: 8,
              background: 'linear-gradient(135deg, #003b95, #006ce4)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 32, flexShrink: 0,
            }}>
              <BankOutlined style={{ color: '#fff' }} />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 17, marginBottom: 4 }}>{hotel?.name || t('hotel.fallbackName')}</div>
              <div style={{ fontSize: 13, color: '#595959' }}>{hotel?.city || hotel?.address || 'Việt Nam'}</div>
            </div>
          </div>

          <Divider style={{ margin: '16px 0' }} />

          {[
            [t('confirmation.guest'), `${guestInfo?.firstName || ''} ${guestInfo?.lastName || ''}`],
            ['Email', guestInfo?.email],
            [t('booking.phone'), guestInfo?.phone],
            [t('hotel.breadcrumbHotels'), hotel?.name || '—'],
            [t('confirmation.roomType'), room?.type || room?.name || t('confirmation.standardRoom')],
            [t('booking.checkInDate'), t('confirmation.checkInValue', { date: checkIn })],
            [t('booking.checkOutDate'), t('confirmation.checkOutValue', { date: checkOut })],
            [t('booking.nights'), t('booking.nightsValue', { count: Math.max(1, nights || 2) })],
            [t('confirmation.paymentStatus'), booking?.paymentStatus === 'PAID' ? t('confirmation.paid') : t('payment.statusPending')],
          ].map(([label, value]) => (
            <div key={label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', padding: '8px 0', borderBottom: '1px solid #f5f5f5' }}>
              <span style={{ color: '#595959', fontSize: 14 }}>{label}</span>
              <span style={{ fontWeight: 600, fontSize: 14, textAlign: 'right', maxWidth: '60%' }}>{value || '—'}</span>
            </div>
          ))}

          <Divider style={{ margin: '16px 0' }} />

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 16, fontWeight: 700 }}>{t('booking.total')}</span>
            <span style={{ fontSize: 22, fontWeight: 800, color: '#006ce4' }}>
              {(totalPrice || 0).toLocaleString('vi-VN')}₫
            </span>
          </div>
        </div>

        {/* Info box */}
        <div style={{ background: '#e8f0fe', border: '1px solid #c2d5f5', borderRadius: 8, padding: 16, marginBottom: 20 }}>
          <div style={{ fontWeight: 700, marginBottom: 8, color: '#003b95' }}><InfoCircleOutlined style={{ marginRight: 6 }} />{t('confirmation.notesTitle')}</div>
          <ul style={{ color: '#595959', fontSize: 13, paddingLeft: 20, margin: 0 }}>
            <li style={{ marginBottom: 6 }}>{t('confirmation.noteId')}</li>
            <li style={{ marginBottom: 6 }}>{t('confirmation.noteTimes')}</li>
            <li style={{ marginBottom: 6 }}>{t('confirmation.noteContact')}</li>
            <li>{t('confirmation.noteCancel')}</li>
          </ul>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Button
            type="primary"
            icon={<UnorderedListOutlined />}
            size="large"
            style={{ flex: 1, height: 48, fontWeight: 700 }}
            onClick={() => navigate('/my-bookings')}
          >
            {t('confirmation.viewBookings')}
          </Button>
          <Button
            icon={<HomeOutlined />}
            size="large"
            style={{ flex: 1, height: 48 }}
            onClick={() => navigate('/')}
          >
            {t('hotel.home')}
          </Button>
          <Button
            icon={<PrinterOutlined />}
            size="large"
            onClick={() => window.print()}
          >
            {t('confirmation.print')}
          </Button>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default BookingConfirmation;
