import React, { useEffect, useState } from 'react';
import { Form, Input, Button, message, Divider, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { UserOutlined, SaveOutlined, MailOutlined, LockOutlined, HeartOutlined, CalendarOutlined, HomeOutlined } from '@ant-design/icons';
import { userApi } from '../api';
import type { User } from '../api-generated';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { getErrorMessage } from '../utils/errors';

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
  const [emailLoading, setEmailLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  // The address the OTP was sent to; confirming always uses it, even if the input was edited afterwards.
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);
  const [emailForm] = Form.useForm<EmailFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const localUsername = localStorage.getItem('username') || 'User';

  useEffect(() => {
    userApi.getMe()
      .then(user => {
        setCurrentUser(user);
        emailForm.setFieldsValue({ username: user.username, email: user.email });
      })
      .catch(err => {
        message.error(getErrorMessage(err, 'Không thể tải hồ sơ.'));
        emailForm.setFieldsValue({ username: localUsername });
      });
  }, [emailForm, localUsername]);

  const handleSendEmailOtp = async () => {
    const { email } = await emailForm.validateFields(['email']);
    setEmailLoading(true);
    try {
      const maskedEmail = await userApi.requestEmailChange(email.trim());
      setPendingEmail(email.trim());
      message.success(`Đã gửi mã OTP tới ${maskedEmail}. Vui lòng kiểm tra hộp thư.`);
    } catch (err) {
      message.error(getErrorMessage(err, 'Không thể gửi mã xác nhận.'));
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
      message.success('Đổi email thành công! 🎉');
    } catch (err) {
      message.error(getErrorMessage(err, 'Xác nhận đổi email thất bại.'));
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
      message.success('Đổi mật khẩu thành công! 🎉');
    } catch (err) {
      message.error(getErrorMessage(err, 'Đổi mật khẩu thất bại.'));
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.clear();
    message.success('Đã đăng xuất!');
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
              {localStorage.getItem('avatarUrl') ? (
                <img src={localStorage.getItem('avatarUrl') as string} alt="avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                localUsername.charAt(0).toUpperCase()
              )}
            </div>
            <div className="profile-username">{localUsername}</div>
            <div className="profile-email">{currentUser?.email || 'Chưa cập nhật email'}</div>

            {currentUser?.role === 'ADMIN' && (
              <div style={{ textAlign: 'center', marginBottom: 12 }}>
                <Tag color="red">Quản trị viên</Tag>
              </div>
            )}

            <div className="profile-menu">
              <div className="profile-menu-item active">
                <UserOutlined /> Thông tin cá nhân
              </div>
              {currentUser?.role !== 'ADMIN' && (
                <>
                  <div className="profile-menu-item" onClick={() => navigate('/my-bookings')}>
                    <CalendarOutlined /> Lịch sử đặt phòng
                  </div>
                  <div className="profile-menu-item" onClick={() => navigate('/wishlist')}>
                    <HeartOutlined /> Khách sạn đã lưu
                  </div>
                </>
              )}
            </div>
          </aside>

          {/* Content */}
          <main className="profile-content">
            <div className="profile-content-card">
              <div className="profile-content-title">Quản lý tài khoản</div>
              
              <Form form={emailForm} layout="vertical" size="large" style={{ maxWidth: 500 }}>
                <Form.Item
                  name="username"
                  label="Tên đăng nhập"
                  extra="Tên đăng nhập không thể thay đổi."
                >
                  <Input prefix={<UserOutlined style={{ color: '#888' }} />} disabled />
                </Form.Item>

                <Form.Item
                  name="email"
                  label="Email"
                  extra="Email mới chỉ được lưu sau khi bạn nhập mã OTP gửi tới địa chỉ đó."
                  rules={[
                    { required: true, message: 'Email không được trống!' },
                    { type: 'email', message: 'Email không đúng định dạng!' }
                  ]}
                >
                  <Input prefix={<MailOutlined style={{ color: '#888' }} />} disabled={pendingEmail !== null} />
                </Form.Item>

                {pendingEmail === null ? (
                  <Form.Item>
                    <Button onClick={handleSendEmailOtp} loading={emailLoading} icon={<MailOutlined />}>
                      Gửi mã xác nhận
                    </Button>
                  </Form.Item>
                ) : (
                  <>
                    <Form.Item
                      name="otp"
                      label={`Mã OTP đã gửi tới ${pendingEmail}`}
                      rules={[{ required: true, message: 'Vui lòng nhập mã OTP!' }]}
                    >
                      <Input maxLength={6} placeholder="Nhập mã 6 số" />
                    </Form.Item>
                    <Form.Item>
                      <div style={{ display: 'flex', gap: 12 }}>
                        <Button type="primary" onClick={handleConfirmEmail} loading={emailLoading} icon={<SaveOutlined />}>
                          Xác nhận đổi email
                        </Button>
                        <Button onClick={handleCancelEmailChange} disabled={emailLoading}>Hủy</Button>
                      </div>
                    </Form.Item>
                  </>
                )}
              </Form>

              <Divider />

              <Form form={passwordForm} layout="vertical" onFinish={onChangePassword} size="large" style={{ maxWidth: 500 }}>
                <Form.Item
                  name="newPassword"
                  label="Mật khẩu mới"
                  rules={[{ required: true, message: 'Vui lòng nhập mật khẩu mới!' }]}
                >
                  <Input.Password prefix={<LockOutlined style={{ color: '#888' }} />} placeholder="Nhập mật khẩu mới" />
                </Form.Item>

                <Form.Item>
                  <Button type="primary" htmlType="submit" loading={passwordLoading} icon={<SaveOutlined />}>
                    Đổi mật khẩu
                  </Button>
                </Form.Item>
              </Form>

              <Divider />
              <div style={{ display: 'flex', gap: 12 }}>
                <Button danger onClick={handleLogout}>Đăng xuất</Button>
                <Button 
                  onClick={() => navigate(currentUser?.role === 'ADMIN' ? '/admin' : '/')} 
                  icon={<HomeOutlined />}
                >
                  {currentUser?.role === 'ADMIN' ? 'Quay về trang Admin' : 'Quay về trang chủ'}
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
