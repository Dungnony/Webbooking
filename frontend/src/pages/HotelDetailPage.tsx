import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Row, Col, Skeleton, Tag, Button, Divider, message, Drawer } from 'antd';
import {
  EnvironmentOutlined,
  HeartOutlined, HeartFilled,
  LeftOutlined, ShareAltOutlined,
} from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { hotelApi, reviewApi, HotelWithStats } from '../api';
import { useWishlist } from '../context/useWishlist';
import { transformImageUrl } from '../utils/imageUtils';
import { cachedFetch, HOTEL_DETAIL_TTL, reviewCacheKey } from '../utils/apiCache';
import HotelReviewAndChatPage from './HotelReviewAndChatPage';

const AMENITIES = [
  { label: 'Wi-Fi miễn phí' },
  { label: 'Bãi đỗ xe' },
  { label: 'Bữa sáng' },
  { label: 'Hồ bơi' },
  { label: 'Phòng gym' },
  { label: 'Nhà hàng' },
  { label: 'Lễ tân 24/7' },
  { label: 'Điều hoà' },
];

const NAV_TABS = ['Tổng quan', 'Thông tin & giá', 'Tiện nghi', 'Quy tắc chung', 'Đánh giá của khách'];
const REVIEWS_CACHE_TTL_MS = 60_000;

/* ── Rating Label Helper ── */
const getRatingLabel = (score: number) => {
  if (score >= 9) return 'Xuất sắc';
  if (score >= 8) return 'Rất tốt';
  if (score >= 7) return 'Tốt';
  if (score >= 5) return 'Ổn';
  return 'Trung bình';
};

const HotelDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [hotel, setHotel] = useState<HotelWithStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('Tổng quan');
  const { isInWishlist, toggleWish } = useWishlist();
  const wished = isInWishlist(Number(id));
  const [previewIndex, setPreviewIndex] = useState<number | null>(null);
  const [reviewDrawerVisible, setReviewDrawerVisible] = useState(false);

  // Đánh giá chỉ dùng để hiển thị điểm; việc viết đánh giá nằm trong drawer HotelReviewAndChatPage
  const [reviewStats, setReviewStats] = useState<{ avg: number | null; total: number }>({ avg: null, total: 0 });

  const fetchReviews = useCallback(() => {
    if (!id) return;
    // Only the totals are needed here, so one review is enough; reviews change rarely → cache 60s.
    cachedFetch(reviewCacheKey(Number(id)), () => reviewApi.getForHotel(Number(id), 0, 1), REVIEWS_CACHE_TTL_MS)
      .then(page => setReviewStats({ avg: page.averageRating, total: page.totalElements }))
      .catch(err => console.error('Failed to load reviews', err));
  }, [id]);

  useEffect(() => {
    if (!id) return;
    // Cache chi tiết khách sạn 2 phút
    cachedFetch(`hotel_${id}`, () => hotelApi.getById(Number(id)), HOTEL_DETAIL_TTL)
      .then(data => setHotel(data))
      .catch(err => console.error('Failed to load hotel', err))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { fetchReviews(); }, [fetchReviews]);

  // Thu thập tất cả hình ảnh từ các phòng
  const allImages = useMemo(() => {
    if (!hotel) return [];
    const imgs: string[] = [];
    if (hotel.imageUrl) imgs.push(transformImageUrl(hotel.imageUrl) as string);
    hotel.rooms?.forEach(room => {
      room.images?.forEach(img => {
        if (img.imageUrl) {
          const tUrl = transformImageUrl(img.imageUrl) as string;
          if (!imgs.includes(tUrl)) imgs.push(tUrl);
        }
      });
    });
    return imgs;
  }, [hotel]);

  const handleTabClick = (tab: string) => {
    if (tab === 'Đánh giá của khách') {
      setReviewDrawerVisible(true);
      return;
    }

    setActiveTab(tab);
    const sectionId = {
      'Tổng quan': 'overview-section',
      'Thông tin & giá': 'rooms-section',
      'Tiện nghi': 'amenities-section',
      'Quy tắc chung': 'policies-section',
    }[tab];

    if (sectionId) {
      // Thêm khoảng cách bù cho header cố định
      const element = document.getElementById(sectionId);
      if (element) {
        const headerOffset = 110; // Điều chỉnh theo chiều cao thực tế của header
        const elementPosition = element.getBoundingClientRect().top;
        const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

        window.scrollTo({
          top: offsetPosition,
          behavior: 'smooth'
        });
      }
    }
  };

  if (loading) return (
    <div className="page-wrapper">
      <Header />
      <div className="container" style={{ padding: '24px' }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    </div>
  );

  if (!hotel) return (
    <div className="page-wrapper">
      <Header />
      <div style={{ textAlign: 'center', padding: '80px 24px' }}>
        <div style={{ fontSize: 48 }}>🏨</div>
        <h2 style={{ marginTop: 12 }}>Không tìm thấy khách sạn</h2>
        <Button type="primary" style={{ marginTop: 12 }} onClick={() => navigate('/search')}>
          Quay lại tìm kiếm
        </Button>
      </div>
      <Footer />
    </div>
  );

  const hotelName = hotel.name || 'Khách sạn';
  const basePrice: number | null = hotel.rooms?.[0]?.pricePerNight ?? null;
  const displayRating = reviewStats.avg ? reviewStats.avg.toFixed(1) : (hotel.ratingAvg ? hotel.ratingAvg.toFixed(1) : null);
  const displayRatingNum = reviewStats.avg ?? hotel.ratingAvg ?? null;
  const ratingLabel = displayRatingNum
    ? getRatingLabel(displayRatingNum)
    : null;

  const handleShare = async () => {
    try {
      if (navigator.share) {
        await navigator.share({
          title: hotelName,
          text: `Xem khách sạn ${hotelName} trên Booking!`,
          url: window.location.href,
        });
      } else {
        await navigator.clipboard.writeText(window.location.href);
        message.success('Đã sao chép liên kết!');
      }
    } catch (error) {
      console.log('Error sharing:', error);
    }
  };

  return (
    <div className="page-wrapper">
      <Header />

      {/* ── Breadcrumb + Back ── */}
      <div style={{ borderBottom: '1px solid var(--border)' }}>
        <div className="container" style={{ padding: '10px 24px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#595959' }}>
            <Button 
              type="text" 
              icon={<LeftOutlined />} 
              onClick={() => navigate('/')} 
              style={{ padding: '0 8px', marginRight: 12, fontWeight: 600, color: '#006ce4', background: '#e6f2ff', borderRadius: 4 }}
            >
              Về trang chủ
            </Button>
            <span style={{ cursor: 'pointer', color: '#006ce4' }} onClick={() => navigate('/search')}>Khách sạn</span>
            <span>›</span>
            <span style={{ color: '#1a1a1a', fontWeight: 500 }}>{hotelName}</span>
          </div>
        </div>
      </div>

      {/* ── Navigation Tabs ── */}
      <div style={{ background: '#fff', position: 'sticky', top: 56, zIndex: 100 }}>
        <div className="container">
          <div className="hotel-nav-tabs">
            {NAV_TABS.map(tab => (
              <button
                key={tab}
                className={`hotel-nav-tab ${activeTab === tab ? 'active' : ''}`}
                onClick={() => handleTabClick(tab)}
              >
                {tab}
                {tab === 'Đánh giá của khách' && reviewStats.total > 0 && (
                  <span style={{
                    marginLeft: 6, background: '#006ce4', color: '#fff',
                    borderRadius: 10, padding: '1px 7px', fontSize: 11, fontWeight: 700,
                  }}>
                    {reviewStats.total}
                  </span>
                )}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* ── Title Row ── */}
      <div id="overview-section" className="container" style={{ padding: '14px 24px 0' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              {[1, 2, 3].map(i => (
                <span key={i} style={{ color: '#febb02', fontSize: 14 }}>★</span>
              ))}
            </div>
            <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0, lineHeight: 1.3 }}>{hotelName}</h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4, flexWrap: 'wrap' }}>
              <span
                style={{ fontSize: 13, color: '#006ce4', cursor: 'pointer' }}
                onClick={() => window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${hotelName} ${hotel.address || ''} ${hotel.city || ''}`)}`, '_blank')}
              >
                {hotel.city || hotel.address || 'Việt Nam'} – <span style={{ textDecoration: 'underline' }}>Vị trí xuất sắc - hiển thị bản đồ</span>
              </span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', flexShrink: 0 }}>
            <Button
              icon={wished ? <HeartFilled style={{ color: '#d4111e' }} /> : <HeartOutlined />}
              onClick={() => toggleWish(Number(id))}
              size="large"
            />
            <Button icon={<ShareAltOutlined />} size="large" onClick={handleShare} />
            <Button
              type="primary"
              size="large"
              style={{ fontWeight: 700, height: 40 }}
              onClick={() => navigate(`/booking/${id}/guest`)}
            >
              Đặt ngay
            </Button>
          </div>
        </div>
      </div>

      {/* ── Photo Gallery + Rating Sidebar ── */}
      <div className="container" style={{ padding: '12px 24px 0' }}>
        <Row gutter={12}>
          <Col xs={24} md={17}>
            {allImages.length > 0 ? (
              <div className="hotel-gallery">
                {allImages.slice(0, 5).map((img, i) => (
                  <div key={i} className={i === 0 ? 'hotel-gallery-main' : ''} style={{ position: 'relative', overflow: 'hidden' }}>
                    <img
                      src={img}
                      alt={`${hotelName} ${i + 1}`}
                      className="hotel-gallery-img"
                      onClick={() => setPreviewIndex(i)}
                      referrerPolicy="no-referrer"
                    />
                    {i === 4 && allImages.length > 5 && (
                      <div style={{
                        position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.5)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        color: '#fff', fontSize: 18, fontWeight: 700, pointerEvents: 'none'
                      }}>
                        +{allImages.length - 5} ảnh
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div style={{ background: '#f0f0f0', borderRadius: 4, height: 403, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 56 }}>
                🏨
              </div>
            )}
          </Col>

          {/* Rating Sidebar & Map */}
          <Col xs={24} md={7}>
            {/* Map Box */}
            <div
              style={{
                position: 'relative',
                borderRadius: 8, overflow: 'hidden', border: '1px solid #e2e8f0',
                height: 150, marginBottom: 16,
                cursor: 'pointer',
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)'
              }}
              onClick={() => window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${hotelName} ${hotel.address || ''} ${hotel.city || ''}`)}`, '_blank')}
            >
              <iframe
                src={`https://maps.google.com/maps?q=${encodeURIComponent(`${hotelName} ${hotel.address || ''} ${hotel.city || ''}`)}&t=&z=14&ie=UTF8&iwloc=&output=embed`}
                style={{ position: 'absolute', top: -50, left: 0, width: '100%', height: 250, border: 0, pointerEvents: 'none' }}
                aria-hidden="true"
                title="map"
              />
              <div style={{
                position: 'absolute', inset: 0,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                background: 'rgba(255,255,255,0.15)'
              }}>
                <Button
                  type="primary"
                  icon={<EnvironmentOutlined />}
                  style={{
                    fontSize: 14, fontWeight: 700, borderRadius: 4,
                    padding: '0 16px', height: 36, background: '#006ce4',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                  }}
                >
                  Xem trên bản đồ
                </Button>
              </div>
            </div>

            {/* Rating Box */}
            <div style={{
              background: '#f8f9fa',
              border: '1px solid #e2e8f0',
              borderRadius: 8,
              padding: 16,
              boxShadow: '0 2px 8px rgba(0,0,0,0.06)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <div style={{ fontSize: 16, fontWeight: 800, color: '#1a1a1a', marginBottom: 2 }}>
                    {ratingLabel || 'Chưa đánh giá'}
                  </div>
                  <div style={{ fontSize: 13, color: '#595959' }}>
                    {reviewStats.total > 0 ? `${reviewStats.total} đánh giá` : 'Đánh giá từ khách'}
                  </div>
                </div>
                <div style={{
                  background: '#003b95', color: '#fff',
                  borderRadius: '6px 6px 6px 0',
                  padding: '6px 10px', fontSize: 16, fontWeight: 800,
                  display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  {displayRating || '—'}
                </div>
              </div>

              <Divider style={{ margin: '16px 0' }} />

              <div style={{ fontSize: 13, color: '#333', lineHeight: 1.8, marginBottom: 16 }}>
                <div>Vị trí thuận lợi, gần trung tâm</div>
                <div>Nhân viên thân thiện và nhiệt tình</div>
                <div>Phòng sạch sẽ, tiện nghi đầy đủ</div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 13 }}>
                <span style={{ fontWeight: 600, color: '#1a1a1a' }}>Nhân viên phục vụ</span>
                <span style={{ fontWeight: 700, color: '#1a1a1a' }}>{displayRating || '—'}</span>
              </div>
              <div style={{ background: '#e2e8f0', height: 4, borderRadius: 2, marginTop: 6, overflow: 'hidden' }}>
                <div style={{ background: '#003b95', width: `${displayRating ? (Number(displayRating) / 10) * 100 : 0}%`, height: '100%' }}></div>
              </div>
            </div>
          </Col>
        </Row>
      </div>

      {/* ── Thumbnail strip ── */}
      {allImages.length > 1 && (
        <div className="container" style={{ padding: '8px 24px 16px' }}>
          <div className="thumbnail-strip" style={{ display: 'flex', gap: 4, overflowX: 'auto' }}>
            {allImages.slice(0, 6).map((img, i) => (
              <div key={i} style={{ width: 100, height: 72, flexShrink: 0, borderRadius: 3, overflow: 'hidden', position: 'relative', cursor: 'pointer' }} onClick={() => setPreviewIndex(i)}>
                <img src={img} alt="" className="hotel-thumbnail-img" referrerPolicy="no-referrer" />
                {i === 5 && allImages.length > 6 && (
                  <div style={{
                    position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.55)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#fff', fontSize: 13, fontWeight: 600, pointerEvents: 'none'
                  }}>
                    +{allImages.length - 6} ảnh
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Main content ── */}
      <div className="container" style={{ padding: '0 24px 48px' }}>
        <Row gutter={20}>
          {/* Left */}
          <Col xs={24} md={17}>

            {/* Overview */}
            <div style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 4, padding: 20, marginBottom: 16 }}>
              <h3 style={{ fontWeight: 700, fontSize: 16, marginBottom: 10 }}>Giới thiệu</h3>
              <p style={{ color: '#595959', lineHeight: 1.8, margin: 0, fontSize: 14 }}>
                {hotel.description || `${hotelName} là khách sạn cao cấp tại ${hotel.city || 'Việt Nam'}, mang đến trải nghiệm lưu trú sang trọng và tiện nghi hiện đại. Với đội ngũ nhân viên chuyên nghiệp, nhiệt tình, chúng tôi cam kết mang lại kỳ nghỉ tuyệt vời nhất cho quý khách.`}
              </p>
            </div>

            {/* Rooms & Prices */}
            {hotel.rooms && hotel.rooms.length > 0 && (
              <div id="rooms-section" style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 4, padding: 20, marginBottom: 16 }}>
                <h3 style={{ fontWeight: 700, fontSize: 16, marginBottom: 14 }}>Phòng trống</h3>
                {hotel.rooms.map(room => {
                  const roomImg = room.images?.[0]?.imageUrl;
                  return (
                    <div key={room.id} className="room-item-row" style={{
                      display: 'flex', gap: 14, padding: '12px 0',
                      borderBottom: '1px solid #f0f0f0', alignItems: 'center'
                    }}>
                      <div style={{ width: 90, height: 64, borderRadius: 4, overflow: 'hidden', flexShrink: 0, background: '#f0f0f0' }}>
                        {roomImg ? (
                          <img src={transformImageUrl(roomImg)} alt={room.name} referrerPolicy="no-referrer" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                        ) : (
                          <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, color: '#999' }}>Chưa có ảnh</div>
                        )}
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 700, fontSize: 14, color: '#006ce4', marginBottom: 2 }}>{room.name}</div>
                        <div style={{ fontSize: 12, color: '#595959' }}>
                          {room.type && <Tag style={{ fontSize: 11 }}>{room.type}</Tag>}
                          {room.capacity && <span>{room.capacity} khách</span>}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right', flexShrink: 0 }}>
                        <div style={{ fontSize: 18, fontWeight: 800 }}>{room.pricePerNight?.toLocaleString('vi-VN')}₫</div>
                        <div style={{ fontSize: 11, color: '#595959' }}>mỗi đêm</div>
                      </div>
                      <Button type="primary" size="small" onClick={() => navigate(`/booking/${id}/guest`, { state: { roomId: room.id } })}>
                        Đặt
                      </Button>
                    </div>
                  );
                })}
              </div>
            )}

            {/* Amenities */}
            <div id="amenities-section" style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 4, padding: 20, marginBottom: 16 }}>
              <h3 style={{ fontWeight: 700, fontSize: 16, marginBottom: 14 }}>Tiện nghi nổi bật</h3>
              <Row gutter={[10, 10]}>
                {AMENITIES.map((a) => (
                  <Col key={a.label} xs={12} sm={8}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                      <span>{a.label}</span>
                    </div>
                  </Col>
                ))}
              </Row>
            </div>

            {/* Policies */}
            <div id="policies-section" style={{ background: '#fff', border: '1px solid #e7e7e7', borderRadius: 4, padding: 20, marginBottom: 16 }}>
              <h3 style={{ fontWeight: 700, fontSize: 16, marginBottom: 12 }}>Quy tắc chung</h3>
              {[
                ['Nhận phòng', 'Từ 14:00'],
                ['Trả phòng', 'Trước 12:00'],
                ['Hủy phòng', 'Miễn phí trước 24 giờ'],
                ['Trẻ em', 'Được phép (liên hệ khách sạn)'],
                ['Vật nuôi', 'Không được phép'],
                ['Thanh toán', 'Tiền mặt & Thẻ ngân hàng'],
              ].map(([label, value]) => (
                <div key={label} style={{ display: 'flex', justifyContent: 'space-between', padding: '7px 0', borderBottom: '1px solid #f5f5f5', fontSize: 13 }}>
                  <span style={{ color: '#595959' }}>{label}</span>
                  <span style={{ fontWeight: 600 }}>{value}</span>
                </div>
              ))}
            </div>


          </Col>

          {/* Right — Sticky CTA */}
          <Col xs={24} md={7}>
            <div className="hotel-sticky-cta">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                <span className="rating-box-score">{displayRating ?? 'N/A'}</span>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{ratingLabel ?? 'Chưa đánh giá'}</div>
                  <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                    {reviewStats.total > 0 ? `${reviewStats.total} đánh giá` : 'Đánh giá chung'}
                  </div>
                </div>
              </div>

              <Divider style={{ margin: '10px 0' }} />

              <div className="hotel-cta-price">
                {basePrice !== null ? `${basePrice.toLocaleString('vi-VN')}₫` : 'Liên hệ'}
              </div>
              <div className="hotel-cta-label">mỗi đêm · đã bao gồm thuế và phí</div>

              <div style={{ background: '#f5f5f5', borderRadius: 4, padding: 10, margin: '10px 0', fontSize: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span>Nhận phòng:</span><span style={{ fontWeight: 600 }}>14:00</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Trả phòng:</span><span style={{ fontWeight: 600 }}>12:00</span>
                </div>
              </div>

              <Button
                type="primary"
                size="large"
                block
                style={{ height: 44, fontSize: 15, fontWeight: 700 }}
                onClick={() => navigate(`/booking/${id}/guest`)}
              >
                Đặt ngay
              </Button>

              <div style={{ textAlign: 'center', marginTop: 8, fontSize: 12, color: '#008234' }}>
                Không mất phí đặt phòng · Hủy miễn phí
              </div>

              <Divider style={{ margin: '12px 0' }} />

              <div style={{ fontSize: 12, color: '#595959' }}>
                <div style={{ marginBottom: 4 }}>Khách sạn: {hotelName}</div>
                <div style={{ marginBottom: 4 }}>Địa điểm: {hotel.city || hotel.address || 'Việt Nam'}</div>
                <div>Đánh giá: {displayRating ? `${displayRating}/10` : 'Chưa đánh giá'}</div>
              </div>
            </div>
          </Col>
        </Row>
      </div>

      {/* ── Photo Grid Lightbox ── */}
      {previewIndex !== null && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 99999, background: '#fff',
          display: 'flex', flexDirection: 'column'
        }}>
          {/* Header */}
          <div style={{ height: 60, borderBottom: '1px solid #e7e7e7', display: 'flex', alignItems: 'center', padding: '0 24px', justifyContent: 'space-between', background: '#fff', flexShrink: 0 }}>
            <div style={{ fontSize: 16, fontWeight: 700, textTransform: 'uppercase' }}>{hotelName}</div>
            <div style={{ display: 'flex', gap: 24, alignItems: 'center' }}>
              <Button type="primary" size="large" onClick={() => { setPreviewIndex(null); navigate(`/booking/${id}/guest`); }}>
                Đặt ngay
              </Button>
              <div 
                style={{ cursor: 'pointer', fontSize: 15, display: 'flex', alignItems: 'center', gap: 6, fontWeight: 500 }} 
                onClick={() => setPreviewIndex(null)}
              >
                Đóng <span style={{ fontSize: 20 }}>✕</span>
              </div>
            </div>
          </div>
          
          {/* Grid Content */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '24px 40px', background: '#f5f5f5' }}>
            <div style={{ maxWidth: 1400, margin: '0 auto' }}>
              <div style={{ marginBottom: 20 }}>
                <span style={{ fontSize: 24, fontWeight: 700 }}>Tất cả hình ảnh</span>
                <span style={{ marginLeft: 12, color: '#595959' }}>({allImages.length} ảnh)</span>
              </div>
              <Row gutter={[16, 16]}>
                {allImages.map((img, i) => (
                  <Col xs={24} sm={12} md={8} lg={6} key={i}>
                    <div style={{ 
                      borderRadius: 8, overflow: 'hidden', boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
                      transition: 'transform 0.2s', cursor: 'pointer', backgroundColor: '#fff'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.02)'}
                    onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
                    >
                      <img 
                        src={img} 
                        alt={`${hotelName} - ảnh ${i + 1}`}
                        style={{ width: '100%', height: 220, objectFit: 'cover', display: 'block' }} 
                        referrerPolicy="no-referrer"
                      />
                    </div>
                  </Col>
                ))}
              </Row>
            </div>
          </div>
        </div>
      )}

      <Drawer
        title={<span style={{ fontSize: 20, fontWeight: 700 }}>Đánh giá của khách về {hotelName}</span>}
        width={1000}
        onClose={() => setReviewDrawerVisible(false)}
        open={reviewDrawerVisible}
        placement="right"
        bodyStyle={{ padding: 0, background: '#f5f5f5' }}
        headerStyle={{ borderBottom: '1px solid #e7e7e7', padding: '24px' }}
        closeIcon={<span style={{ fontSize: 20 }}>✕</span>}
        maskStyle={{ backdropFilter: 'blur(8px)', background: 'rgba(0, 0, 0, 0.4)' }}
      >
        <HotelReviewAndChatPage isDrawer />
      </Drawer>

      <Footer />
    </div>
  );
};

export default HotelDetailPage;
