# Stylo - Ứng dụng bán quần áo

Ứng dụng Android (Kotlin + Jetpack Compose) và API Node.js (Express 5 + Prisma 7 + MySQL 8.4).
Repo gồm hai build độc lập: `android/` và `server/`.

- API: `http://127.0.0.1:8787/api/v1`, health check tại `/health`
- Ứng dụng Android gọi API qua `adb reverse`, mặc định `http://127.0.0.1:8787/api/v1/`
- Tài liệu kiến trúc, API, CSDL và kiểm thử: [`docs/`](docs/)

## Yêu cầu

| Thành phần | Bắt buộc | Ghi chú |
|---|---|---|
| Node.js >= 22.12 (khuyến nghị 24) | Có | Chạy server và script setup |
| Docker Desktop / Docker Engine | Có, nếu dùng MySQL trong container | Script tự tạo container `stylo-mysql` |
| MySQL 8.4 sẵn có | Thay thế Docker | Trỏ `DATABASE_URL` trong `server/.env` và chạy `--no-docker` |
| JDK 17 | Chỉ khi build Android | `java -version` phải là 17 |
| Android SDK + AVD hoặc máy thật | Chỉ khi chạy app | `compileSdk 36`, `minSdk 26` |

## Cách nhanh nhất: một lệnh

```bash
git clone <repo-url> stylo-clothing-store
cd stylo-clothing-store
node scripts/dev.mjs
```

Script làm toàn bộ các bước sau, chạy lại nhiều lần vẫn an toàn:

1. Tạo `server/.env` từ `server/.env.example` nếu chưa có (`PAYMENT_PROVIDER=MOCK`).
2. Tạo/khởi động container MySQL `stylo-mysql`, đợi cổng 3306 sẵn sàng.
3. Tạo schema `stylo_test` và cấp quyền shadow database cho Prisma.
4. `npm install` trong `server/` khi thiếu dependency, sinh Prisma client.
5. `prisma migrate deploy`, seed dữ liệu mẫu nếu DB chưa có sản phẩm.
6. Chạy API bằng `npm run dev` và đợi `/health` trả lời.
7. In tài khoản demo và địa chỉ API.

Dừng bằng `Ctrl+C`.

### Tùy chọn

| Lệnh | Tác dụng |
|---|---|
| `node scripts/dev.mjs --android` | Build APK debug, cài lên thiết bị/máy ảo đang kết nối, `adb reverse` cổng 8787 rồi mở app |
| `node scripts/dev.mjs --reset` | Xóa schema, chạy lại migration và seed (mất dữ liệu dev) |
| `node scripts/dev.mjs --seed` | Chạy lại seed dù DB đã có sản phẩm |
| `node scripts/dev.mjs --no-api` | Chỉ chuẩn bị DB/dependency, không chạy server |
| `node scripts/dev.mjs --no-docker` | Không quản lý container; dùng `DATABASE_URL` có sẵn |
| `node scripts/dev.mjs --install` | Chạy `npm install` dù `node_modules` đã tồn tại |
| `node scripts/dev.mjs --help` | In trợ giúp |

Trước khi dùng `--android`, mở AVD trong Android Studio (hoặc cắm máy thật bật USB debugging).
Nếu chưa có `android/local.properties`, script tự tạo từ `ANDROID_HOME` hoặc vị trí SDK mặc định.

## Tài khoản seed

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Admin | `admin@stylo.local` | `Admin@12345` |
| Khách hàng 01-11 | `customer01@stylo.local` ... `customer11@stylo.local` | `Customer@12345` |
| Khách hàng 12 (bị khóa) | `customer12@stylo.local` | `Customer@12345` |

Seed tạo 4 danh mục, 4 thương hiệu, 185 sản phẩm / 1330 biến thể, 140 đơn hàng ở mọi trạng thái
và 3 phiếu nhập kho. `customer11@stylo.local` cố ý không có giỏ hàng và đơn hàng. Chi tiết hợp
đồng seed: [`docs/testing.md`](docs/testing.md#seed-and-demo-data).

## Chạy thủ công (không dùng script)

```bash
# 1. MySQL
docker run -d --name stylo-mysql \
  -e MYSQL_ROOT_PASSWORD=stylo-root \
  -e MYSQL_DATABASE=stylo \
  -e MYSQL_USER=stylo \
  -e MYSQL_PASSWORD=stylo \
  -p 3306:3306 mysql:8.4

docker exec -e MYSQL_PWD=stylo-root stylo-mysql mysql -uroot -e "
  CREATE DATABASE IF NOT EXISTS stylo_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  GRANT ALL PRIVILEGES ON stylo_test.* TO 'stylo'@'%';
  GRANT ALL PRIVILEGES ON *.* TO 'stylo'@'%';"

# 2. API
cd server
cp .env.example .env
npm install
npm run db:deploy
npm run db:seed
npm run dev

# 3. Android (terminal khác)
cd android
adb reverse tcp:8787 tcp:8787
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.stylo.shop/.MainActivity
```

Nếu không dùng `adb reverse`, trỏ app thẳng vào host của máy ảo:

```bash
./gradlew :app:assembleDebug -Pstylo.apiBaseUrl=http://10.0.2.2:8787/api/v1/
```

## Kiểm thử

```bash
cd server
npm run typecheck
npm run test:unit
npm run test:integration     # cần schema stylo_test; script dev.mjs đã tạo sẵn
```

Quan trọng: luôn chạy integration test bằng `npm run test:integration` (hoặc
`npx vitest run --config vitest.integration.config.ts ...`). Chạy `npx vitest run
tests/integration/...` sẽ bỏ qua `.env.test`, load `server/.env` và `beforeEach(resetDb())`
xóa sạch DB dev.

Android:

```bash
cd android
./gradlew :app:testDebugUnitTest      # Windows: gradlew.bat :app:testDebugUnitTest
```

E2E Kaspresso cần API đang chạy, AVD đang bật và mobilecli; quy trình đầy đủ ở
[`docs/testing.md`](docs/testing.md). Trên Windows dùng `android\e2e.cmd -Class <TênJourney>`.

## Xử lý sự cố

| Hiện tượng | Cách xử lý |
|---|---|
| `Docker chưa chạy` hoặc lỗi named pipe | Mở Docker Desktop, đợi engine sẵn sàng rồi chạy lại |
| `Port 3306 already allocated` | Tắt MySQL khác, hoặc đổi cổng trong `server/.env` và chạy `--no-docker` |
| `Port 8787` bận | Script bỏ qua nếu API cũ còn sống; nếu là tiến trình khác, đổi `PORT` trong `server/.env` rồi chạy lại |
| Sửa `.env` nhưng API không đổi | `tsx watch` không theo dõi `.env`; dừng và chạy lại API (hoặc `touch server/src/index.ts`) |
| Windows: adb báo không kết nối được cổng 5037 | Windows giữ dải 4942-5041. Đặt `ANDROID_ADB_SERVER_PORT=5359` trước khi chạy script/AVD, sau đó `adb -P 5359 reverse tcp:8787 tcp:8787` |
| App báo lỗi mạng | Kiểm tra `adb reverse tcp:8787 tcp:8787` sau mỗi lần khởi động lại máy ảo, hoặc build với `-Pstylo.apiBaseUrl=http://10.0.2.2:8787/api/v1/` |
| Muốn thanh toán VNPay sandbox | Đặt `PAYMENT_PROVIDER=VNPAY` cùng `VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` trong `server/.env`, khởi động lại API. Demo mặc định dùng `MOCK` |
| Muốn nhận mã đặt lại mật khẩu qua email | Mặc định `MAIL_TRANSPORT=log` in mã ra log server; muốn gửi thật thì đặt `MAIL_TRANSPORT=smtp` cùng `SMTP_HOST`/`SMTP_PORT`/`SMTP_USER`/`SMTP_PASS` (Mailtrap sandbox nhận mọi địa chỉ người nhận) |

## Đóng gói bài nộp

`docs/submission/` là thư mục nộp bài: báo cáo, slide, video demo, ERD, dump CSDL và mã nguồn.
Mã nguồn sạch nằm trong `docs/submission/Stylo_Source/` (không có `node_modules`, `build`, `.env`
hay dữ liệu máy cá nhân) kèm bản nén cùng tên. Dựng lại sau khi sửa code:

```bash
python docs/report/tools/make_zip.py
```

Script sao chép cây nguồn theo danh sách loại trừ trong `docs/report/tools/make_zip.py`, tạo
`docs/submission/Stylo_Source/` và `docs/submission/Stylo_Source.zip` từ cùng một cây.

## Cấu trúc repo

```
.
├── android/            # Android app (Kotlin, Compose, Hilt)
├── server/             # Express 5 + Prisma 7 API
│   ├── prisma/         # schema, migrations, seed
│   └── seed-assets/    # ảnh và catalog JSON dùng cho seed
├── docs/               # kiến trúc, API, ERD, kiểm thử, ADR
├── scripts/dev.mjs     # setup một lệnh
└── GLOSSARY.md         # thuật ngữ dùng xuyên suốt repo
```
