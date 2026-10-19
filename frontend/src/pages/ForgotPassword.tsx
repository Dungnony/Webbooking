import React, { useState } from 'react';
import { Form, Input, Button, Steps, message } from 'antd';
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../api';
import { getErrorMessage } from '../utils/errors';
import { STRONG_PASSWORD_PATTERN } from '../utils/passwordPolicy';

const OTP_LENGTH = 6;

const ForgotPassword: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [step, setStep] = useState(0);
  const [username, setUsername] = useState('');
  const [maskedEmail, setMaskedEmail] = useState('');
  const [loading, setLoading] = useState(false);

  const handleUsernameSubmit = async (values: { username: string }) => {
    setLoading(true);
    try {
      const emailRes = await authApi.forgotPassword(values.username);
      setUsername(values.username);
      setMaskedEmail(emailRes);
      setStep(1);
      message.success(t('auth.forgot.codeSent'));
    } catch (error) {
      message.error(getErrorMessage(error, t('auth.forgot.usernameNotFound')));
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (values: { otp: string; password: string }) => {
    if (values.otp.length < OTP_LENGTH) {
      message.error(t('auth.forgot.otpInvalid'));
      return;
    }
    setLoading(true);
    try {
      await authApi.resetPassword(username, values.otp, values.password);
      setStep(2);
      message.success(t('auth.forgot.resetSuccess'));
    } catch (error) {
      message.error(getErrorMessage(error, t('auth.forgot.resetFailed')));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-header">
        <div className="auth-header-logo" onClick={() => navigate('/')}>
          Booking<span style={{ color: '#febb02' }}>.com</span>
        </div>
      </div>

      <div className="auth-container">
        <div className="auth-box">
          <h1 className="auth-title">{t('auth.forgot.title')}</h1>
          <p className="auth-subtitle">
            {step === 0 && t('auth.forgot.step0Subtitle')}
            {step === 1 && t('auth.forgot.step1Subtitle', { email: maskedEmail })}
          </p>

          <Steps
            current={step}
            size="small"
            labelPlacement="vertical"
            style={{ marginBottom: 28 }}
            items={[
              { title: t('auth.username') },
              { title: t('auth.forgot.stepPassword') },
              { title: t('auth.forgot.stepDone') },
            ]}
          />

          {step === 0 && (
            <Form onFinish={handleUsernameSubmit} layout="vertical">
              <Form.Item name="username" label={t('auth.username')} rules={[{ required: true, message: t('auth.usernameRequired') }]}>
                <Input prefix={<UserOutlined />} placeholder={t('auth.usernamePlaceholder')} size="large" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block size="large" loading={loading} style={{ fontWeight: 700 }}>
                {t('auth.forgot.sendCode')}
              </Button>
            </Form>
          )}

          {step === 1 && (
            <Form onFinish={handleResetPassword} layout="vertical">
              <Form.Item name="otp" label={t('auth.forgot.otpLabel')} rules={[{ required: true, message: t('auth.otpRequired') }]}>
                <Input
                  prefix={<SafetyOutlined />}
                  placeholder={t('auth.forgot.otpPlaceholder')}
                  size="large"
                  maxLength={OTP_LENGTH}
                  style={{ letterSpacing: 2, textAlign: 'center', fontSize: 16 }}
                />
              </Form.Item>

              <Form.Item
                name="password"
                label={t('auth.forgot.newPassword')}
                rules={[
                  { required: true, message: t('auth.forgot.newPasswordRequired') },
                  { pattern: STRONG_PASSWORD_PATTERN, message: t('auth.passwordPolicy') },
                ]}
              >
                <Input.Password prefix={<LockOutlined />} placeholder={t('auth.forgot.newPassword')} size="large" />
              </Form.Item>

              <Form.Item
                name="confirm"
                label={t('auth.confirmPassword')}
                dependencies={['password']}
                rules={[
                  { required: true, message: t('auth.confirmRequired') },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) return Promise.resolve();
                      return Promise.reject(new Error(t('auth.passwordMismatch')));
                    },
                  }),
                ]}
              >
                <Input.Password prefix={<LockOutlined />} placeholder={t('auth.confirmPassword')} size="large" />
              </Form.Item>

              <Button type="primary" htmlType="submit" block size="large" loading={loading} style={{ fontWeight: 700 }}>
                {t('auth.forgot.resetSubmit')}
              </Button>
              <Button type="link" block style={{ marginTop: 8 }} onClick={() => setStep(0)}>
                {t('auth.forgot.back')}
              </Button>
            </Form>
          )}

          {step === 2 && (
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <div style={{
                width: 64, height: 64, background: '#e8f5e9', borderRadius: '50%',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                margin: '0 auto 24px'
              }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#2e7d32" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                  <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>
              </div>
              <h2 style={{ fontSize: 24, fontWeight: 800, color: '#1a1a1a', marginBottom: 12 }}>
                {t('auth.forgot.successTitle')}
              </h2>
              <p style={{ fontSize: 15, color: '#595959', marginBottom: 32, lineHeight: 1.6 }}>
                {t('auth.forgot.successBody')}
              </p>
              <Button
                type="primary"
                size="large"
                block
                onClick={() => navigate('/login')}
                style={{ height: 48, fontSize: 16, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}
              >
                {t('auth.forgot.loginNow')}
              </Button>
            </div>
          )}

          {step === 0 && (
            <div style={{ textAlign: 'center', marginTop: 20 }}>
              <Link to="/login" style={{ color: '#006ce4', fontSize: 14 }}>
                {t('auth.forgot.backToLogin')}
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
