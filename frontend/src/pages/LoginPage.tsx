import React, { useState } from 'react';
import { Form, Input, Button, message, Divider } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
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

  const redirectByRole = (role: string) => navigate(role === 'ADMIN' ? '/admin' : '/');

  const handleGoogleSuccess = async (credentialResponse: CredentialResponse) => {
    if (!credentialResponse.credential) return;
    setLoading(true);
    try {
      const token = await authApi.googleLogin(credentialResponse.credential);
      const role = saveSession(token, 'GoogleUser');
      message.success('Đăng nhập bằng Google thành công! 👋');
      redirectByRole(role);
    } catch (error) {
      message.error('Lỗi đăng nhập Google: ' + getErrorMessage(error, 'Không xác định'));
    } finally {
      setLoading(false);
    }
  };

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(values.username, values.password);
      const role = saveSession(res.data, values.username);
      message.success('Đăng nhập thành công! 👋');
      redirectByRole(role);
    } catch (error) {
      const status = getErrorStatus(error);
      if (status === TOO_MANY_REQUESTS) {
        message.error(getErrorMessage(error, 'Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.'));
      } else if (status === 401 || status === 400) {
        message.error('Tên đăng nhập hoặc mật khẩu không đúng!');
      } else if (status === undefined) {
        message.error('Không thể kết nối server. Kiểm tra backend đang chạy!');
      } else {
        message.error('Có lỗi xảy ra: ' + getErrorMessage(error, String(status)));
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
          <h1 className="auth-title" style={{ fontSize: 28, fontWeight: 800, color: '#1a1a1a', marginBottom: 8 }}>Đăng nhập</h1>
          <p className="auth-subtitle" style={{ fontSize: 15, color: '#595959', marginBottom: 24 }}>Chào mừng bạn quay lại Booking.com</p>

          <Form name="login" onFinish={onFinish} layout="vertical" size="large">
            <Form.Item
              name="username"
              label={<span style={{ fontWeight: 600 }}>Tên đăng nhập</span>}
              rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#929292' }} />}
                placeholder="Nhập tên đăng nhập"
                style={{ borderRadius: 8 }}
              />
            </Form.Item>

            <Form.Item
              name="password"
              label={<span style={{ fontWeight: 600 }}>Mật khẩu</span>}
              rules={[{ required: true, message: 'Vui lòng nhập mật khẩu!' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#929292' }} />}
                placeholder="Nhập mật khẩu"
                iconRender={(visible) => (visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />)}
                style={{ borderRadius: 8 }}
              />
            </Form.Item>

            <div style={{ textAlign: 'right', marginBottom: 16, marginTop: -8 }}>
              <Link to="/forgot-password" style={{ fontSize: 13, color: '#006ce4' }}>
                Quên mật khẩu?
              </Link>
            </div>

            <Form.Item style={{ marginTop: 12 }}>
              <Button type="primary" htmlType="submit" block loading={loading} style={{ height: 44, fontSize: 15, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}>
                Đăng nhập
              </Button>
            </Form.Item>

            <Divider plain style={{ color: '#888', fontSize: 13 }}>hoặc đăng nhập với</Divider>

            <div style={{ display: 'flex', justifyContent: 'center' }}>
              <GoogleLogin
                onSuccess={handleGoogleSuccess}
                onError={() => {
                  message.error('Đăng nhập Google thất bại');
                }}
                useOneTap
              />
            </div>
          </Form>

          <div style={{ textAlign: 'center', marginTop: 24, fontSize: 14 }}>
            <span style={{ color: '#595959' }}>Chưa có tài khoản? </span>
            <Link to="/register" style={{ color: '#006ce4', fontWeight: 700, marginLeft: 4 }}>
              Đăng ký miễn phí
            </Link>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginTop: 24, fontSize: 12, color: '#929292', lineHeight: 1.5 }}>
          Bằng cách đăng nhập, bạn đồng ý với{' '}
          <Link to="/terms" style={{ color: '#006ce4', fontWeight: 500 }}>Điều khoản sử dụng</Link>{' '}và{' '}
          <Link to="/privacy" style={{ color: '#006ce4', fontWeight: 500 }}>Chính sách bảo mật</Link> của chúng tôi.
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
