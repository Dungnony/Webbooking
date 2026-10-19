import React from 'react';
import { useTranslation } from 'react-i18next';
import { getHotelImage } from '../utils/imageUtils';
import type { HotelWithStats } from '../api';

interface HotelCardProps {
  hotel: HotelWithStats;
  onClick?: () => void;
  wished?: boolean;
  onWishToggle?: () => void;
  showPrice?: boolean;
  estimatedPrice?: number;
}

const HotelCard: React.FC<HotelCardProps> = ({
  hotel,
  onClick,
  wished = false,
  onWishToggle,
  showPrice = true,
  estimatedPrice,
}) => {
  const { t } = useTranslation();
  const rating = hotel.ratingAvg ?? 0;
  // Lấy giá từ backend, không tự sinh giá
  const price = estimatedPrice ?? hotel.rooms?.[0]?.pricePerNight;
  const imgUrl = getHotelImage(hotel) || 'https://images.unsplash.com/photo-1542314831-c6a4d1409e15?auto=format&fit=crop&q=80&w=1000'; // fallback đẹp
  
  const ratingKey = rating >= 9 ? 'exceptional' : rating >= 8.5 ? 'superb' : rating >= 8 ? 'veryGood' : rating > 0 ? 'good' : 'none';
  const ratingLabel = t(`common.rating.${ratingKey}`);

  const [imageError, setImageError] = React.useState(false);

  return (
    <div className="hotel-card" onClick={onClick}>
      {/* Container ảnh */}
      <div className="hotel-card-img-container">
        {onWishToggle && (
          <button
            className={`hotel-card-wish-btn ${wished ? 'active' : ''}`}
            onClick={(e) => { e.stopPropagation(); onWishToggle(); }}
          >
            <svg viewBox="0 0 24 24" fill={wished ? "#e71d36" : "none"} stroke={wished ? "#e71d36" : "#666"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
            </svg>
          </button>
        )}
        {!imageError ? (
          <img
            className="hotel-card-img"
            src={imgUrl}
            alt={hotel.name}
            loading="lazy"
            referrerPolicy="no-referrer"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="hotel-card-img hotel-card-img-fallback">🏨</div>
        )}
      </div>

      {/* Body */}
      <div className="hotel-card-body">
        <div className="hotel-card-name">{hotel.name}</div>
        <div className="hotel-card-city">
          {hotel.city || hotel.address || t('hotelCard.locationPending')}
        </div>

        <div className="hotel-card-rating-container">
          <div className="hotel-card-rating-score">{rating > 0 ? rating.toFixed(1) : 'N/A'}</div>
          <div className="hotel-card-rating-text">
            <span className="rating-word">{ratingLabel}</span>
            <span className="rating-count">{t('common.reviewsCount', { count: hotel.reviewCount || 0 })}</span>
          </div>
        </div>

        {showPrice && (
          <div className="hotel-card-price-wrapper">
            <span className="hotel-card-price-label">{t('hotelCard.startingFrom')}</span>
            <span className="hotel-card-price-new">
              {price ? `VND ${price.toLocaleString('vi-VN')}` : t('hotelCard.contactForPrice')}
            </span>
          </div>
        )}
      </div>
    </div>
  );
};

export default HotelCard;
