import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Slider, Checkbox, Select, Empty } from 'antd';
import Header from '../components/Header';
import Footer from '../components/Footer';
import SearchWidget from '../components/SearchWidget';
import { getHotelImage } from '../utils/imageUtils';
import { lowestRoomPrice, ratingLabelKey } from '../utils/hotelDisplay';
import { hotelApi, HotelWithStats } from '../api';
import { cachedFetch, HOTEL_LIST_TTL } from '../utils/apiCache';

const { Option } = Select;

// Keywords are matched against the hotels' Vietnamese descriptions, so only the labels are translated.
const PROPERTY_TYPE_FILTERS = [
  { labelKey: 'results.filters.holidayHome', keyword: 'nhà nghỉ mát' },
  { labelKey: 'results.filters.homestay', keyword: 'nhà dân' },
  { labelKey: 'results.filters.countryHouse', keyword: 'nông thôn' },
  { labelKey: 'results.filters.apartment', keyword: 'căn hộ' },
  { labelKey: 'results.filters.villa', keyword: 'biệt thự' },
] as const;

const POPULAR_FILTERS = [
  { labelKey: 'results.filters.hotel', keyword: 'khách sạn', group: 'property' },
  { labelKey: 'results.filters.breakfast', keyword: 'bữa sáng', group: 'feature' },
  { labelKey: 'results.filters.noCreditCard', keyword: 'thẻ tín dụng', group: 'feature' },
  { labelKey: 'results.filters.beachfront', keyword: 'biển', group: 'feature' },
  { labelKey: 'results.filters.apartment', keyword: 'căn hộ', group: 'property' },
  { labelKey: 'results.filters.veryGood8', subKey: 'results.filters.basedOnReviews', val: 8 },
  { labelKey: 'results.filters.resort', keyword: 'resort', group: 'property' },
] as const;

const AMENITY_FILTERS = [
  { labelKey: 'results.filters.pool', keyword: 'hồ bơi' },
  { labelKey: 'results.filters.wifi', keyword: 'wifi' },
  { labelKey: 'results.filters.airportShuttle', keyword: 'sân bay' },
  { labelKey: 'results.filters.parking', keyword: 'đậu xe' },
  { labelKey: 'results.filters.spa', keyword: 'spa' },
  { labelKey: 'results.filters.gym', keyword: 'gym' },
] as const;

const POLICY_FILTERS = [
  { labelKey: 'results.filters.freeCancellation', keyword: 'hủy' },
  { labelKey: 'results.filters.noPrepayment', keyword: 'thanh toán trước' },
] as const;

const SearchResults: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const city = searchParams.get('city') || '';
  const checkIn = searchParams.get('checkIn') || '';
  const checkOut = searchParams.get('checkOut') || '';
  const guests = searchParams.get('guests') || '2';
  const roomsStr = searchParams.get('rooms') || '1';
  
  const numGuests = Number(guests) || 2;
  const numRooms = Number(roomsStr) || 1;

  const [hotels, setHotels] = useState<HotelWithStats[]>([]);
  const [loading, setLoading] = useState(true);
  const [sortBy, setSortBy] = useState('rating');
  const [priceRange, setPriceRange] = useState<[number, number]>([0, 10000000]);
  const [selectedRatings, setSelectedRatings] = useState<number[]>([]);
  const [selectedPropertyTypes, setSelectedPropertyTypes] = useState<string[]>([]);
  const [selectedFeatures, setSelectedFeatures] = useState<string[]>([]);

  useEffect(() => {
    setLoading(true);
    // Tái dùng cache hotels_all từ HomePage nếu có
    cachedFetch('hotels_all', () => hotelApi.getAll(), HOTEL_LIST_TTL)
      .then(data => setHotels(data || []))
      .catch(err => console.error('Failed to load hotels', err))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    let result = [...hotels];

    // Lọc theo thành phố
    if (city) {
      result = result.filter(h =>
        h.city?.toLowerCase().includes(city.toLowerCase()) ||
        h.name?.toLowerCase().includes(city.toLowerCase()) ||
        h.address?.toLowerCase().includes(city.toLowerCase())
      );
    }

    // Lọc theo đánh giá
    if (selectedRatings.length > 0) {
      result = result.filter(h => {
        const r = h.ratingAvg || 0;
        return selectedRatings.some(sel => r >= sel);
      });
    }

    // Lọc theo khoảng giá
    result = result.filter(h => {
      const price = lowestRoomPrice(h) ?? 0;
      return price >= priceRange[0] && price <= priceRange[1];
    });

    // Lọc theo loại chỗ nghỉ (logic OR)
    if (selectedPropertyTypes.length > 0) {
      result = result.filter(h => {
        const textToSearch = `${h.name} ${h.description}`.toLowerCase();
        return selectedPropertyTypes.some(type => textToSearch.includes(type.toLowerCase()));
      });
    }

    // Lọc theo tiện nghi (logic AND)
    if (selectedFeatures.length > 0) {
      result = result.filter(h => {
        const textToSearch = `${h.name} ${h.description}`.toLowerCase();
        return selectedFeatures.every(feature => textToSearch.includes(feature.toLowerCase()));
      });
    }

    // Lọc theo sức chứa khách và phòng
    result = result.filter(h => {
      if (!h.rooms || h.rooms.length === 0) return false;
      const totalCapacity = h.rooms.reduce((sum, r) => sum + (r.capacity || 0) * (r.quantity || 0), 0);
      const totalRooms = h.rooms.reduce((sum, r) => sum + (r.quantity || 0), 0);
      return totalCapacity >= numGuests && totalRooms >= numRooms;
    });

    // Sắp xếp
    result.sort((a, b) => {
      if (sortBy === 'rating') return (b.ratingAvg || 0) - (a.ratingAvg || 0);
      if (sortBy === 'name') return (a.name ?? '').localeCompare(b.name ?? '');
      return 0;
    });

    return result;
  }, [hotels, city, selectedRatings, sortBy, priceRange, selectedPropertyTypes, selectedFeatures, numGuests, numRooms]);


  return (
    <div className="page-wrapper">
      <Header />

      {/* Search bar strip */}
      <div style={{ background: '#003b95', padding: '16px 0' }}>
        <div className="container">
          <SearchWidget
            initialCity={city}
            initialCheckIn={checkIn}
            initialCheckOut={checkOut}
            initialGuests={Number(guests)}
          />
        </div>
      </div>

      <div style={{ background: '#f5f5f5', flex: 1 }}>
        <div className="container">
          {/* Back button */}
          <button
            className="bk-btn-back-blue"
            onClick={() => navigate('/')}
          >
            {t('common.backToHome')}
          </button>
          <div className="search-results">
            {/* ── FILTER SIDEBAR ── */}
            <aside className="filter-sidebar">
              {/* Map Box */}
              <div 
                style={{
                  position: 'relative',
                  borderRadius: 8, overflow: 'hidden', border: '1px solid #e2e8f0',
                  height: 150, marginBottom: 16,
                  cursor: 'pointer',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.06)'
                }}
                onClick={() => {
                  const loc = city || (filtered.length > 0 ? (filtered[0].city || filtered[0].address || 'Hà Nội, Việt Nam') : 'Việt Nam');
                  window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(loc)}`, '_blank');
                }}
              >
                <iframe 
                  src={`https://maps.google.com/maps?q=${encodeURIComponent(city || (filtered.length > 0 ? (filtered[0].city || filtered[0].address || 'Hà Nội, Việt Nam') : 'Việt Nam'))}&t=&z=13&ie=UTF8&iwloc=&output=embed`}
                  style={{ position: 'absolute', top: -50, left: 0, width: '100%', height: 250, border: 0, pointerEvents: 'none' }}
                  aria-hidden="true"
                  title="map"
                />
                <div style={{
                  position: 'absolute', inset: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: 'rgba(255,255,255,0.15)'
                }}>
                  <button style={{ 
                      fontSize: 14, fontWeight: 700, borderRadius: 4, 
                      padding: '0 16px', height: 36, background: '#006ce4', color: '#fff',
                      border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6,
                      boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                    }}
                  >
                    📍 {t('results.showOnMap')}
                  </button>
                </div>
              </div>

              {/* Main Filter Card */}
              <div style={{ background: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
                <div style={{ padding: '16px', borderBottom: '1px solid #e2e8f0' }}>
                  <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#1a1a1a' }}>{t('results.filterBy')}</h3>
                </div>

                <div style={{ padding: '16px', borderBottom: '1px solid #e2e8f0' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 14, color: '#1a1a1a' }}>{t('results.propertyType')}</div>
                  {PROPERTY_TYPE_FILTERS.map((item, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12, alignItems: 'center' }}>
                      <Checkbox 
                        checked={selectedPropertyTypes.includes(item.keyword)}
                        onChange={(e) => {
                          if (e.target.checked) setSelectedPropertyTypes([...selectedPropertyTypes, item.keyword]);
                          else setSelectedPropertyTypes(selectedPropertyTypes.filter(k => k !== item.keyword));
                        }}
                      >
                        <span style={{ fontSize: 14, color: '#333' }}>{t(item.labelKey)}</span>
                      </Checkbox>
                    </div>
                  ))}
                </div>

                <div style={{ padding: '16px', borderBottom: '1px solid #e2e8f0' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 12, color: '#1a1a1a' }}>{t('results.budget')}</div>
                  <div style={{ fontSize: 13, marginBottom: 16, color: '#333' }}>
                    VND {priceRange[0].toLocaleString('vi-VN')} - VND {priceRange[1].toLocaleString('vi-VN')}{priceRange[1] === 10000000 ? '+' : ''}
                  </div>
                  
                  {/* Histogram Chart */}
                  <div style={{ height: 40, display: 'flex', alignItems: 'flex-end', gap: '2px', padding: '0 10px', marginBottom: -10 }}>
                    {[1, 2, 1, 9, 4, 3, 5, 2, 1, 2, 1, 2, 1, 1, 2, 1, 1, 0, 1, 0, 0].map((h, i) => (
                      <div key={i} style={{ flex: 1, background: '#e0e0e0', height: `${h * 10}%`, borderRadius: '2px 2px 0 0' }}></div>
                    ))}
                  </div>
                  <Slider 
                    range 
                    min={0}
                    max={10000000}
                    step={100000}
                    value={[priceRange[0], priceRange[1]]}
                    onChange={(val) => setPriceRange([val[0], val[1]])}
                    tooltip={{ formatter: null }} 
                    trackStyle={[{ backgroundColor: '#006ce4', height: 4 }]}
                    handleStyle={[
                      { borderColor: '#006ce4', backgroundColor: '#006ce4', width: 18, height: 18, marginTop: -7 },
                      { borderColor: '#006ce4', backgroundColor: '#006ce4', width: 18, height: 18, marginTop: -7 }
                    ]}
                  />
                </div>

                <div style={{ padding: '16px', borderBottom: '1px solid #e2e8f0' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 14, color: '#1a1a1a' }}>{t('results.popularFilters')}</div>
                  {POPULAR_FILTERS.map((item, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14, alignItems: 'flex-start' }}>
                      <Checkbox
                        checked={
                          'val' in item ? selectedRatings.includes(item.val)
                          : item.group === 'property' ? selectedPropertyTypes.includes(item.keyword)
                          : selectedFeatures.includes(item.keyword)
                        }
                        onChange={(e) => {
                          if ('val' in item) {
                            if (e.target.checked) setSelectedRatings([...selectedRatings, item.val]);
                            else setSelectedRatings(selectedRatings.filter(r => r !== item.val));
                          } else if (item.group === 'property') {
                            if (e.target.checked) setSelectedPropertyTypes([...selectedPropertyTypes, item.keyword]);
                            else setSelectedPropertyTypes(selectedPropertyTypes.filter(k => k !== item.keyword));
                          } else {
                            if (e.target.checked) setSelectedFeatures([...selectedFeatures, item.keyword]);
                            else setSelectedFeatures(selectedFeatures.filter(k => k !== item.keyword));
                          }
                        }}
                      >
                        <div>
                          <div style={{ fontSize: 14, color: '#333' }}>{t(item.labelKey)}</div>
                          {'subKey' in item && <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>{t(item.subKey)}</div>}
                        </div>
                      </Checkbox>
                    </div>
                  ))}
                </div>

                <div style={{ padding: '16px', borderBottom: '1px solid #e2e8f0' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 14, color: '#1a1a1a' }}>{t('results.amenities')}</div>
                  {AMENITY_FILTERS.map((item, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12, alignItems: 'center' }}>
                      <Checkbox
                        checked={selectedFeatures.includes(item.keyword)}
                        onChange={(e) => {
                          if (e.target.checked) setSelectedFeatures([...selectedFeatures, item.keyword]);
                          else setSelectedFeatures(selectedFeatures.filter(k => k !== item.keyword));
                        }}
                      >
                        <span style={{ fontSize: 14, color: '#333' }}>{t(item.labelKey)}</span>
                      </Checkbox>
                    </div>
                  ))}
                </div>

                <div style={{ padding: '16px' }}>
                  <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 14, color: '#1a1a1a' }}>{t('results.bookingPolicy')}</div>
                  {POLICY_FILTERS.map((item, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12, alignItems: 'center' }}>
                      <Checkbox
                        checked={selectedFeatures.includes(item.keyword)}
                        onChange={(e) => {
                          if (e.target.checked) setSelectedFeatures([...selectedFeatures, item.keyword]);
                          else setSelectedFeatures(selectedFeatures.filter(k => k !== item.keyword));
                        }}
                      >
                        <span style={{ fontSize: 14, color: '#333' }}>{t(item.labelKey)}</span>
                      </Checkbox>
                    </div>
                  ))}
                </div>
              </div>
            </aside>

            {/* ── RESULTS ── */}
            <div className="results-list">
              <div className="results-header">
                <div>
                  <div className="results-count">
                    {city ? `${city}: ` : ''}{t('results.found', { count: filtered.length })}
                  </div>
                  {(checkIn && checkOut) && (
                    <div style={{ fontSize: 14, color: '#595959', marginTop: 4 }}>
                      {t('results.stayDetails', { checkIn, checkOut, guests, rooms: roomsStr })}
                    </div>
                  )}
                </div>
                <Select
                  value={sortBy}
                  onChange={setSortBy}
                  style={{ width: 180 }}
                >
                  <Option value="rating">{t('results.sortRating')}</Option>
                  <Option value="name">{t('results.sortName')}</Option>
                </Select>
              </div>

              {loading ? (
                [...Array(4)].map((_, i) => (
                  <div key={i} className="hotel-result-card" style={{ padding: 0, overflow: 'hidden', pointerEvents: 'none', display: 'flex' }}>
                    <div style={{ width: 220, minHeight: 150, flexShrink: 0, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                    <div style={{ flex: 1, padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                      <div style={{ height: 20, width: '55%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                      <div style={{ height: 14, width: '35%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                      <div style={{ height: 14, width: '45%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                      <div style={{ height: 20, width: '25%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite', marginTop: 'auto' }} />
                    </div>
                  </div>
                ))
              ) : filtered.length === 0 ? (
                <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 8, padding: '80px 20px', display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      <div>
                        <div style={{ fontSize: 20, fontWeight: 700, color: '#1a1a1a', marginBottom: 8 }}>{t('results.emptyTitle')}</div>
                        <div style={{ color: '#595959', fontSize: 15 }}>{t('results.emptyHint')}</div>
                      </div>
                    }
                  />
                </div>
              ) : (
                filtered.map((hotel) => {
                  const price = lowestRoomPrice(hotel);
                  const rating = hotel.ratingAvg ?? null;
                  const ratingLabel = t(`common.rating.${ratingLabelKey(rating)}`);

                  return (
                    <div
                      key={hotel.id}
                      className="hotel-result-card"
                      onClick={() => navigate(`/hotels/${hotel.id}`)}
                    >
                    {(() => {
                      const imgUrl = getHotelImage(hotel);
                      return imgUrl ? (
                        <img
                          className="hotel-result-img"
                          src={imgUrl}
                          alt={hotel.name}
                          loading="lazy"
                          referrerPolicy="no-referrer"
                          style={{ objectFit: 'cover' }}
                          onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                        />
                      ) : (
                        <div
                          className="hotel-result-img"
                          style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 48, background: 'linear-gradient(135deg, #e8f0fe, #f0f0f0)' }}
                        >
                          🏨
                        </div>
                      );
                    })()}
                      <div className="hotel-result-body">
                        <div className="hotel-result-info">
                          <div className="hotel-result-name">{hotel.name}</div>
                          <div className="hotel-result-city">{hotel.city || hotel.address || 'Hà Nội'}</div>
                          <div className="hotel-result-desc">{hotel.description || t('results.defaultDescription')}</div>

                          <div style={{ marginTop: 12, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                            <span style={{ fontSize: 12, color: '#008234', background: '#e6f5ea', padding: '2px 8px', borderRadius: 12 }}>✓ {t('results.badgeFreeCancel')}</span>
                            <span style={{ fontSize: 12, color: '#006ce4', background: '#e8f0fe', padding: '2px 8px', borderRadius: 12 }}>✓ {t('results.badgePayAtHotel')}</span>
                          </div>
                        </div>

                        <div className="hotel-result-pricing">
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'flex-end', marginBottom: 'auto' }}>
                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', textAlign: 'right' }}>
                              <span className="rating-word">{ratingLabel}</span>
                              <span className="rating-count">{t('common.reviewsCount', { count: hotel.reviewCount || 0 })}</span>
                            </div>
                            <div className="hotel-card-rating-score">
                              {rating !== null ? rating.toFixed(1) : 'N/A'}
                            </div>
                          </div>
                          <div style={{ textAlign: 'right' }}>
                            <div className="hotel-card-price-label">{t('hotelCard.startingFrom')}</div>
                            <div className="hotel-card-price-new" style={{ marginBottom: 4 }}>{price !== null ? 'VND ' + price.toLocaleString('vi-VN') : t('hotelCard.contactForPrice')}</div>
                            <button
                              className="hotel-result-cta"
                              style={{ marginTop: 8, padding: '8px 16px', borderRadius: 4, cursor: 'pointer', background: '#006ce4', color: '#fff', border: 'none', fontWeight: 700, fontSize: 14, width: '100%' }}
                              onClick={(e) => { e.stopPropagation(); navigate(`/hotels/${hotel.id}`); }}
                            >
                              {t('results.seeAvailability')}
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default SearchResults;
