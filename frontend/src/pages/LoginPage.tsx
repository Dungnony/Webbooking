import React, { useState } from 'react';
import { Form, Input, Button, message, Divider } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { UserOutlined, LockOutlined, EyeInvisibleOutlined, EyeTwoTone } from '@ant-design/icons';
import { GoogleLogin, CredentialResponse } from '@react-oauth/google';
import { authApi } from '../api';
import { saveSession } from '../utils/auth';
import { getErrorMessage, getErrorStatus } from '../utils/errors';

// Backend locks an account for 15 minutes after repeated wrong passwords.
const TOO_MANY_REQUESTS = 429;

const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { t } = useTranslation();

  const redirectByRole = (role: string) => navigate(role === 'ADMIN' ? '/admin' : '/');

  const handleGoogleSuccess = async (credentialResponse: CredentialResponse) => {
    if (!credentialResponse.credential) return;
    setLoading(true);
    try {
      const token = await authApi.googleLogin(credentialResponse.credential);
      const role = saveSession(token, 'GoogleUser');
      message.success(t('auth.googleSuccess'));
      redirectByRole(role);
    } catch (error) {
      message.error(t('auth.googleError', { reason: getErrorMessage(error, t('payment.unknownError')) }));
    } finally {
      setLoading(false);
    }
  };

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(values.username, values.password);
      const role = saveSession(res.data, values.username);
      message.success(t('auth.loginSuccess'));
      redirectByRole(role);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === TOO_MANY_REQUESTS) {
        message.error(getErrorMessage(error, t('auth.tooManyAttempts')));
      } else if (status === 401 || status === 400) {
        message.error(t('auth.wrongCredentials'));
      } else if (status === undefined) {
        message.error(t('auth.serverUnreachable'));
      } else {
        message.error(t('auth.genericError', { reason: getErrorMessage(error, String(status)) }));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page" style={{ fontFamily: 'Inter, sans-serif' }}>
      {/* Phần Đầu Trang (Header) */}
      <div className="auth-header">
        <div className="auth-header-logo" onClick={() => navigate('/')}>
          Booking<span style={{ color: '#febb02' }}>.com</span>
        </div>
      </div>

      <div className="auth-container">
        <div className="auth-box" style={{ padding: '40px 32px' }}>
          <h1 className="auth-title" style={{ fontSize: 28, fontWeight: 800, color: '#1a1a1a', marginBottom: 8 }}>{t('auth.loginTitle')}</h1>
          <p className="auth-subtitle" style={{ fontSize: 15, color: '#595959', marginBottom: 24 }}>{t('auth.loginSubtitle')}</p>

          <Form name="login" onFinish={onFinish} layout="vertical" size="large">
            <Form.Item
              name="username"
              label={<span style={{ fontWeight: 600 }}>{t('auth.username')}</span>}
              rules={[{ required: true, message: t('auth.usernameRequired') }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#929292' }} />}
                placeholder={t('auth.usernamePlaceholder')}
                style={{ borderRadius: 8 }}
              />
            </Form.Item>

            <Form.Item
              name="password"
              label={<span style={{ fontWeight: 600 }}>{t('auth.password')}</span>}
              rules={[{ required: true, message: t('auth.passwordRequired') }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#929292' }} />}
                placeholder={t('auth.passwordPlaceholder')}
                iconRender={(visible) => (visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />)}
                style={{ borderRadius: 8 }}
              />
            </Form.Item>

            <div style={{ textAlign: 'right', marginBottom: 16, marginTop: -8 }}>
              <Link to="/forgot-password" style={{ fontSize: 13, color: '#006ce4' }}>
                {t('auth.forgotPassword')}
              </Link>
            </div>

            <Form.Item style={{ marginTop: 12 }}>
              <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 44, fontSize: 15, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}>
                {t('auth.loginTitle')}
              </Button>
            </Form.Item>

            <Divider plain style={{ color: '#888', fontSize: 13 }}>{t('auth.orSignInWith')}</Divider>

            <div style={{ display: 'flex', justifyContent: 'center' }}>
              <GoogleLogin
                onSuccess={handleGoogleSuccess}
                onError={() => {
                  message.error(t('auth.googleFailed'));
                }}
                useOneTap
              />
            </div>
          </Form>

          <div style={{ textAlign: 'center', marginTop: 24, fontSize: 14 }}>
            <span style={{ color: '#595959' }}>{t('auth.noAccount')} </span>
            <Link to="/register" style={{ color: '#006ce4', fontWeight: 700, marginLeft: 4 }}>
              {t('auth.registerFree')}
            </Link>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginTop: 24, fontSize: 12, color: '#929292', lineHeight: 1.5 }}>
          {t('auth.agreeSignIn')}{' '}
          <Link to="/terms" style={{ color: '#006ce4', fontWeight: 500 }}>{t('auth.terms')}</Link>{' '}{t('auth.and')}{' '}
          <Link to="/privacy" style={{ color: '#006ce4', fontWeight: 500 }}>{t('auth.privacy')}</Link>{t('auth.agreeSuffix')}
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
