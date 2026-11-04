# GLOSSARY

Thuật ngữ dùng trong Stylo. Cột **Code** là tên dùng trong code, API và schema; đừng đặt tên khác.

| Thuật ngữ | Code | Nghĩa | Ghi chú |
|---|---|---|---|
| Sản phẩm | `Product` | Mặt hàng trừu tượng, không bán trực tiếp | Có nhiều variant; ẩn bằng `status = INACTIVE` khi đã phát sinh đơn |
| Variant | `ProductVariant` | Tổ hợp size × màu của một sản phẩm | Đơn vị bán và đơn vị tồn kho |
| SKU | `sku` | Mã duy nhất của variant | Unique toàn hệ thống |
| Giá bán | `price` | Giá niêm yết của variant, VND | Số nguyên, không thập phân |
| Tồn kho | `stock` | Số lượng còn bán được của một variant | Tăng khi nhập kho, giảm khi đặt hàng thành công |
| Phiếu nhập kho | `InventoryReceipt` | Chứng từ một lần nhập hàng | Bất biến sau khi lưu; không sửa, không xóa |
| Dòng phiếu nhập | `InventoryReceiptItem` | Variant + số lượng + đơn giá nhập | Lưu lịch sử giá nhập |
| Nhà cung cấp | `supplier` | Tên đơn vị bán hàng cho cửa hàng | Chuỗi tự do trên phiếu nhập, không phải bảng riêng |
| Giỏ hàng | `Cart` | Tập variant khách chọn trước khi đặt | Server-side, một giỏ mỗi customer |
| Đơn hàng | `Order` | Cam kết mua của customer | PENDING → CONFIRMED → SHIPPING → DELIVERED; CANCELLED trước khi giao |
| Dòng đơn | `OrderItem` | Snapshot tên sản phẩm, variant, đơn giá tại thời điểm đặt | Không join ngược để lấy giá hiện tại |
| Thanh toán | `Payment` | Bản ghi ý định trả tiền cho một đơn | UNPAID / PAID / FAILED |
| Phương thức thanh toán | `PaymentProvider` | `cod`, `vnpay`, `mock` | Chọn bằng biến môi trường `PAYMENT_PROVIDER`; sau interface `PaymentGateway` |
| Khách | `Customer` | Vai trò mua hàng | Xem sản phẩm không cần đăng nhập; giỏ và đơn cần đăng nhập |
| Quản trị | `Admin` | Vai trò quản lý | Tạo bằng seed, không tự đăng ký trong app |
| Địa chỉ giao hàng | `Address` | Địa chỉ nhận hàng của customer | Nhiều địa chỉ, đúng một địa chỉ mặc định |
