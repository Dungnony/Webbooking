# Booking Nhóm 2 — Hệ thống đặt phòng khách sạn

Ứng dụng đặt phòng khách sạn gồm:

- **Backend**: Spring Boot 3.5 (Java 17), Spring Security + JWT, JPA/PostgreSQL (Supabase), thanh toán VietQR + webhook Sepay, trợ lý AI qua OpenAI.
- **Frontend**: React 18 + TypeScript + Vite, Ant Design 4, nằm trong thư mục [`frontend/`](frontend/).

```
.
├── src/main/java/com/Nhom2/booking/   # Backend: config, controller, service, repository, entity, dto, exception
├── src/main/resources/                # application.properties (không commit) + application.properties.example
├── src/test/java/                     # Unit test + test phân quyền (WebMvcTest)
├── frontend/                          # React app
│   ├── src/api.ts                     # Wrapper quanh client sinh tự động (api-generated.ts)
│   ├── src/pages/ , src/components/
│   └── vercel.json                    # Rewrite /proxy/* → backend trên Railway
└── Dockerfile                         # Build image backend
```

## Yêu cầu

- JDK 17 trở lên (không cần cài Maven, dùng `mvnw`)
- Node.js 18 trở lên
- Một database PostgreSQL (dự án đang dùng Supabase)

## Chạy backend

1. Tạo file cấu hình từ mẫu:

   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   ```

   File `application.properties` nằm trong `.gitignore`. **Không commit mật khẩu thật.** Có thể để nguyên placeholder và cung cấp giá trị qua biến môi trường:

   | Biến môi trường | Ý nghĩa |
   |---|---|
   | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Kết nối PostgreSQL |
   | `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Gmail SMTP (app password) để gửi OTP |
   | `SEPAY_API_KEY` | **Bắt buộc.** Webhook Sepay từ chối mọi request khi biến này trống |
   | `GOOGLE_CLIENT_ID` | Đăng nhập Google |
   | `PAYMENT_BANK_ACCOUNT_NUMBER`, `PAYMENT_BANK_ACCOUNT_NAME` | Tài khoản nhận tiền trên mã VietQR |
   | `JPA_DDL_AUTO` | Mặc định `update`; nên dùng `validate` khi schema đã ổn định |
   | `JPA_SHOW_SQL` | Mặc định `false`; bật `true` khi cần debug SQL |

   Key OpenAI được đọc từ bảng `app_config` (dòng `config_key = 'OPENAI_API_KEY'`), không nằm trong file cấu hình.

2. Chạy:

   ```bash
   ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```

   Backend chạy ở `http://localhost:8080`, Swagger UI ở `http://localhost:8080/swagger-ui/index.html`.

## Chạy frontend

```bash
cd frontend
cp .env.example .env            # điền VITE_GOOGLE_CLIENT_ID
npm install
npm run dev
```

Frontend gọi API qua tiền tố `/proxy`:

- Khi dev, Vite proxy chuyển tiếp tới `http://localhost:8080` (xem [`vite.config.ts`](frontend/vite.config.ts)).
- Trên Vercel, [`vercel.json`](frontend/vercel.json) rewrite tới backend trên Railway.

Sau khi đổi API ở backend, sinh lại client (backend phải đang chạy):

```bash
npm run generate-api            # ghi đè src/api-generated.ts — không sửa tay file này
```

## Kiểm tra trước khi push

```bash
# Backend: unit test + test phân quyền, không cần database
./mvnw test '-Dtest=!BackendcuoikyApplicationTests'   # PowerShell: .\mvnw.cmd test "-Dtest=!BackendcuoikyApplicationTests"

# Frontend
cd frontend
npm run lint
npm run build
```

> `BackendcuoikyApplicationTests` khởi động toàn bộ ứng dụng và **kết nối tới database thật** đang cấu hình. Đừng chạy test này với database production.

## Phân quyền API (tóm tắt)

| Endpoint | Quyền |
|---|---|
| `/auth/**`, `GET /hotels/**`, `GET /rooms/**`, `/api/webhook/**` (có API key) | Công khai |
| `/api/chat`, `/bookings/**`, `/notifications/**`, `/api/reviews`, `/users/me` | Đăng nhập |
| `/admin/**`, `/users/**`, `/api/images/**`, `POST/PUT/DELETE /hotels/**`, `POST/DELETE /rooms/**` | `ADMIN` |

Quy tắc đầy đủ nằm trong [`SecurityConfig.java`](src/main/java/com/Nhom2/booking/config/SecurityConfig.java) và được kiểm tra bởi [`SecurityRulesTest`](src/test/java/com/Nhom2/booking/config/SecurityRulesTest.java).

## Deploy

- **Backend**: Railway build từ [`Dockerfile`](Dockerfile). Khai báo đủ các biến môi trường ở bảng trên trong dashboard Railway.
- **Frontend**: Vercel, thư mục gốc là `frontend/`.
