import 'i18next';
import type { Translations } from './locales/vi';

// Makes t('...') keys type-checked: a typo or a key missing from vi.ts fails `tsc`.
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: {
      translation: Translations;
    };
  }
}
