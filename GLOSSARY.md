# GLOSSARY

Thuật ngữ dùng trong Stylo. Cột **Code** là tên dùng trong code, API và schema; đừng đặt tên khác.

| Thuật ngữ | Code | Nghĩa | Ghi chú |
|---|---|---|---|
| Người dùng | `User` | Tài khoản đăng nhập của app | Một bảng, một vai trò; admin tạo bằng seed |
| Khách | `Role.CUSTOMER` | Vai trò mua hàng | Xem sản phẩm không cần đăng nhập; giỏ và đơn cần đăng nhập |
| Quản trị | `Role.ADMIN` | Vai trò quản lý | Không tự đăng ký trong app |
| Khách vãng lai | `Guest` | Người chưa đăng nhập | Không phải row trong DB |
| Địa chỉ giao hàng | `Address` | Địa chỉ nhận hàng của khách | Nhiều địa chỉ, đúng một mặc định; đơn lưu snapshot, không FK tới `Address` |
| Sản phẩm | `Product` | Mặt hàng trừu tượng, không bán trực tiếp | Có nhiều variant; ẩn bằng `status = INACTIVE`, không xoá khi đã phát sinh đơn |
| Variant | `ProductVariant` | Tổ hợp size × màu của một sản phẩm | Đơn vị bán và đơn vị tồn kho; unique theo `(product, size, color)` |
| SKU | `sku` | Mã duy nhất của variant | Unique toàn hệ thống |
| Giá bán | `price` | Giá niêm yết của variant, VND | Số nguyên, không thập phân |
| Tồn kho | `stock` | Số lượng còn bán được của một variant | Cột trên variant; mọi thay đổi kèm một `StockMovement` trong cùng transaction |
| Sổ cái tồn kho | `StockMovement` | Bản ghi append-only của mỗi lần tồn thay đổi | `delta` âm khi bán, dương khi nhập/hoàn; không sửa, không xoá |
| Phiếu nhập kho | `InventoryReceipt` | Chứng từ một lần nhập hàng | `code` dạng `PN-…`; bất biến sau khi lưu; không sửa, không xoá |
| Dòng phiếu nhập | `InventoryReceiptItem` | Variant + số lượng + đơn giá nhập | Lưu lịch sử giá nhập |
| Nhà cung cấp | `supplier` | Tên đơn vị bán hàng cho cửa hàng | Chuỗi tự do trên phiếu nhập, không phải bảng riêng |
| Giá vốn | `unit_cost` | Giá nhập bình quân gia quyền của variant tại thời điểm bán | Snapshot trên `OrderItem`; null nếu variant chưa từng nhập |
| Giỏ hàng | `Cart` | Tập variant khách chọn trước khi đặt | Server-side, đúng một giỏ mỗi khách, tạo khi thêm lần đầu |
| Dòng giỏ | `CartItem` | Một variant và số lượng trong giỏ | Unique theo `(cart, variant)` |
| Đơn hàng | `Order` | Cam kết mua của customer | `PENDING → CONFIRMED → SHIPPING → DELIVERED`; `CANCELLED` từ `PENDING`/`CONFIRMED` |
| Mã đơn | `Order.code` | Mã hiển thị và đối soát của đơn | Duy nhất, dạng `STY-…`; **không** dùng làm tham chiếu gateway |
| Mã đặt hàng phía client | `clientOrderId` | UUID app sinh cho một lần bấm đặt hàng | Unique theo `(user, clientOrderId)`; retry trả lại đơn cũ, không trừ kho hay tạo payment lần hai |
| Dòng đơn | `OrderItem` | Snapshot tên, size, màu, SKU, đơn giá, giá vốn tại thời điểm đặt | Không join ngược để lấy giá hiện tại |
| Phương thức thanh toán | `payment_method` | Cách khách trả tiền: `COD`, `ONLINE` | Thuộc tính của đơn |
| Thanh toán | `Payment` | Một lần thử thanh toán online cho một đơn | Nhiều lần thử trên một đơn; COD không có row nào |
| Trạng thái thanh toán của đơn | `payment_status` | `UNPAID` / `PAID` / `FAILED` | Suy ra từ `Order.paid_at` và lần thử gần nhất; không lưu cột |
| Nhà cung cấp thanh toán | `provider` | Adapter sinh ra giao dịch: `MOCK` khi demo, `VNPAY` khi sandbox | Chọn bằng biến môi trường `PAYMENT_PROVIDER`, không phải lựa chọn của khách |
| Mã tham chiếu giao dịch | `txn_ref` | Mã duy nhất gửi gateway (`vnp_TxnRef`) | Alnum; khác `Order.code` vì mỗi lần thử cần mã mới |
| Đường dẫn thanh toán | `paymentUrl` | URL app mở để khách trả tiền | Dựng lại từ payment attempt + config mỗi lần đọc; không lưu DB |
| Return URL | `returnUrl` | URL trình duyệt quay về sau khi thanh toán | Chỉ VNPay dùng; verify checksum trước khi đổi trạng thái |
| IPN | `ipn` | Gateway gọi server-to-server báo kết quả | Server local không có IPN public; endpoint vẫn idempotent và trả `RspCode` |
| Refresh token | `RefreshToken` | Token dài hạn đã hash để cấp access token mới | Không lưu token thô; access 15 phút, refresh 30 ngày |
