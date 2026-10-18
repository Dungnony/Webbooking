import React, { useState } from 'react';
import { Form, Input, Button, Steps, message } from 'antd';
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api';
import { getErrorMessage } from '../utils/errors';

const ForgotPassword: React.FC = () => {
  const navigate = useNavigate();
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
      message.success(`Mã xác nhận đã được gửi đến email liên kết`);
    } catch (error) {
      message.error(getErrorMessage(error, 'Tên đăng nhập không tồn tại hoặc lỗi hệ thống.'));
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (values: { otp: string; password: string }) => {
    if (values.otp.length < 6) {
      message.error('Mã OTP không hợp lệ!');
      return;
    }
    setLoading(true);
    try {
      await authApi.resetPassword(username, values.otp, values.password);
      setStep(2);
      message.success('Đặt lại mật khẩu thành công!');
    } catch (error) {
      message.error(getErrorMessage(error, 'Mã OTP không hợp lệ hoặc lỗi hệ thống.'));
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
          <h1 className="auth-title">Quên mật khẩu</h1>
          <p className="auth-subtitle">
            {step === 0 && 'Nhập tên đăng nhập để nhận mã khôi phục mật khẩu'}
            {step === 1 && `Nhập mã OTP đã gửi đến ${maskedEmail} và tạo mật khẩu mới`}
          </p>

          <Steps
            current={step}
            size="small"
            labelPlacement="vertical"
            style={{ marginBottom: 28 }}
            items={[
              { title: 'Tên đăng nhập' },
              { title: 'Tạo mật khẩu' },
              { title: 'Hoàn tất' },
            ]}
          />

          {step === 0 && (
            <Form onFinish={handleUsernameSubmit} layout="vertical">
              <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}>
                <Input prefix={<UserOutlined />} placeholder="Nhập tên đăng nhập" size="large" />
              </Form.Item>
              <Button type="primary" htmlType="submit" block size="large" loading={loading} style={{ fontWeight: 700 }}>
                Nhận mã xác nhận
              </Button>
            </Form>
          )}

          {step === 1 && (
            <Form onFinish={handleResetPassword} layout="vertical">
              <Form.Item name="otp" label="Mã xác nhận OTP" rules={[{ required: true, message: 'Vui lòng nhập OTP!' }]}>
                <Input
                  prefix={<SafetyOutlined />}
                  placeholder="Nhập mã OTP (6 số)"
                  size="large"
                  maxLength={6}
                  style={{ letterSpacing: 2, textAlign: 'center', fontSize: 16 }}
                />
              </Form.Item>
              
              <Form.Item name="password" label="Mật khẩu mới" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu mới!', min: 6 }]}>
                <Input.Password prefix={<LockOutlined />} placeholder="Ít nhất 6 ký tự" size="large" />
              </Form.Item>
              
              <Form.Item
                name="confirm"
                label="Xác nhận mật khẩu"
                dependencies={['password']}
                rules={[
                  { required: true, message: 'Vui lòng xác nhận mật khẩu!' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) return Promise.resolve();
                      return Promise.reject(new Error('Mật khẩu không khớp!'));
                    },
                  }),
                ]}
              >
                <Input.Password prefix={<LockOutlined />} placeholder="Nhập lại mật khẩu" size="large" />
              </Form.Item>

              <Button type="primary" htmlType="submit" block size="large" loading={loading} style={{ fontWeight: 700 }}>
                Xác nhận & Đặt lại mật khẩu
              </Button>
              <Button type="link" block style={{ marginTop: 8 }} onClick={() => setStep(0)}>
                Quay lại
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
                Đổi mật khẩu thành công!
              </h2>
              <p style={{ fontSize: 15, color: '#595959', marginBottom: 32, lineHeight: 1.6 }}>
                Mật khẩu của bạn đã được cập nhật an toàn. Bạn có thể sử dụng mật khẩu mới để đăng nhập ngay bây giờ.
              </p>
              <Button 
                type="primary" 
                size="large" 
                block
                onClick={() => navigate('/login')} 
                style={{ height: 48, fontSize: 16, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}
              >
                Đăng nhập ngay
              </Button>
            </div>
          )}

          {step === 0 && (
            <div style={{ textAlign: 'center', marginTop: 20 }}>
              <Link to="/login" style={{ color: '#006ce4', fontSize: 14 }}>
                ← Quay lại đăng nhập
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
