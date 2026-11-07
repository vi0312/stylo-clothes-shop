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
| Doanh thu | `revenue` | Tổng `order.total` của các đơn đã thanh toán trong kỳ | Gồm phí ship; tính theo ngày `paid_at` đổi sang giờ VN nên đơn COD vào doanh thu ở ngày giao |
| Giá vốn hàng bán | `cogs` | Σ `quantity × unit_cost` snapshot của dòng đơn đã thanh toán trong kỳ | `unit_cost` null coi như 0; không dùng giá nhập phát sinh sau |
| Lãi gộp | `grossProfit` | `revenue − cogs` | Không trừ chi phí vận chuyển, nên cao hơn lãi thật đúng bằng tiền ship |
| Kỳ báo cáo | `from` / `to` | Hai ngày lịch VN, bao gồm cả hai đầu | Mặc định 30 ngày gần nhất; đổi sang cửa sổ UTC nửa mở khi truy vấn |
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
| Seed catalog | `seed-assets/catalog/catalog.json` | 100 sản phẩm mẫu lấy từ dataset sản phẩm thật, đã chuẩn hoá sang schema Stylo | Seed chỉ đọc snapshot + ảnh commit, không gọi mạng; giá/size/tồn kho là dữ liệu demo |
| Pinned product | `Áo thun basic` | Sản phẩm seed cố định cho catalog integration test và journey Kaspresso | Không đổi tên hoặc SKU `STY-AO-001-*` khi tái tạo snapshot; seed tạo nó cuối để đứng đầu sort `newest` |
| Symbol | `ic_launcher_foreground` | Phần hình của logo, 5 polygon, không có chữ | Chỉ dùng cho icon launcher/themed icon; 58% canvas 108dp để nằm trong safe zone 66dp |
| Wordmark | `stylo-wordmark-*` | Phần chữ "stylo" của logo (4 path) | Không đứng một mình trong app; luôn đi cùng symbol trong lockup |
| Lockup | `ic_stylo_lockup_horizontal`, `ic_stylo_lockup_stacked` | Bố cục symbol + wordmark | Horizontal cho header, stacked cho màn đăng nhập; trong app luôn monochrome, tô bằng `MaterialTheme` |
| Themed icon | `ic_launcher_monochrome` | Lớp alpha cho icon Material You (Android 13+) | Hệ thống tô màu theo wallpaper; không đặt màu thương hiệu ở đây |

## Thuật ngữ UI

Cùng một khái niệm nhưng chuỗi hiển thị khác nhau giữa các màn là lỗi (audit 2026-11-08 tìm thấy
"Kích cỡ" ở PDP và "Size S" ở giỏ). Bảng dưới là chuỗi chuẩn; screen chỉ dùng `stringResource`,
không hardcode, và **không** để tên model (`Variant`, `size`, `color`) lọt ra UI.

| Thuật ngữ (model) | Chuỗi UI | Dùng ở | Cấm |
|---|---|---|---|
| `ProductVariant` | "Biến thể" (số nhiều: "biến thể"), trong câu ngắn có thể là "phân loại" | Dashboard, form sản phẩm, chi tiết phiếu nhập | "Variant", "variant" |
| `ProductVariant.size` | "Kích cỡ" (giá trị hiển thị nguyên: S, M, L, 39…) | PDP, giỏ, đơn, phiếu nhập | "Size" |
| `ProductVariant.color` | "Màu sắc" (giá trị: Đen, Trắng…) | PDP, giỏ, đơn | "Màu" khi đứng một mình làm nhãn; "Color" |
| `ProductVariant` đang chọn ở PDP | "Thêm {size} vào giỏ" trong CTA | PDP | "Thêm size S vào giỏ" (lẫn chữ "size"), "Thêm vào giỏ" chung chung |
| `Product.soldCount` | *không hiển thị* | — | "Đã bán N" (xem ADR 0016 §4) |
| `stock` | "Tồn kho" cho admin, "Còn {n} sản phẩm" cho khách | Dashboard, PDP | "Stock", "Tồn" |
| `InventoryReceipt` | "Phiếu nhập kho", dòng: "dòng" | Admin kho | "Receipt", "phiếu nhập hàng" lẫn lộn |
| `ProductVariant` tồn thấp (dashboard) | "Biến thể tồn thấp" | Dashboard KPI, báo cáo | "Variant tồn thấp" |
| Giá | `Formatters.money` → `289.000 ₫` (NBSP trước ₫) | Mọi nơi | "289k", "289.000đ" không NBSP, dấu "-" làm khoảng giá (dùng "–") |
