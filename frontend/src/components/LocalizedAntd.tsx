import React, { useEffect } from 'react';
import { ConfigProvider } from 'antd';
import viVN from 'antd/es/locale/vi_VN';
import enUS from 'antd/es/locale/en_US';
import moment from 'moment';
import 'moment/locale/vi';
import { useTranslation } from 'react-i18next';

/** Keeps Ant Design's built-in texts (date pickers, tables, modals) and moment in the selected language. */
const LocalizedAntd: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { i18n } = useTranslation();
  const isEnglish = i18n.language === 'en';

  useEffect(() => {
    moment.locale(isEnglish ? 'en' : 'vi');
  }, [isEnglish]);

  return <ConfigProvider locale={isEnglish ? enUS : viVN}>{children}</ConfigProvider>;
};

export default LocalizedAntd;
