import React, { useEffect } from 'react';
import Header from '../components/Header';
import Footer from '../components/Footer';

export type PolicySection = 'terms' | 'privacy';

const CONTACT_EMAIL = 'duytuvvd@gmail.com';

// Keep these texts in line with what the system actually does (see BookingService, BookingExpiryService, ReviewService).
const Policies: React.FC<{ section: PolicySection }> = ({ section }) => {
  useEffect(() => {
    document.getElementById(section)?.scrollIntoView({ behavior: 'smooth' });
  }, [section]);

  return (
    <div className="page-wrapper">
      <Header />

      <div style={{ background: '#f5f5f5', flex: 1, padding: '40px 0 64px' }}>
        <div className="container" style={{ maxWidth: 820 }}>
          <div style={{ background: '#fff', borderRadius: 8, padding: 32, lineHeight: 1.7, color: '#333' }}>
            <p style={{ color: '#8c8c8c', fontSize: 13 }}>
              Đây là sản phẩm đồ án môn học của Nhóm 2 (RIPT1307), không phải dịch vụ thương mại.
            </p>

            <h1 id="terms" style={{ fontSize: 26, fontWeight: 800, marginTop: 8 }}>Điều khoản sử dụng</h1>
            <h3>1. Tài khoản</h3>
            <p>
              Mỗi email chỉ được dùng cho một tài khoản. Bạn chịu trách nhiệm giữ bí mật mật khẩu và mã OTP.
              Mã OTP có hiệu lực 5 phút và bị hủy sau 5 lần nhập sai.
            </p>
            <h3>2. Đặt phòng và thanh toán</h3>
            <ul>
              <li>Thanh toán bằng chuyển khoản qua mã VietQR; hệ thống tự xác nhận khi nhận được tiền.</li>
              <li>Đơn chưa thanh toán sẽ tự hủy sau 30 phút kể từ lúc đặt để trả phòng cho khách khác.</li>
              <li>Bạn có thể tự hủy đơn chưa thanh toán trước ngày nhận phòng.</li>
              <li>Đơn đã thanh toán cần gửi yêu cầu hủy; quản trị viên sẽ xem xét và liên hệ hoàn tiền.</li>
              <li>Không thể hủy đơn từ ngày nhận phòng trở đi.</li>
            </ul>
            <h3>3. Đánh giá</h3>
            <p>
              Chỉ khách đã đặt và thanh toán phòng tại một khách sạn mới được đánh giá khách sạn đó, mỗi người một lần.
              Nội dung đánh giá phải trung thực và không xúc phạm người khác.
            </p>

            <h1 id="privacy" style={{ fontSize: 26, fontWeight: 800, marginTop: 40 }}>Chính sách bảo mật</h1>
            <h3>Dữ liệu chúng tôi lưu</h3>
            <ul>
              <li>Tên đăng nhập, email và mật khẩu (chỉ lưu dạng mã hóa BCrypt, không lưu mật khẩu gốc).</li>
              <li>Lịch sử đặt phòng, thanh toán, đánh giá, danh sách yêu thích và ảnh đại diện bạn tải lên.</li>
              <li>Khi đăng nhập bằng Google, chúng tôi chỉ nhận địa chỉ email đã được Google xác minh.</li>
            </ul>
            <h3>Mục đích sử dụng</h3>
            <ul>
              <li>Gửi mã OTP, thông báo đăng nhập và thông báo về đơn đặt phòng.</li>
              <li>Đối soát thanh toán chuyển khoản theo mã đơn.</li>
              <li>Tin nhắn gửi cho trợ lý AI được chuyển tới OpenAI kèm thông tin khách sạn đang xem để tạo câu trả lời.</li>
            </ul>
            <h3>Hiển thị công khai</h3>
            <p>Đánh giá của bạn hiển thị kèm tên đăng nhập; email không bao giờ được hiển thị cho người khác.</p>
            <h3>Liên hệ</h3>
            <p>
              Mọi câu hỏi về dữ liệu cá nhân, vui lòng gửi về <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
            </p>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
};

export default Policies;
