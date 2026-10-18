import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Row, Col } from 'antd';
import Header from '../components/Header';
import Footer from '../components/Footer';
import SearchWidget from '../components/SearchWidget';
import HotelCard from '../components/HotelCard';
import { hotelApi, HotelWithStats } from '../api';
import { useWishlist } from '../context/useWishlist';
import { TrophyTwoTone, SafetyCertificateTwoTone, CustomerServiceTwoTone, StarTwoTone } from '@ant-design/icons';
import AIAssistantWidget from '../components/AIAssistantWidget';
import { cachedFetch, HOTEL_LIST_TTL } from '../utils/apiCache';


const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const [hotels, setHotels] = useState<HotelWithStats[]>([]);
  const [loading, setLoading] = useState(true);
  const { isInWishlist, toggleWish } = useWishlist();

  useEffect(() => {
    cachedFetch('hotels_all', () => hotelApi.getAll(), HOTEL_LIST_TTL)
      .then(data => setHotels(data || []))
      .catch(err => console.error('Failed to load hotels', err))
      .finally(() => setLoading(false));
  }, []);


  return (
    <div className="page-wrapper">
      <Header />

      {/* ── HERO (Booking.com style) ── */}
      <section className="bk-hero">
        <div className="bk-hero-inner">
          <h1 className="bk-hero-title">Tìm chỗ nghỉ tiếp theo của bạn</h1>
          <p className="bk-hero-subtitle">Tìm kiếm ưu đãi khách sạn, nhà nghỉ và nhiều hơn nữa...</p>
          <div className="bk-hero-search">
            <SearchWidget />
          </div>
        </div>
      </section>

      {/* ── FEATURED HOTELS ── */}
      <section className="bk-section">
        <div className="bk-section-inner">
          <div className="bk-section-header">
            <div>
              <h2 className="bk-section-title">Khách sạn nổi bật</h2>
              <p className="bk-section-subtitle">Được đánh giá cao và đặt nhiều nhất tuần này</p>
            </div>
            <button className="bk-section-link" onClick={() => navigate('/search')}>Xem tất cả →</button>
          </div>

          {loading ? (
            <Row gutter={[16, 20]}>
              {[...Array(8)].map((_, i) => (
                <Col key={i} xs={24} sm={12} md={6}>
                  <div style={{
                    borderRadius: 12,
                    overflow: 'hidden',
                    background: '#fff',
                    boxShadow: '0 2px 12px rgba(0,0,0,0.07)',
                  }}>
                    {/* Image skeleton with shimmer */}
                    <div style={{
                      width: '100%',
                      height: 180,
                      background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)',
                      backgroundSize: '200% 100%',
                      animation: 'shimmer 1.4s ease-in-out infinite',
                    }} />
                    <div style={{ padding: '12px 14px 16px' }}>
                      {/* Title */}
                      <div style={{
                        height: 16, width: '80%', borderRadius: 6,
                        background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)',
                        backgroundSize: '200% 100%',
                        animation: 'shimmer 1.4s ease-in-out infinite',
                        marginBottom: 8,
                      }} />
                      {/* City */}
                      <div style={{
                        height: 12, width: '50%', borderRadius: 6,
                        background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)',
                        backgroundSize: '200% 100%',
                        animation: 'shimmer 1.4s ease-in-out infinite',
                        marginBottom: 14,
                      }} />
                      {/* Price */}
                      <div style={{
                        height: 14, width: '60%', borderRadius: 6,
                        background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)',
                        backgroundSize: '200% 100%',
                        animation: 'shimmer 1.4s ease-in-out infinite',
                      }} />
                    </div>
                  </div>
                </Col>
              ))}
            </Row>
          ) : hotels.length === 0 ? (
            <div className="bk-empty-state">
              <div style={{ fontSize: 56 }}>🏨</div>
              <p>Chưa có khách sạn nào. Vui lòng kiểm tra backend!</p>
            </div>
          ) : (
            <Row gutter={[16, 20]}>
              {hotels.slice(0, 8).map((hotel) => {
                const hotelId = hotel.id;
                if (hotelId === undefined) return null;
                return (
                  <Col key={hotelId} xs={24} sm={12} md={6}>
                    <HotelCard
                      hotel={hotel}
                      wished={isInWishlist(hotelId)}
                      onWishToggle={() => toggleWish(hotelId)}
                      onClick={() => navigate(`/hotels/${hotelId}`)}
                    />
                  </Col>
                );
              })}
            </Row>
          )}
        </div>
      </section>

      {/* ── WHY BOOKING ── */}
      <section className="bk-why-section">
        <div className="bk-section-inner">
          <h2 className="bk-section-title" style={{ textAlign: 'center', marginBottom: 36 }}>
            Tại sao chọn chúng tôi?
          </h2>
          <Row gutter={[24, 24]}>
            {[
              { icon: <TrophyTwoTone twoToneColor="#006ce4" style={{ fontSize: 36 }} />, title: 'Giá tốt nhất đảm bảo', desc: 'Chúng tôi cam kết bạn sẽ nhận được giá tốt nhất hoặc hoàn lại tiền' },
              { icon: <SafetyCertificateTwoTone twoToneColor="#006ce4" style={{ fontSize: 36 }} />, title: 'Thanh toán an toàn', desc: 'Dữ liệu của bạn được mã hóa và bảo vệ bởi hệ thống bảo mật cao cấp' },
              { icon: <CustomerServiceTwoTone twoToneColor="#006ce4" style={{ fontSize: 36 }} />, title: 'Hỗ trợ 24/7', desc: 'Đội ngũ hỗ trợ khách hàng luôn sẵn sàng giải đáp mọi thắc mắc của bạn' },
              { icon: <StarTwoTone twoToneColor="#febb02" style={{ fontSize: 36 }} />, title: 'Triệu đánh giá thực', desc: 'Tất cả đánh giá đều đến từ khách đã lưu trú thực sự' },
            ].map((item) => (
              <Col key={item.title} xs={24} sm={12} md={6}>
                <div className="bk-why-card">
                  <div className="bk-why-icon">{item.icon}</div>
                  <div className="bk-why-title">{item.title}</div>
                  <div className="bk-why-desc">{item.desc}</div>
                </div>
              </Col>
            ))}
          </Row>
        </div>
      </section>

      <Footer />
      <AIAssistantWidget /> 
    </div>
  );
};

export default HomePage;
