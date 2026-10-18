import React, { useState } from 'react';
import { Form, Input, Button, message, Divider } from 'antd';
import { useNavigate, Link } from 'react-router-dom';
import { GoogleLogin, CredentialResponse } from '@react-oauth/google';
import { authApi } from '../api';
import { saveSession } from '../utils/auth';
import { getErrorMessage, getErrorStatus } from '../utils/errors';

const Register: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState<'register' | 'otp'>('register');
  const [registerData, setRegisterData] = useState({ username: '', email: '', password: '' });
  const navigate = useNavigate();

  const onRegisterFinish = async (values: { username: string; email: string; password: string }) => {
    setLoading(true);
    try {
      await authApi.register(values.username, values.password, values.email);
      message.success('Mã OTP đã được gửi đến email của bạn! Vui lòng kiểm tra.');
      setRegisterData(values);
      setStep('otp');
    } catch (error) {
      if (getErrorStatus(error) === undefined) {
        message.error('Không thể kết nối server. Hãy kiểm tra backend đang chạy!');
      } else if (getErrorMessage(error, '') === 'Username already exists') {
        message.error('Tên đăng nhập đã tồn tại! Vui lòng chọn tên khác.');
      } else if (getErrorMessage(error, '') === 'Email already exists') {
        message.error('Email này đã có tài khoản! Hãy đăng nhập hoặc dùng "Quên mật khẩu".');
      } else {
        message.error(getErrorMessage(error, 'Thao tác thất bại. Vui lòng thử lại.'));
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
        message.success('Đăng ký tài khoản thành công! 🎉');
        navigate('/login');
      } else {
        message.error('Mã OTP không hợp lệ hoặc có lỗi xảy ra.');
      }
    } catch (error) {
      const errResponse = getErrorMessage(error, 'Xác thực OTP thất bại. Vui lòng thử lại.');

      if (errResponse === 'OTP invalid') {
        message.error('Mã OTP không đúng hoặc đã hết hạn! Vui lòng thử lại hoặc đăng ký lại để nhận mã mới.');
      } else if (errResponse === 'Email already exists') {
        message.error('Email này vừa được dùng cho tài khoản khác. Vui lòng đăng nhập.');
        setStep('register');
      } else if (errResponse === 'No registration request found') {
        message.error('Không tìm thấy yêu cầu đăng ký. Vui lòng đăng ký lại.');
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
      message.success('Đăng nhập bằng Google thành công! 👋');
      navigate(role === 'ADMIN' ? '/admin' : '/');
    } catch (error) {
      message.error('Lỗi đăng nhập Google: ' + getErrorMessage(error, 'Không xác định'));
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
              <h1 className="auth-title" style={{ fontSize: 28, fontWeight: 800, color: '#1a1a1a', marginBottom: 8 }}>Đăng ký tài khoản</h1>
              <p className="auth-subtitle" style={{ fontSize: 15, color: '#595959', marginBottom: 24 }}>Tạo tài khoản Booking.com của bạn</p>
              
              <Form name="register" onFinish={onRegisterFinish} layout="vertical" size="large">
                <Form.Item
                  name="username"
                  label={<span style={{ fontWeight: 600 }}>Tên đăng nhập</span>}
                  rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}
                >
                  <Input placeholder="Nhập tên đăng nhập của bạn" style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item
                  name="email"
                  label={<span style={{ fontWeight: 600 }}>Địa chỉ email</span>}
                  rules={[
                    { required: true, message: 'Vui lòng nhập email!' },
                    { type: 'email', message: 'Email không hợp lệ!' },
                  ]}
                >
                  <Input placeholder="Nhập địa chỉ email của bạn" style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item
                  name="password"
                  label={<span style={{ fontWeight: 600 }}>Mật khẩu</span>}
                  rules={[
                    { required: true, message: 'Vui lòng nhập mật khẩu!' },
                    { 
                      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,72}$/, 
                      message: "Mật khẩu phải chứa từ 8-72 ký tự bao gồm chữ hoa, chữ thường, số, ký tự đặc biệt và không có khoảng trống." 
                    },
                  ]}
                >
                  <Input.Password placeholder="Nhập mật khẩu" style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item
                  name="confirm"
                  label={<span style={{ fontWeight: 600 }}>Xác nhận mật khẩu</span>}
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
                  <Input.Password placeholder="Xác nhận mật khẩu" style={{ borderRadius: 8 }} />
                </Form.Item>

                <Form.Item style={{ marginTop: 12 }}>
                  <Button 
                    type="primary" 
                    htmlType="submit" 
                    block 
                    loading={loading} 
                    style={{ height: 44, fontSize: 15, fontWeight: 700, borderRadius: 8, background: '#006ce4' }}
                  >
                    Đăng ký ngay
                  </Button>
                </Form.Item>

                <Divider plain style={{ color: '#888', fontSize: 13 }}>hoặc đăng ký với</Divider>

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
              <h1 className="auth-title" style={{ fontSize: 24, fontWeight: 800, color: '#1a1a1a', marginBottom: 12 }}>Xác thực email</h1>
              <p className="auth-subtitle" style={{ fontSize: 15, color: '#595959', marginBottom: 28, lineHeight: 1.5 }}>
                Mã OTP đã được gửi đến email <b style={{ color: '#1a1a1a' }}>{registerData.email}</b>.<br />
                Vui lòng nhập mã gồm 6 chữ số vào bên dưới.
              </p>
              <Form name="otp" onFinish={onOtpFinish} layout="vertical" size="large">
                <Form.Item
                  name="otp"
                  rules={[{ required: true, message: 'Vui lòng nhập mã OTP!' }]}
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
                    Xác minh & Kích hoạt
                  </Button>
                </Form.Item>

                <Button type="link" block onClick={() => setStep('register')} style={{ color: '#595959', fontWeight: 500 }}>
                  Quay lại thay đổi thông tin
                </Button>
              </Form>
            </div>
          )}

          <Divider plain style={{ color: '#8c8c8c', fontSize: 13, margin: '24px 0' }}>hoặc</Divider>

          <div style={{ textAlign: 'center', fontSize: 14 }}>
            <span style={{ color: '#595959' }}>Đã có tài khoản? </span>
            <Link to="/login" style={{ color: '#006ce4', fontWeight: 700, marginLeft: 4 }}>
              Đăng nhập
            </Link>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginTop: 24, fontSize: 12, color: '#929292', lineHeight: 1.5 }}>
          Bằng cách đăng ký, bạn đồng ý với{' '}
          <Link to="/terms" style={{ color: '#006ce4', fontWeight: 500 }}>Điều khoản sử dụng</Link>{' '}và{' '}
          <Link to="/privacy" style={{ color: '#006ce4', fontWeight: 500 }}>Chính sách bảo mật</Link> của chúng tôi.
        </div>
      </div>
    </div>
  );
};

export default Register;
