import React from 'react';
import Header from '../components/Header';
import Footer from '../components/Footer';

const Deals: React.FC = () => {
  return (
    <div className="page-wrapper">
      <Header />

      {/* Hero */}
      <div style={{ background: 'linear-gradient(135deg, #cc0000, #ff6600)', padding: '48px 0' }}>
        <div className="container" style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 12 }}>🔥</div>
          <h1 style={{ color: '#fff', fontSize: 36, fontWeight: 800, marginBottom: 8 }}>Ưu đãi & Khuyến mãi</h1>
          <p style={{ color: 'rgba(255,255,255,0.9)', fontSize: 18 }}>Những deal cực hot không thể bỏ lỡ!</p>
        </div>
      </div>

      {/* Deals grid */}
      <div style={{ background: '#f5f5f5', flex: 1, padding: '40px 0 64px' }}>
        <div className="container">
          <div style={{ textAlign: 'center', padding: '80px 0', color: '#929292' }}>
            <div style={{ fontSize: 48 }}>🔥</div>
            <p style={{ marginTop: 12, fontSize: 16 }}>Chưa có ưu đãi nào. Vui lòng kiểm tra lại sau!</p>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default Deals;
