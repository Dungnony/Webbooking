import React, { useState } from 'react';
import { Form, Input, Button, message, Divider } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { GoogleLogin, CredentialResponse } from '@react-oauth/google';
import { authApi } from '../api';
import { saveSession } from '../utils/auth';
import { getErrorMessage, getErrorStatus } from '../utils/errors';
import { STRONG_PASSWORD_PATTERN } from '../utils/passwordPolicy';

const Register: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState<'register' | 'otp'>('register');
  const [registerData, setRegisterData] = useState({ username: '', email: '', password: '' });
  const navigate = useNavigate();
  const { t } = useTranslation();

  const onRegisterFinish = async (values: { username: string; email: string; password: string }) => {
    setLoading(true);
    try {
      await authApi.register(values.username, values.password, values.email);
      message.success(t('auth.otpSentToast'));
      setRegisterData(values);
      setStep('otp');
    } catch (error) {
      // The backend answers with these fixed English codes; anything else is shown as returned.
      if (getErrorStatus(error) === undefined) {
        message.error(t('auth.serverUnreachable'));
      } else if (getErrorMessage(error, '') === 'Username already exists') {
        message.error(t('auth.usernameExists'));
      } else if (getErrorMessage(error, '') === 'Email already exists') {
        message.error(t('auth.emailExists'));
      } else {
        message.error(getErrorMessage(error, t('auth.genericFailed')));
      }
    } finally {
      setLoading(false);
    }
  };

  const onOtpFinish = async (values: { otp: string }) => {
    setLoading(true);
    try {
      const res = await authApi.verifyOtp(registerData.email, values.otp.trim());
      const msg = res.data;

      if (msg === 'Register success' || msg?.includes('success')) {
        message.success(t('auth.registerSuccess'));
        navigate('/login');
      } else {
        message.error(t('auth.otpInvalidGeneric'));
      }
    } catch (error) {
      const errResponse = getErrorMessage(error, t('auth.otpVerifyFailed'));

      if (errResponse === 'OTP invalid') {
        message.error(t('auth.otpInvalidOrExpired'));
      } else if (errResponse === 'Email already exists') {
        message.error(t('auth.emailTakenMeanwhile'));
        setStep('register');
      } else if (errResponse === 'No registration request found') {
        message.error(t('auth.noRegistration'));
        setStep('register');
      } else {
        message.error(errResponse);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSuccess = async (credentialResponse: CredentialResponse) => {
    if (!credentialResponse.credential) return;
    setLoading(true);
    try {
      const token = await authApi.googleLogin(credentialResponse.credential);
      const role = saveSession(token, 'GoogleUser');
      message.success(t('auth.googleSuccess'));
      navigate(role === 'ADMIN' ? '/admin' : '/');
    } catch (error) {
      message.error(t('auth.googleError', { reason: getErrorMessage(error, t('payment.unknownError')) }));
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
          {step === 'register' ? (
            <>
              <h1 className="auth-title" style={{ fontSize: 28, fontWeight: 800, color: '#1a1a1a', marginBottom: 8 }}>{t('auth.registerTitle')}</h1>
              <p className="auth-subtitle" style={{ fontSize: 15, color: '#595959', marginBottom: 24 }}>{t('auth.registerSubtitle')}</p>

              <Form name="register" onFinish={onRegisterFinish} layout="vertical" size="large">
                <Form.Item
                  name="username"
                  label={<span style={{ fontWeight: 600 }}>{t('auth.username')}</span>}
                  rules={[{ required: true, message: t('auth.usernameRequired') }]}
                >
                  <Input placeholder={t('auth.usernamePlaceholderRegister')} style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item
                  name="email"
                  label={<span style={{ fontWeight: 600 }}>{t('auth.email')}</span>}
                  rules={[
                    { required: true, message: t('auth.emailRequired') },
                    { type: 'email', message: t('auth.emailInvalid') },
                  ]}
                >
                  <Input placeholder={t('auth.emailPlaceholder')} style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item
                  name="password"
                  label={<span style={{ fontWeight: 600 }}>{t('auth.password')}</span>}
                  rules={[
                    { required: true, message: t('auth.passwordRequired') },
                    { pattern: STRONG_PASSWORD_PATTERN, message: t('auth.passwordPolicy') },
                  ]}
                >
                  <Input.Password placeholder={t('auth.passwordPlaceholder')} style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item
                  name="confirm"
                  label={<span style={{ fontWeight: 600 }}>{t('auth.confirmPassword')}</span>}
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
                  <Input.Password placeholder={t('auth.confirmPassword')} style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item style={{ marginTop: 12 }}>
                  <Button
                    type="primary"
                    htmlType="submit"
                    block
                    loading={loading}
                    style={{ height: 44, fontSize: 15, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}
                  >
                    {t('auth.registerNow')}
                  </Button>
                </Form.Item>

                <Divider plain style={{ color: '#888', fontSize: 13 }}>{t('auth.orRegisterWith')}</Divider>

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
            </>
          ) : (
            <div style={{ textAlign: 'center' }}>
              <div style={{
                width: 56, height: 56, background: '#ebf3ff', borderRadius: '50%',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                margin: '0 auto 20px'
              }}>
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#006ce4" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                  <polyline points="22,6 12,13 2,6"></polyline>
                </svg>
              </div>
              <h1 className="auth-title" style={{ fontSize: 24, fontWeight: 800, color: '#1a1a1a', marginBottom: 12 }}>{t('auth.verifyTitle')}</h1>
              <p className="auth-subtitle" style={{ fontSize: 15, color: '#595959', marginBottom: 28, lineHeight: 1.5 }}>
                {t('auth.otpSentTo')} <b style={{ color: '#1a1a1a' }}>{registerData.email}</b>.<br />
                {t('auth.enterOtp6')}
              </p>
              <Form name="otp" onFinish={onOtpFinish} layout="vertical" size="large">
                <Form.Item
                  name="otp"
                  rules={[{ required: true, message: t('auth.otpRequired') }]}
                >
                  <Input
                    placeholder="• • • • • •"
                    maxLength={6}
                    style={{
                      letterSpacing: 14,
                      textAlign: 'center',
                      fontSize: 24,
                      fontWeight: 700,
                      padding: '10px 0',
                      borderRadius: 10,
                      background: '#f8f8f8',
                      border: '1px solid #e0e0e0'
                    }}
                  />
                </Form.Item>

                <Form.Item style={{ marginTop: 24 }}>
                  <Button
                    type="primary"
                    htmlType="submit"
                    block
                    loading={loading}
                    style={{ height: 44, fontSize: 15, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}
                  >
                    {t('auth.verifyActivate')}
                  </Button>
                </Form.Item>

                <Button type="link" block onClick={() => setStep('register')} style={{ color: '#595959', fontWeight: 500 }}>
                  {t('auth.backToEdit')}
                </Button>
              </Form>
            </div>
          )}

          <Divider plain style={{ color: '#8c8c8c', fontSize: 13, margin: '24px 0' }}>{t('auth.or')}</Divider>

          <div style={{ textAlign: 'center', fontSize: 14 }}>
            <span style={{ color: '#595959' }}>{t('auth.haveAccount')} </span>
            <Link to="/login" style={{ color: '#006ce4', fontWeight: 700, marginLeft: 4 }}>
              {t('auth.loginTitle')}
            </Link>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginTop: 24, fontSize: 12, color: '#929292', lineHeight: 1.5 }}>
          {t('auth.agreeRegister')}{' '}
          <Link to="/terms" style={{ color: '#006ce4', fontWeight: 500 }}>{t('auth.terms')}</Link>{' '}{t('auth.and')}{' '}
          <Link to="/privacy" style={{ color: '#006ce4', fontWeight: 500 }}>{t('auth.privacy')}</Link>{t('auth.agreeSuffix')}
        </div>
      </div>
    </div>
  );
};

export default Register;
