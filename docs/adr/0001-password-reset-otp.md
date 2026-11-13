# Đặt lại mật khẩu bằng mã xác nhận 6 số nhập trong app

**Status**: accepted (2026-11-13)

## Bối cảnh

Stylo chỉ có client Android và server Express; app không có deep link/App Links (`AndroidManifest.xml` chỉ có MAIN/LAUNCHER), API chỉ với tới `127.0.0.1:8787` qua `adb reverse`, và tài khoản seed dùng domain chết `@stylo.local`. Server trước đó chưa có mailer.

## Quyết định

Quên mật khẩu phát một **mã xác nhận 6 số** (`PasswordResetToken`) qua email và người dùng nhập mã đó trong app, không phải magic link. Mã lưu dưới dạng hash argon2id, TTL 15 phút, mỗi user chỉ có một mã sống, dùng xong ghi `consumedAt`; tối đa 5 lần nhập sai, gửi lại cách nhau 60 giây, tối đa 5 mã/giờ/email. `POST /auth/forgot-password` luôn trả `202`, `POST /auth/reset-password` trả `204`; đổi mật khẩu thu hồi toàn bộ refresh token và **không** tự đăng nhập.

Email đi qua `MAIL_TRANSPORT`: `smtp` (nodemailer, biến `SMTP_*` bắt buộc) cho gửi thật, `log` in nội dung ra log server cho dev/demo. Mã **không bao giờ** xuất hiện trong response API.

## Phương án đã loại

- **Magic link**: cần App Links (`assetlinks.json` + domain HTTPS) hoặc custom scheme, và trình duyệt trên AVD không mở được `127.0.0.1` của máy host. Đổi lại chỉ tiết kiệm vài thao tác gõ.
- **Trả `devCode` trong response khi `NODE_ENV != production`**: biến mã thành một phần hợp đồng công khai và tạo đường lộ mã nếu cấu hình môi trường sai.

## Hệ quả

- Vẫn còn chênh lệch thời gian phản hồi giữa email tồn tại và không tồn tại: argon2 giả san phẳng phần CPU, còn thời gian SMTP thì không. Chấp nhận cho phạm vi đồ án.
- Demo cần `MAIL_TRANSPORT=log` (mặc định trong `.env.example`) hoặc một sandbox SMTP như Mailtrap; journey Kaspresso đọc mã qua Mailtrap API và tự bỏ qua khi không có token.
- Mã 6 số chỉ có 10⁶ khả năng, nên argon2id + giới hạn số lần thử là bắt buộc, không được đổi sang sha256 trần như `RefreshToken` (token 256-bit ngẫu nhiên).
