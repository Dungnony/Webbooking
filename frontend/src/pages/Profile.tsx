import React, { useEffect, useState } from 'react';
import { Form, Input, Button, message, Divider, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { UserOutlined, SaveOutlined, MailOutlined, LockOutlined, HeartOutlined, CalendarOutlined, HomeOutlined } from '@ant-design/icons';
import { userApi, UserProfile } from '../api';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { clearSession } from '../utils/auth';
import { getErrorMessage } from '../utils/errors';
import { STRONG_PASSWORD_PATTERN } from '../utils/passwordPolicy';

const OTP_LENGTH = 6;

interface EmailFormValues {
  username: string;
  email: string;
  otp?: string;
}

interface PasswordFormValues {
  newPassword: string;
}

// Route is wrapped in <AuthGuard>, so a token is always present here.
const Profile: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [emailLoading, setEmailLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(null);
  // The address the OTP was sent to; confirming always uses it, even if the input was edited afterwards.
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);
  const [emailForm] = Form.useForm<EmailFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const localUsername = localStorage.getItem('username') || 'User';
  const isAdmin = currentUser?.role === 'ADMIN';

  useEffect(() => {
    userApi.getMe()
      .then(user => {
        setCurrentUser(user);
        emailForm.setFieldsValue({ username: user.username, email: user.email });
      })
      .catch(err => {
        message.error(getErrorMessage(err, t('profile.loadFailed')));
        emailForm.setFieldsValue({ username: localUsername });
      });
  }, [emailForm, localUsername, t]);

  const handleSendEmailOtp = async () => {
    const { email } = await emailForm.validateFields(['email']);
    setEmailLoading(true);
    try {
      const maskedEmail = await userApi.requestEmailChange(email.trim());
      setPendingEmail(email.trim());
      message.success(t('profile.otpSent', { email: maskedEmail }));
    } catch (err) {
      message.error(getErrorMessage(err, t('profile.otpSendFailed')));
    } finally {
      setEmailLoading(false);
    }
  };

  const handleConfirmEmail = async () => {
    if (!pendingEmail) return;
    const { otp } = await emailForm.validateFields(['otp']);
    setEmailLoading(true);
    try {
      const updated = await userApi.confirmEmailChange(pendingEmail, (otp ?? '').trim());
      setCurrentUser(updated);
      setPendingEmail(null);
      emailForm.setFieldsValue({ email: updated.email, otp: undefined });
      message.success(t('profile.emailChanged'));
    } catch (err) {
      message.error(getErrorMessage(err, t('profile.emailChangeFailed')));
    } finally {
      setEmailLoading(false);
    }
  };

  const handleCancelEmailChange = () => {
    setPendingEmail(null);
    emailForm.setFieldsValue({ email: currentUser?.email, otp: undefined });
  };

  const onChangePassword = async (values: PasswordFormValues) => {
    setPasswordLoading(true);
    try {
      const updated = await userApi.updateMe({ newPassword: values.newPassword });
      setCurrentUser(updated);
      passwordForm.resetFields();
      message.success(t('profile.passwordChanged'));
    } catch (err) {
      message.error(getErrorMessage(err, t('profile.passwordChangeFailed')));
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleLogout = () => {
    clearSession();
    message.success(t('profile.loggedOut'));
    navigate('/login');
  };

  return (
    <div className="page-wrapper" style={{ background: '#f5f5f5' }}>
      <Header />

      <div className="container">
        <div className="profile-layout">
          {/* Sidebar */}
          <aside className="profile-sidebar">
            <div className="profile-avatar" style={{ overflow: 'hidden', padding: 0 }}>
              {currentUser?.avatarUrl ? (
                <img src={currentUser.avatarUrl} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                localUsername.charAt(0).toUpperCase()
              )}
            </div>
            <div className="profile-username">{localUsername}</div>
            <div className="profile-email">{currentUser?.email || t('profile.noEmail')}</div>

            {isAdmin && (
              <div style={{ textAlign: 'center', marginBottom: 12 }}>
                <Tag color="red">{t('profile.adminTag')}</Tag>
              </div>
            )}

            <div className="profile-menu">
              <div className="profile-menu-item active">
                <UserOutlined /> {t('profile.personalInfo')}
              </div>
              {!isAdmin && (
                <>
                  <div className="profile-menu-item" onClick={() => navigate('/my-bookings')}>
                    <CalendarOutlined /> {t('header.myBookings')}
                  </div>
                  <div className="profile-menu-item" onClick={() => navigate('/wishlist')}>
                    <HeartOutlined /> {t('profile.savedHotels')}
                  </div>
                </>
              )}
            </div>
          </aside>

          {/* Content */}
          <main className="profile-content">
            <div className="profile-content-card">
              <div className="profile-content-title">{t('profile.title')}</div>

              <Form form={emailForm} layout="vertical" size="large" style={{ maxWidth: 500 }}>
                <Form.Item
                  name="username"
                  label={t('auth.username')}
                  extra={t('profile.usernameLocked')}
                >
                  <Input prefix={<UserOutlined style={{ color: '#888' }} />} disabled />
                </Form.Item>

                <Form.Item
                  name="email"
                  label="Email"
                  extra={t('profile.emailHint')}
                  rules={[
                    { required: true, message: t('auth.emailRequired') },
                    { type: 'email', message: t('auth.emailInvalid') }
                  ]}
                >
                  <Input prefix={<MailOutlined style={{ color: '#888' }} />} disabled={pendingEmail !== null} />
                </Form.Item>

                {pendingEmail === null ? (
                  <Form.Item>
                    <Button onClick={handleSendEmailOtp} loading={emailLoading} icon={<MailOutlined />}>
                      {t('auth.forgot.sendCode')}
                    </Button>
                  </Form.Item>
                ) : (
                  <>
                    <Form.Item
                      name="otp"
                      label={t('profile.otpSentTo', { email: pendingEmail })}
                      rules={[{ required: true, message: t('auth.otpRequired') }]}
                    >
                      <Input maxLength={OTP_LENGTH} placeholder={t('auth.forgot.otpPlaceholder')} />
                    </Form.Item>
                    <Form.Item>
                      <div style={{ display: 'flex', gap: 12 }}>
                        <Button type="primary" onClick={handleConfirmEmail} loading={emailLoading} icon={<SaveOutlined />}>
                          {t('profile.confirmEmailChange')}
                        </Button>
                        <Button onClick={handleCancelEmailChange} disabled={emailLoading}>{t('common.cancel')}</Button>
                      </div>
                    </Form.Item>
                  </>
                )}
              </Form>

              <Divider />

              <Form form={passwordForm} layout="vertical" onFinish={onChangePassword} size="large" style={{ maxWidth: 500 }}>
                <Form.Item
                  name="newPassword"
                  label={t('auth.forgot.newPassword')}
                  rules={[
                    { required: true, message: t('auth.forgot.newPasswordRequired') },
                    { pattern: STRONG_PASSWORD_PATTERN, message: t('auth.passwordPolicy') },
                  ]}
                >
                  <Input.Password prefix={<LockOutlined style={{ color: '#888' }} />} placeholder={t('auth.forgot.newPassword')} />
                </Form.Item>

                <Form.Item>
                  <Button type="primary" htmlType="submit" loading={passwordLoading} icon={<SaveOutlined />}>
                    {t('profile.changePassword')}
                  </Button>
                </Form.Item>
              </Form>

              <Divider />
              <div style={{ display: 'flex', gap: 12 }}>
                <Button danger onClick={handleLogout}>{t('header.logout')}</Button>
                <Button
                  onClick={() => navigate(isAdmin ? '/admin' : '/')}
                  icon={<HomeOutlined />}
                >
                  {isAdmin ? t('profile.backToAdmin') : t('profile.backToHome')}
                </Button>
              </div>
            </div>
          </main>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default Profile;
