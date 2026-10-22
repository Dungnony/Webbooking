import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Empty, Input, Modal, Spin, Tag } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { fetchBankApps, type BankApp, type MobilePlatform } from '../utils/bankApps';

interface BankAppPickerProps {
  open: boolean;
  platform: MobilePlatform;
  onClose: () => void;
  onSelect: (app: BankApp) => void;
}

/** Popup chọn app ngân hàng (danh sách deeplink của VietQR). */
const BankAppPicker: React.FC<BankAppPickerProps> = ({ open, platform, onClose, onSelect }) => {
  const { t } = useTranslation();
  const [apps, setApps] = useState<BankApp[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [query, setQuery] = useState('');

  useEffect(() => {
    if (!open || apps.length > 0) return;
    setLoading(true);
    setFailed(false);
    fetchBankApps(platform)
      .then(setApps)
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  }, [open, platform, apps.length]);

  const visibleApps = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return apps;
    return apps.filter(app => `${app.appName} ${app.bankName} ${app.appId}`.toLowerCase().includes(q));
  }, [apps, query]);

  const renderBody = () => {
    if (loading) {
      return <div style={{ textAlign: 'center', padding: '32px 0' }}><Spin /></div>;
    }
    if (failed) {
      return <Empty description={t('payment.bankApp.loadFailed')} />;
    }
    if (visibleApps.length === 0) {
      return <Empty description={t('payment.bankApp.noMatch')} />;
    }
    return (
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
        gap: 10,
        maxHeight: 420,
        overflowY: 'auto',
        padding: 2,
      }}>
        {visibleApps.map(app => (
          <button
            key={app.appId}
            type="button"
            className="bank-app-option"
            onClick={() => onSelect(app)}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 6,
              padding: '12px 8px',
              background: '#fff',
              border: '1px solid #e7e7e7',
              borderRadius: 10,
              cursor: 'pointer',
            }}
          >
            <img src={app.appLogo} alt="" width={44} height={44} style={{ borderRadius: 10 }} />
            <span style={{ fontWeight: 600, fontSize: 13, color: '#1a1a1a', textAlign: 'center' }}>{app.appName}</span>
            <Tag color={app.autofill ? 'green' : 'default'} style={{ marginRight: 0 }}>
              {app.autofill ? t('payment.bankApp.autofill') : t('payment.bankApp.openOnly')}
            </Tag>
          </button>
        ))}
      </div>
    );
  };

  return (
    <Modal open={open} onCancel={onClose} footer={null} title={t('payment.bankApp.pickerTitle')} width={600}>
      <Input
        prefix={<SearchOutlined />}
        allowClear
        placeholder={t('payment.bankApp.search')}
        value={query}
        onChange={e => setQuery(e.target.value)}
        style={{ marginBottom: 12 }}
      />
      {renderBody()}
    </Modal>
  );
};

export default BankAppPicker;
