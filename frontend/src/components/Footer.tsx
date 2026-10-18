import React from 'react';
import { useNavigate } from 'react-router-dom';

const Footer: React.FC = () => {
  const navigate = useNavigate();

  const appCities = [
    'Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Nha Trang', 'Phú Quốc', 'Đà Lạt', 'Vũng Tàu'
  ];

  const handleCityClick = (city: string) => {
    navigate(`/search?city=${encodeURIComponent(city)}`);
  };

  return (
    <footer className="footer-container">
      {/* Top section: Popular destinations in app */}
      <div className="footer-destinations">
        <h2>Các điểm đến phổ biến</h2>
        <div className="footer-city-grid">
          {appCities.map((city) => (
            <a
              key={city}
              className="footer-city-link"
              onClick={(e) => {
                e.preventDefault();
                handleCityClick(city);
              }}
            >
              Khách sạn {city}
            </a>
          ))}
        </div>
      </div>

      <hr style={{ border: 'none', borderTop: '1px solid #e6e6e6', margin: '30px 0' }} />

      {/* Main links columns */}
      <div className="footer-links-grid">
        <div className="footer-column">
          <h3>Chức năng chính</h3>
          <ul>
            <li><a onClick={() => navigate('/')}>Trang chủ</a></li>
            <li><a onClick={() => navigate('/search')}>Tìm kiếm khách sạn</a></li>
            <li><a onClick={() => navigate('/deals')}>Ưu đãi hấp dẫn</a></li>
            <li><a onClick={() => navigate('/wishlist')}>Danh sách yêu thích</a></li>
            <li><a onClick={() => navigate('/my-bookings')}>Đặt phòng của tôi</a></li>
            <li><a onClick={() => navigate('/profile')}>Hồ sơ cá nhân</a></li>
          </ul>
        </div>

        <div className="footer-column">
          <h3>Hệ thống & Đối tác</h3>
          <ul>
            <li><a onClick={() => navigate('/login')}>Đăng nhập hệ thống</a></li>
            <li><a onClick={() => navigate('/register')}>Đăng ký tài khoản</a></li>
            <li><a href="#">Đăng chỗ nghỉ của Quý vị</a></li>
            <li><a href="#">Trợ giúp đối tác</a></li>
            <li><a onClick={() => navigate('/terms')}>Điều khoản dịch vụ</a></li>
            <li><a onClick={() => navigate('/privacy')}>Chính sách Bảo mật</a></li>
          </ul>
        </div>

        <div className="footer-column">
          <h3>Thông tin liên hệ</h3>
          <ul>
            <li>
              Liên hệ với chúng tôi qua:<br />
              <a href="mailto:duytuvvd@gmail.com" style={{ fontWeight: 'bold', marginTop: '4px', display: 'inline-block' }}>
                duytuvvd@gmail.com
              </a>
            </li>
          </ul>
        </div>
      </div>

      <div className="footer-copyright">
        © 2026 BookingApp-ALL RIGHTS RESERVED.
      </div>
    </footer>
  );
};

export default Footer;
