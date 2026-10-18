import type { AxiosResponse } from 'axios';
import {
  Api,
  Booking,
  ContentType,
  CreateBookingRequestDto,
  Hotel,
  ProcessBookingRequestDto,
  Review,
  Room,
  User,
} from './api-generated';
import { getErrorMessage } from './utils/errors';

// ─── Khởi tạo Swagger client ──────────────────────────────────
// baseUrl = '/proxy' để dựa vào Vite proxy (dev) hoặc Vercel rewrite (prod)
const swaggerApi = new Api({
  baseURL: '/proxy',
  // Tự động gắn JWT token cho mọi request
  securityWorker: () => {
    const token = localStorage.getItem('token');
    if (token) {
      return { headers: { Authorization: `Bearer ${token}` } };
    }
    return {};
  },
});

export default swaggerApi;

// ─── Kiểu dữ liệu chưa có trong api-generated.ts ─────────────
/** Hotel as returned by the backend, which also computes `reviewCount`. */
export type HotelWithStats = Hotel & { reviewCount?: number };

export interface PaymentQr {
  bookingId: number;
  qrCodeUrl: string;
  bankName: string;
  accountNumber: string;
  accountName: string;
  amount: number;
  transferContent: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatHotelInfo {
  name?: string;
  description?: string;
  address?: string;
  city?: string;
  ratingAvg?: number;
}

export interface ChatReply {
  reply?: string;
}

// The generated `BookingRequest` is the entity, not the create-booking DTO, because of a Swagger name clash.
export interface CreateBookingPayload {
  checkInDate: string;
  checkOutDate: string;
  rooms: { roomId: number; quantity: number }[];
}

export interface UpdateProfilePayload {
  newPassword: string;
}

export type AdminOverview = Record<string, number>;

// ─── Param mặc định: luôn parse response thành JSON ──────────
const JSON_FMT = { format: 'json' as const };
const TEXT_FMT = { format: 'text' as const };

/** Returns the response body; rejects with an Error carrying the backend's message. */
const unwrap = <T>(request: Promise<AxiosResponse<T>>, fallbackMessage: string): Promise<T> =>
  request.then(
    res => res.data,
    (error: unknown) => {
      throw new Error(getErrorMessage(error, fallbackMessage));
    },
  );

// ─── AUTH ─────────────────────────────────────────────────────
export const authApi = {
  login: (username: string, password: string) =>
    swaggerApi.auth.login({ username, password }, TEXT_FMT),

  register: (username: string, password: string, email: string) =>
    swaggerApi.auth.register({ username, password, email }, TEXT_FMT),

  verifyOtp: (email: string, otp: string) =>
    swaggerApi.auth.verifyOtp({ email, otp }, TEXT_FMT),

  googleLogin: (credential: string) =>
    unwrap(
      swaggerApi.request<string>({
        path: '/auth/google', method: 'POST', body: { credential }, type: ContentType.Json, ...TEXT_FMT,
      }),
      'Đăng nhập Google thất bại',
    ),

  forgotPassword: (username: string) =>
    unwrap(
      swaggerApi.request<string>({
        path: '/auth/forgot-password', method: 'POST', body: { username }, type: ContentType.Json, ...TEXT_FMT,
      }),
      'Không thể gửi mã xác nhận',
    ),

  resetPassword: (username: string, otp: string, newPassword: string) =>
    unwrap(
      swaggerApi.request<string>({
        path: '/auth/reset-password', method: 'POST', body: { username, otp, newPassword }, type: ContentType.Json, ...TEXT_FMT,
      }),
      'Đặt lại mật khẩu thất bại',
    ),
};

export const chatApi = {
  sendMessage: (messages: ChatMessage[], hotelInfo: ChatHotelInfo) =>
    unwrap(
      swaggerApi.request<ChatReply>({
        path: '/api/chat', method: 'POST', body: { messages, hotelInfo }, secure: true, type: ContentType.Json, ...JSON_FMT,
      }),
      'Lỗi kết nối tới trợ lý AI',
    ),
};

// ─── USERS ────────────────────────────────────────────────────
export const userApi = {
  getAll: () => swaggerApi.users.getAll(JSON_FMT),
  getById: (id: number) => swaggerApi.users.getUser(id, JSON_FMT),
  create: (data: User) => swaggerApi.users.create(data, JSON_FMT),
  update: (id: number, data: User) => swaggerApi.users.update(id, data, JSON_FMT),
  delete: (id: number) => swaggerApi.users.delete(id, JSON_FMT),

  getMe: () =>
    unwrap(
      swaggerApi.request<User>({ path: '/users/me', method: 'GET', secure: true, ...JSON_FMT }),
      'Không thể tải hồ sơ',
    ),
  updateMe: (data: UpdateProfilePayload) =>
    unwrap(
      swaggerApi.request<User>({
        path: '/users/me', method: 'PUT', body: data, secure: true, type: ContentType.Json, ...JSON_FMT,
      }),
      'Cập nhật thất bại',
    ),
  /** Sends an OTP to the new address; resolves with the masked address. */
  requestEmailChange: (email: string) =>
    unwrap(
      swaggerApi.request<string>({
        path: '/users/me/email/otp', method: 'POST', body: { email }, secure: true, type: ContentType.Json, ...TEXT_FMT,
      }),
      'Không thể gửi mã xác nhận',
    ),
  confirmEmailChange: (email: string, otp: string) =>
    unwrap(
      swaggerApi.request<User>({
        path: '/users/me/email', method: 'PUT', body: { email, otp }, secure: true, type: ContentType.Json, ...JSON_FMT,
      }),
      'Xác nhận đổi email thất bại',
    ),
};

// ─── HOTELS ───────────────────────────────────────────────────
export const hotelApi = {
  getAll: () =>
    swaggerApi.request<HotelWithStats[]>({ path: '/hotels', method: 'GET', secure: true, ...JSON_FMT }),
  getById: (id: number) =>
    swaggerApi.request<HotelWithStats>({ path: `/hotels/${id}`, method: 'GET', secure: true, ...JSON_FMT }),
  create: (data: Hotel) => swaggerApi.hotels.create2(data, JSON_FMT),
  update: (id: number, data: Hotel) => swaggerApi.hotels.update1(id, data, JSON_FMT),
  delete: (id: number) => swaggerApi.hotels.delete1(id, JSON_FMT),
};

// ─── ROOMS ────────────────────────────────────────────────────
export const roomApi = {
  getByHotel: (hotelId: number) => swaggerApi.rooms.getByHotel(hotelId, JSON_FMT),
  create: (hotelId: number, data: Room) => swaggerApi.rooms.create1(hotelId, data, JSON_FMT),
  delete: (id: number) => swaggerApi.rooms.delete2(id, JSON_FMT),
};

// ─── BOOKINGS ─────────────────────────────────────────────────
export const bookingApi = {
  create: (hotelId: number, data: CreateBookingPayload) =>
    swaggerApi.request<Booking>({
      path: `/bookings/${hotelId}`, method: 'POST', body: data, secure: true, type: ContentType.Json, ...JSON_FMT,
    }),
  getMy: () => swaggerApi.bookings.myBookings(JSON_FMT),
  cancel: (bookingId: number) => swaggerApi.bookings.cancelBooking(bookingId, JSON_FMT),
  createRequest: (bookingId: number, data: CreateBookingRequestDto) =>
    swaggerApi.bookings.createRequest(bookingId, data, JSON_FMT),
  getRequests: (bookingId: number) =>
    swaggerApi.bookings.getRequests(bookingId, JSON_FMT),
};

// ─── PAYMENTS ─────────────────────────────────────────────────
export const paymentApi = {
  getPaymentQr: (bookingId: number) =>
    unwrap(
      swaggerApi.request<PaymentQr>({
        path: `/bookings/${bookingId}/payment-qr`, method: 'GET', secure: true, ...JSON_FMT,
      }),
      'Lỗi lấy QR',
    ),
  confirmPayment: (bookingId: number) =>
    unwrap(
      swaggerApi.request<Booking>({
        path: `/bookings/${bookingId}/confirm-payment`, method: 'POST', secure: true, ...JSON_FMT,
      }),
      'Lỗi xác nhận',
    ),
};

// ─── NOTIFICATIONS ────────────────────────────────────────────
export const notificationApi = {
  getMy: () => swaggerApi.notifications.getMyNotifications(JSON_FMT),
  markAsRead: (notificationId: number) =>
    swaggerApi.notifications.markAsRead(notificationId, JSON_FMT),
  delete: (notificationId: number) =>
    unwrap(
      swaggerApi.request<void>({ path: `/notifications/${notificationId}`, method: 'DELETE', secure: true }),
      'Không thể xóa thông báo',
    ),
};

// ─── ADMIN ────────────────────────────────────────────────────
export type BookingStatus = NonNullable<Booking['status']>;
export type BookingRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export const adminApi = {
  // Đặt phòng
  getBookings: (status?: BookingStatus) =>
    swaggerApi.admin.getBookings(status ? { status } : {}, JSON_FMT),
  approveBooking: (bookingId: number) =>
    swaggerApi.admin.approveBooking(bookingId, JSON_FMT),
  rejectBooking: (bookingId: number, response?: string) =>
    swaggerApi.admin.rejectBooking(bookingId, { response } as ProcessBookingRequestDto, JSON_FMT),
  markBookingPaid: (bookingId: number) =>
    swaggerApi.admin.markBookingPaid(bookingId, JSON_FMT),

  // Yêu cầu đặt phòng
  getBookingRequests: (status?: BookingRequestStatus) =>
    swaggerApi.admin.getBookingRequests(status ? { status } : {}, JSON_FMT),
  approveBookingRequest: (requestId: number, response?: string) =>
    swaggerApi.admin.approveBookingRequest(requestId, { response } as ProcessBookingRequestDto, JSON_FMT),
  rejectBookingRequest: (requestId: number, response?: string) =>
    swaggerApi.admin.rejectBookingRequest(requestId, { response } as ProcessBookingRequestDto, JSON_FMT),

  // Thống kê & Báo cáo
  getOverview: () =>
    swaggerApi.request<AdminOverview>({ path: '/admin/statistics/overview', method: 'GET', secure: true, ...JSON_FMT }),
  exportBookingsExcel: () =>
    unwrap(
      swaggerApi.request<Blob>({ path: '/admin/reports/bookings.xlsx', method: 'GET', secure: true, format: 'blob' }),
      'Có lỗi xảy ra khi xuất báo cáo',
    ),
};

// ─── REVIEWS ──────────────────────────────────────────────────
export const reviewApi = {
  getAll: () => swaggerApi.api.getAll2(JSON_FMT),
  create: (data: Review) => swaggerApi.api.create3(data, JSON_FMT),
};

// ─── IMAGES ───────────────────────────────────────────────────
export const imageApi = {
  uploadHotelImage: (hotelId: number, file: File) =>
    swaggerApi.images.uploadHotelImage(hotelId, { file }, JSON_FMT),
  uploadRoomImage: (roomId: number, file: File) =>
    swaggerApi.images.uploadRoomImage(roomId, { file }, JSON_FMT),
};
