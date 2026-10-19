import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Row, Col, Button } from 'antd';
import { HeartFilled, DeleteOutlined } from '@ant-design/icons';
import Header from '../components/Header';
import Footer from '../components/Footer';
import HotelCard from '../components/HotelCard';
import { hotelApi, HotelWithStats } from '../api';
import { useWishlist } from '../context/useWishlist';
import { cachedFetch, HOTEL_DETAIL_TTL } from '../utils/apiCache';

const Wishlist: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { wishlist, removeWish, clearAll } = useWishlist();
  const [hotels, setHotels] = useState<HotelWithStats[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (wishlist.length === 0) {
      setHotels([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    // Cache từng KS theo ID, tái dùng cache từ trang chi tiết nếu có
    Promise.all(
      wishlist.map(id => cachedFetch(`hotel_${id}`, () => hotelApi.getById(Number(id)), HOTEL_DETAIL_TTL))
    )
      .then(hotels => setHotels(hotels.filter(Boolean)))
      .catch(err => console.error('Failed to load wishlist hotels', err))
      .finally(() => setLoading(false));
  }, [wishlist]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', background: '#fff' }}>
      <Header />

      <div style={{ flex: 1, padding: '40px 0' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto', padding: '0 24px' }}>
          
          {/* Header Section */}
          <div style={{ marginBottom: 32 }}>
            <Button 
              type="text" 
              onClick={() => navigate('/')} 
              style={{ padding: 0, marginBottom: 16, color: '#006ce4', fontWeight: 600, fontSize: 15 }}
            >
              {t('common.backToHome')}
            </Button>
            <h1 style={{ fontSize: 32, fontWeight: 800, margin: '0 0 8px 0', color: '#1a1a1a' }}>
              {t('header.wishlist')}
            </h1>
            <p style={{ margin: 0, fontSize: 16, color: '#595959' }}>
              {t('wishlist.savedCount', { count: wishlist.length })}
            </p>
          </div>

          {loading ? (
            <Row gutter={[24, 24]}>
              {[...Array(wishlist.length || 4)].map((_, i) => (
                <Col key={i} xs={24} sm={12} lg={8}>
                  <div style={{ borderRadius: 12, overflow: 'hidden', background: '#fff', border: '1px solid #e7e7e7' }}>
                    <div style={{ width: '100%', height: 220, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                    <div style={{ padding: '16px' }}>
                      <div style={{ height: 18, width: '80%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite', marginBottom: 12 }} />
                      <div style={{ height: 14, width: '50%', borderRadius: 6, background: 'linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%)', backgroundSize: '200% 100%', animation: 'shimmer 1.4s ease-in-out infinite' }} />
                    </div>
                  </div>
                </Col>
              ))}
            </Row>
          ) : hotels.length === 0 ? (
            <div style={{ 
              background: '#fff', borderRadius: 12, padding: '80px 24px', 
              textAlign: 'center', border: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column', alignItems: 'center' 
            }}>
              <div style={{ 
                width: 80, height: 80, borderRadius: '50%', background: '#f5f5f5', 
                display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24 
              }}>
                <HeartFilled style={{ fontSize: 32, color: '#e2e8f0' }} />
              </div>
              <h2 style={{ fontSize: 24, fontWeight: 800, color: '#1a1a1a', margin: '0 0 12px 0' }}>{t('wishlist.emptyTitle')}</h2>
              <p style={{ color: '#595959', marginBottom: 32, fontSize: 16, maxWidth: 400 }}>
                {t('wishlist.emptyBody')}
              </p>
              <Button type="primary" size="large" onClick={() => navigate('/')} style={{ fontWeight: 700, height: 48, padding: '0 32px' }}>
                {t('wishlist.explore')}
              </Button>
            </div>
          ) : (
            <>
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 24 }}>
                <Button icon={<DeleteOutlined />} danger onClick={clearAll} size="middle" style={{ borderRadius: 6, fontWeight: 500 }}>
                  {t('wishlist.clearAll')}
                </Button>
              </div>

              <Row gutter={[24, 24]}>
                {hotels.map(hotel => {
                  const hotelId = hotel.id;
                  if (hotelId === undefined) return null;
                  return (
                    <Col key={hotelId} xs={24} sm={12} lg={8}>
                      <HotelCard
                        hotel={hotel}
                        wished={true}
                        onWishToggle={() => removeWish(hotelId)}
                        onClick={() => navigate(`/hotels/${hotelId}`)}
                      />
                    </Col>
                  );
                })}
              </Row>
            </>
          )}
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default Wishlist;
