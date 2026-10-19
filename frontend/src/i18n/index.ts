import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import vi from './locales/vi';
import en from './locales/en';

export type Language = 'vi' | 'en';

export const LANGUAGE_STORAGE_KEY = 'lang';

const readStoredLanguage = (): Language => {
  try {
    return localStorage.getItem(LANGUAGE_STORAGE_KEY) === 'en' ? 'en' : 'vi';
  } catch {
    // localStorage can be blocked (private mode); Vietnamese is the default.
    return 'vi';
  }
};

const initialLanguage = readStoredLanguage();

void i18n.use(initReactI18next).init({
  resources: { vi: { translation: vi }, en: { translation: en } },
  lng: initialLanguage,
  fallbackLng: 'vi',
  // React already escapes rendered values.
  interpolation: { escapeValue: false },
});

document.documentElement.lang = initialLanguage;
i18n.on('languageChanged', language => {
  document.documentElement.lang = language;
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // Not persisting the choice is acceptable; it still applies for this visit.
  }
});

/** BCP 47 locale for dates and times in the current language. */
export const dateLocale = () => (i18n.language === 'en' ? 'en-US' : 'vi-VN');

export default i18n;
