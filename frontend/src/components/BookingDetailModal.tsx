import React from 'react';
import { Descriptions, Modal } from 'antd';
import { useTranslation } from 'react-i18next';
import type { Booking } from '../api-generated';
import { dateLocale } from '../i18n';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

interface Props {
  booking: Booking | null;
  statusLabel: string;
  paymentLabel: string;
  onClose: () => void;
}

const formatDate = (value?: string) => (value ? new Date(value).toLocaleDateString(dateLocale()) : '—');

const countNights = (checkIn?: string, checkOut?: string) =>
  checkIn && checkOut ? Math.round((new Date(checkOut).getTime() - new Date(checkIn).getTime()) / MS_PER_DAY) : 0;

const formatVnd = (amount?: number) => `${(amount || 0).toLocaleString('vi-VN')}₫`;

/** Read-only summary of one booking, using the data already loaded for the "My bookings" list. */
const BookingDetailModal: React.FC<Props> = ({ booking, statusLabel, paymentLabel, onClose }) => {
  const { t } = useTranslation();

  return (
    <Modal
      title={booking ? t('myBookings.detail.title', { id: booking.id }) : ''}
      open={booking !== null}
      onCancel={onClose}
      footer={null}
      centered
    >
      {booking && (
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label={t('myBookings.detail.hotel')}>
            <div style={{ fontWeight: 600 }}>{booking.hotel?.name || t('hotel.fallbackName')}</div>
            {booking.hotel?.address && <div style={{ color: '#595959' }}>{booking.hotel.address}</div>}
          </Descriptions.Item>
          <Descriptions.Item label={t('myBookings.detail.stay')}>
            {formatDate(booking.checkInDate)} → {formatDate(booking.checkOutDate)}
            {' '}({t('myBookings.detail.nights', { count: countNights(booking.checkInDate, booking.checkOutDate) })})
          </Descriptions.Item>
          <Descriptions.Item label={t('myBookings.detail.rooms')}>
            {(booking.bookingRooms || []).map(item => (
              <div key={item.id}>
                {t('myBookings.detail.roomLine', {
                  name: item.room?.name || t('confirmation.standardRoom'),
                  quantity: item.quantity ?? 0,
                  price: formatVnd(item.price ?? item.room?.pricePerNight),
                })}
              </div>
            ))}
          </Descriptions.Item>
          <Descriptions.Item label={t('myBookings.detail.total')}>
            <strong>{formatVnd(booking.totalPrice)}</strong>
          </Descriptions.Item>
          <Descriptions.Item label={t('myBookings.detail.status')}>{statusLabel}</Descriptions.Item>
          <Descriptions.Item label={t('myBookings.detail.payment')}>{paymentLabel}</Descriptions.Item>
          <Descriptions.Item label={t('myBookings.detail.createdAt')}>
            {booking.createdAt ? new Date(booking.createdAt).toLocaleString(dateLocale()) : '—'}
          </Descriptions.Item>
        </Descriptions>
      )}
    </Modal>
  );
};

export default BookingDetailModal;
