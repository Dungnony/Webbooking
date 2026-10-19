import React, { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import Header from '../components/Header';
import Footer from '../components/Footer';

export type PolicySection = 'terms' | 'privacy';

const CONTACT_EMAIL = 'duytuvvd@gmail.com';

// Keep these texts in line with what the system actually does (see BookingService, BookingExpiryService, ReviewService).
const Policies: React.FC<{ section: PolicySection }> = ({ section }) => {
  const { t } = useTranslation();

  useEffect(() => {
    document.getElementById(section)?.scrollIntoView({ behavior: 'smooth' });
  }, [section]);

  return (
    <div className="page-wrapper">
      <Header />

      <div style={{ background: '#f5f5f5', flex: 1, padding: '40px 0 64px' }}>
        <div className="container" style={{ maxWidth: 820 }}>
          <div style={{ background: '#fff', borderRadius: 8, padding: 32, lineHeight: 1.7, color: '#333' }}>
            <p style={{ color: '#8c8c8c', fontSize: 13 }}>
              {t('policies.disclaimer')}
            </p>

            <h1 id="terms" style={{ fontSize: 26, fontWeight: 800, marginTop: 8 }}>{t('auth.terms')}</h1>
            <h3>{t('policies.accountTitle')}</h3>
            <p>{t('policies.accountBody')}</p>
            <h3>{t('policies.bookingTitle')}</h3>
            <ul>
              <li>{t('policies.bookingPayment')}</li>
              <li>{t('policies.bookingExpiry')}</li>
              <li>{t('policies.bookingSelfCancel')}</li>
              <li>{t('policies.bookingPaidCancel')}</li>
              <li>{t('policies.bookingNoCancel')}</li>
            </ul>
            <h3>{t('policies.reviewsTitle')}</h3>
            <p>{t('policies.reviewsBody')}</p>

            <h1 id="privacy" style={{ fontSize: 26, fontWeight: 800, marginTop: 40 }}>{t('auth.privacy')}</h1>
            <h3>{t('policies.dataTitle')}</h3>
            <ul>
              <li>{t('policies.dataCredentials')}</li>
              <li>{t('policies.dataHistory')}</li>
              <li>{t('policies.dataGoogle')}</li>
            </ul>
            <h3>{t('policies.purposeTitle')}</h3>
            <ul>
              <li>{t('policies.purposeEmail')}</li>
              <li>{t('policies.purposePayment')}</li>
              <li>{t('policies.purposeAi')}</li>
            </ul>
            <h3>{t('policies.publicTitle')}</h3>
            <p>{t('policies.publicBody')}</p>
            <h3>{t('policies.contactTitle')}</h3>
            <p>
              {t('policies.contactBody')} <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
            </p>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default Policies;
