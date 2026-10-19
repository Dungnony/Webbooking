import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

// City names are proper nouns and the search matches them as stored, so they are not translated.
const APP_CITIES = ['Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Nha Trang', 'Phú Quốc', 'Đà Lạt', 'Vũng Tàu'];
const CONTACT_EMAIL = 'duytuvvd@gmail.com';

const Footer: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const handleCityClick = (city: string) => {
    navigate(`/search?city=${encodeURIComponent(city)}`);
  };

  return (
    <footer className="footer-container">
      {/* Top section: Popular destinations in app */}
      <div className="footer-destinations">
        <h2>{t('footer.popularDestinations')}</h2>
        <div className="footer-city-grid">
          {APP_CITIES.map((city) => (
            <a
              key={city}
              className="footer-city-link"
              onClick={(e) => {
                e.preventDefault();
                handleCityClick(city);
              }}
            >
              {t('footer.hotelsIn', { city })}
            </a>
          ))}
        </div>
      </div>

      <hr style={{ border: 'none', borderTop: '1px solid #e6e6e6', margin: '30px 0' }} />

      {/* Main links columns */}
      <div className="footer-links-grid">
        <div className="footer-column">
          <h3>{t('footer.mainFeatures')}</h3>
          <ul>
            <li><a onClick={() => navigate('/')}>{t('footer.home')}</a></li>
            <li><a onClick={() => navigate('/search')}>{t('footer.searchHotels')}</a></li>
            <li><a onClick={() => navigate('/deals')}>{t('footer.deals')}</a></li>
            <li><a onClick={() => navigate('/wishlist')}>{t('footer.wishlist')}</a></li>
            <li><a onClick={() => navigate('/my-bookings')}>{t('footer.myBookings')}</a></li>
            <li><a onClick={() => navigate('/profile')}>{t('footer.profile')}</a></li>
          </ul>
        </div>

        <div className="footer-column">
          <h3>{t('footer.systemAndPartners')}</h3>
          <ul>
            <li><a onClick={() => navigate('/login')}>{t('footer.signIn')}</a></li>
            <li><a onClick={() => navigate('/register')}>{t('footer.register')}</a></li>
            <li><a href="#">{t('footer.listProperty')}</a></li>
            <li><a href="#">{t('footer.partnerHelp')}</a></li>
            <li><a onClick={() => navigate('/terms')}>{t('footer.terms')}</a></li>
            <li><a onClick={() => navigate('/privacy')}>{t('footer.privacy')}</a></li>
          </ul>
        </div>

        <div className="footer-column">
          <h3>{t('footer.contact')}</h3>
          <ul>
            <li>
              {t('footer.contactUs')}<br />
              <a href={`mailto:${CONTACT_EMAIL}`} style={{ fontWeight: 'bold', marginTop: '4px', display: 'inline-block' }}>
                {CONTACT_EMAIL}
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
