# 0003 — Xoá mềm sản phẩm đã phát sinh giao dịch

## Bối cảnh

`Product` bị `OrderItem`, `InventoryReceiptItem` và `StockMovement` tham chiếu bằng
khoá ngoại `onDelete: Restrict`; các bảng này là sổ cái bán hàng và tồn kho. Vì vậy
không thể xoá cứng một sản phẩm đã bán hoặc đã nhập hàng: MySQL sẽ chặn, còn xoá dây
chuyền các dòng sổ cái thì làm sai lịch sử doanh thu/COGS/top sản phẩm. Trước đây admin
nhận `409 IN_USE` và bị buộc phải chuyển sản phẩm sang `INACTIVE`.

## Quyết định

- `DELETE /admin/products/:id` luôn thành công với sản phẩm đang tồn tại:
  - chưa có tham chiếu sổ cái → xoá cứng như cũ (dọn luôn ảnh trên disk);
  - đã có tham chiếu → ghi `Product.deletedAt` (xoá mềm), giữ nguyên row và mọi
    variant/ảnh/sổ cái liên quan.
- Sản phẩm có `deletedAt` bị loại khỏi mọi truy vấn bán hàng và quản trị: catalog,
  tìm kiếm/chi tiết admin, bộ lọc, tìm variant (phiếu nhập), tồn thấp, và bị chặn ở
  checkout.
- Xoá mềm dọn `CartItem` của các variant thuộc sản phẩm trong cùng transaction, để giỏ
  của khách không còn dòng trỏ tới sản phẩm đã xoá.
- Lịch sử đơn/phiếu nhập vẫn join được `Product` và hiển thị đúng tên, vì row còn tồn tại.

## Hệ quả

- Admin không còn thấy `409 IN_USE` khi xoá sản phẩm; app báo "Đã xoá sản phẩm" và sản
  phẩm biến mất khỏi danh sách.
- Không có UI khôi phục/xem sản phẩm đã xoá mềm; dữ liệu vẫn nằm trong DB cho báo cáo.
- Xoá danh mục/thương hiệu vẫn có thể bị chặn nếu còn sản phẩm (kể cả đã xoá mềm) tham
  chiếu, đúng với ràng buộc khoá ngoại.
