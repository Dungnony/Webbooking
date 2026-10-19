import React from 'react';
import { useTranslation } from 'react-i18next';
import type { Language } from '../i18n';

const LANGUAGES: { code: Language; label: string }[] = [
  { code: 'vi', label: 'VI' },
  { code: 'en', label: 'EN' },
];

/** Compact VI | EN toggle for the header; the choice is remembered by the i18n setup. */
const LanguageSwitcher: React.FC = () => {
  const { i18n, t } = useTranslation();

  return (
    <div role="group" aria-label={t('common.language')} style={{ display: 'flex', border: '1px solid rgba(255,255,255,0.6)', borderRadius: 6, overflow: 'hidden' }}>
      {LANGUAGES.map(({ code, label }) => {
        const active = i18n.language === code;
        return (
          <button
            key={code}
            type="button"
            onClick={() => { void i18n.changeLanguage(code); }}
            aria-pressed={active}
            style={{
              padding: '4px 8px', border: 'none', cursor: 'pointer', fontWeight: 700, fontSize: 12,
              background: active ? '#fff' : 'transparent', color: active ? '#003b95' : '#fff',
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
};

export default LanguageSwitcher;
