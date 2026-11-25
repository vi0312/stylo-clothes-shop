# 0002 — Huỷ đơn tới trước khi giao, kèm lý do

## Bối cảnh

Khách chỉ huỷ được đơn `PENDING`/`CONFIRMED` và chưa thanh toán. Đơn đã thanh toán online
bị chặn bởi `ORDER_ALREADY_PAID`, nên khách không thể huỷ ngay sau khi trả tiền; đơn đang
giao (`SHIPPING`) cũng không huỷ được dù hàng chưa tới tay.

## Quyết định

- Khách huỷ được khi đơn ở `PENDING`, `CONFIRMED` hoặc `SHIPPING`; `DELIVERED` và
  `CANCELLED` là trạng thái cuối.
- Mỗi lần khách huỷ phải gửi `reason` (1–255 ký tự). Lý do lưu vào `Order.cancelReason`
  và hiện ở chi tiết đơn của khách lẫn admin.
- Đơn đã thanh toán vẫn huỷ được. Hệ thống không có luồng hoàn tiền: admin hoàn tiền ngoài
  ứng dụng, còn `paidAt` được giữ làm dấu vết.
- Báo cáo doanh thu/COGS/top sản phẩm loại đơn `CANCELLED`, nên tiền đã hoàn không bị tính
  là doanh thu.

## Hệ quả

- Admin cũng huỷ được đơn `SHIPPING`; hộp thoại chuyển trạng thái hỏi lý do khi đích là
  `CANCELLED`.
- `POST /orders/:id/cancel` trả `VALIDATION_FAILED` khi thiếu lý do và `INVALID_ORDER_STATE`
  khi đơn đã giao.
- Tồn kho vẫn được hoàn đúng một lần nhờ `CANCELLED` là trạng thái cuối.
