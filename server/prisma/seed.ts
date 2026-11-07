import argon2 from 'argon2'
import { copyFileSync, existsSync, mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { getEnv } from '../src/config/env.js'
import { prisma } from '../src/lib/prisma.js'

type CatalogStatus = 'ACTIVE' | 'INACTIVE'
type UserStatus = 'ACTIVE' | 'DISABLED'
type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED'
type PaymentMethod = 'COD' | 'ONLINE'
type PaymentPlan = 'SUCCEEDED' | 'FAILED' | 'PENDING' | 'FAILED_THEN_SUCCEEDED'

interface SeedCategory {
  name: string
  status: CatalogStatus
}

interface SeedVariant {
  sku: string
  size: string
  color: string
  price: number
  /** Stock the demo should show after every seeded receipt and sale. */
  targetStock: number
}

interface SeedProduct {
  name: string
  description: string
  category: string
  brand: string
  status?: CatalogStatus
  imageIndexes: number[]
  variants: SeedVariant[]
}

interface SeedOrderItem {
  sku: string
  quantity: number
}

interface SeedOrder {
  code: string
  customer: number
  daysAgo: number
  status: OrderStatus
  method: PaymentMethod
  payment?: PaymentPlan
  note?: string
  items: SeedOrderItem[]
}

const DAY_MS = 86_400_000
const NOW = new Date()

/** Southeast Asia noon, expressed in UTC, keeps order dates on their intended VN day. */
function daysAgoAt(daysAgo: number, hourUtc: number): Date {
  const day = new Date(NOW.getTime() - daysAgo * DAY_MS)
  return new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(), hourUtc, 0, 0))
}

function orderTime(daysAgo: number): Date {
  return daysAgo === 0 ? new Date(NOW.getTime() - 60 * 60 * 1000) : daysAgoAt(daysAgo, 3)
}

function addHours(date: Date, hours: number): Date {
  return new Date(date.getTime() + hours * 3_600_000)
}

function earlier(first: Date, second: Date): Date {
  return first.getTime() <= second.getTime() ? first : second
}

const CUSTOMER_PASSWORD = 'Customer@12345'
const CUSTOMER_EMAILS = Array.from({ length: 12 }, (_, index) => `customer${String(index + 1).padStart(2, '0')}@stylo.local`)
const CUSTOMER_NAMES = [
  'Nguyễn Văn An',
  'Trần Thị Bích',
  'Lê Hoàng Nam',
  'Phạm Thu Hà',
  'Hoàng Minh Tuấn',
  'Vũ Thị Lan',
  'Đặng Quốc Huy',
  'Bùi Thanh Mai',
  'Đỗ Anh Khoa',
  'Ngô Thị Hồng',
  'Dương Gia Bảo',
  'Lý Thị Ngọc',
]
const DISABLED_CUSTOMER_INDEX = 12

const CATEGORIES: SeedCategory[] = [
  { name: 'Áo', status: 'ACTIVE' },
  { name: 'Quần', status: 'ACTIVE' },
  { name: 'Áo khoác', status: 'ACTIVE' },
  { name: 'Giày', status: 'ACTIVE' },
  // Inactive category with no active product: the catalog filter edge case.
  { name: 'Phụ kiện', status: 'INACTIVE' },
]

const BRANDS = ['Stylo', 'Local Brand', 'Urban Viet', 'Basic Lab']

const PRODUCTS: SeedProduct[] = [
  {
    name: 'Áo thun basic',
    description: 'Áo thun cotton mềm, form regular, dễ phối đồ hằng ngày.',
    category: 'Áo',
    brand: 'Stylo',
    imageIndexes: [0, 1],
    variants: [
      { sku: 'STY-AO-001-S', size: 'S', color: 'Đen', price: 199_000, targetStock: 12 },
      { sku: 'STY-AO-001-M', size: 'M', color: 'Đen', price: 199_000, targetStock: 13 },
      { sku: 'STY-AO-001-L', size: 'L', color: 'Trắng', price: 209_000, targetStock: 3 },
    ],
  },
  {
    name: 'Áo sơ mi linen',
    description: 'Sơ mi linen thoáng mát cho mùa hè.',
    category: 'Áo',
    brand: 'Stylo',
    imageIndexes: [1],
    variants: [
      { sku: 'STY-AO-002-M', size: 'M', color: 'Be', price: 359_000, targetStock: 10 },
      { sku: 'STY-AO-002-L', size: 'L', color: 'Be', price: 359_000, targetStock: 9 },
      { sku: 'STY-AO-002-XL', size: 'XL', color: 'Trắng', price: 369_000, targetStock: 4 },
    ],
  },
  {
    name: 'Áo polo pique',
    description: 'Polo pique co giãn, cổ bẻ gọn.',
    category: 'Áo',
    brand: 'Urban Viet',
    imageIndexes: [2],
    variants: [
      { sku: 'STY-AO-003-S', size: 'S', color: 'Xanh navy', price: 329_000, targetStock: 14 },
      { sku: 'STY-AO-003-M', size: 'M', color: 'Xanh navy', price: 329_000, targetStock: 11 },
      { sku: 'STY-AO-003-L', size: 'L', color: 'Trắng', price: 329_000, targetStock: 8 },
    ],
  },
  {
    name: 'Áo hoodie nỉ',
    description: 'Hoodie nỉ bông dày vừa, mũ hai lớp.',
    category: 'Áo khoác',
    brand: 'Local Brand',
    imageIndexes: [3],
    variants: [
      { sku: 'STY-AK-001-M', size: 'M', color: 'Xám', price: 499_000, targetStock: 7 },
      { sku: 'STY-AK-001-L', size: 'L', color: 'Xám', price: 499_000, targetStock: 6 },
      { sku: 'STY-AK-001-XL', size: 'XL', color: 'Đen', price: 519_000, targetStock: 9 },
    ],
  },
  {
    name: 'Áo khoác bomber',
    description: 'Bomber chống gió nhẹ, form hộp.',
    category: 'Áo khoác',
    brand: 'Urban Viet',
    imageIndexes: [4],
    variants: [
      { sku: 'STY-AK-002-M', size: 'M', color: 'Đen', price: 659_000, targetStock: 9 },
      { sku: 'STY-AK-002-L', size: 'L', color: 'Đen', price: 659_000, targetStock: 8 },
      { sku: 'STY-AK-002-XL', size: 'XL', color: 'Nâu', price: 679_000, targetStock: 6 },
    ],
  },
  {
    name: 'Áo cardigan len',
    description: 'Cardigan len mảnh, phù hợp thời tiết se lạnh.',
    category: 'Áo khoác',
    brand: 'Basic Lab',
    imageIndexes: [5],
    variants: [
      { sku: 'STY-AK-003-M', size: 'M', color: 'Kem', price: 429_000, targetStock: 6 },
      { sku: 'STY-AK-003-L', size: 'L', color: 'Kem', price: 429_000, targetStock: 1 },
    ],
  },
  {
    name: 'Quần jeans straight',
    description: 'Jeans ống suông, chất denim dày vừa.',
    category: 'Quần',
    brand: 'Local Brand',
    imageIndexes: [0],
    variants: [
      { sku: 'STY-QU-001-29', size: '29', color: 'Xanh đậm', price: 459_000, targetStock: 7 },
      { sku: 'STY-QU-001-30', size: '30', color: 'Xanh đậm', price: 459_000, targetStock: 8 },
      { sku: 'STY-QU-001-31', size: '31', color: 'Xanh đậm', price: 459_000, targetStock: 5 },
    ],
  },
  {
    name: 'Quần short kaki',
    description: 'Short kaki túi hộp, lên form gọn.',
    category: 'Quần',
    brand: 'Local Brand',
    imageIndexes: [1],
    variants: [
      { sku: 'STY-QU-002-29', size: '29', color: 'Kem', price: 289_000, targetStock: 6 },
      { sku: 'STY-QU-002-30', size: '30', color: 'Kem', price: 289_000, targetStock: 9 },
      { sku: 'STY-QU-002-31', size: '31', color: 'Kem', price: 289_000, targetStock: 7 },
    ],
  },
  {
    name: 'Quần jogger',
    description: 'Jogger nỉ co giãn, bo gấu.',
    category: 'Quần',
    brand: 'Basic Lab',
    imageIndexes: [2],
    variants: [
      { sku: 'STY-QU-003-M', size: 'M', color: 'Xám', price: 349_000, targetStock: 7 },
      { sku: 'STY-QU-003-L', size: 'L', color: 'Xám', price: 349_000, targetStock: 6 },
      { sku: 'STY-QU-003-XL', size: 'XL', color: 'Đen', price: 359_000, targetStock: 8 },
    ],
  },
  {
    name: 'Quần tây slim',
    description: 'Quần tây slim fit, lưng co giãn.',
    category: 'Quần',
    brand: 'Stylo',
    imageIndexes: [3],
    variants: [
      { sku: 'STY-QU-004-29', size: '29', color: 'Đen', price: 399_000, targetStock: 8 },
      { sku: 'STY-QU-004-30', size: '30', color: 'Đen', price: 399_000, targetStock: 7 },
      { sku: 'STY-QU-004-31', size: '31', color: 'Đen', price: 399_000, targetStock: 6 },
    ],
  },
  {
    name: 'Chân váy chữ A',
    description: 'Chân váy chữ A cạp cao, vải tuyết mưa.',
    category: 'Quần',
    brand: 'Urban Viet',
    imageIndexes: [4],
    variants: [
      { sku: 'STY-QU-005-S', size: 'S', color: 'Đen', price: 329_000, targetStock: 6 },
      { sku: 'STY-QU-005-M', size: 'M', color: 'Đen', price: 329_000, targetStock: 9 },
    ],
  },
  {
    name: 'Giày sneaker trắng',
    description: 'Sneaker trắng tối giản, đế cao su.',
    category: 'Giày',
    brand: 'Stylo',
    imageIndexes: [5],
    variants: [
      { sku: 'STY-GI-001-38', size: '38', color: 'Trắng', price: 799_000, targetStock: 10 },
      { sku: 'STY-GI-001-39', size: '39', color: 'Trắng', price: 799_000, targetStock: 9 },
      { sku: 'STY-GI-001-40', size: '40', color: 'Trắng', price: 799_000, targetStock: 8 },
      { sku: 'STY-GI-001-41', size: '41', color: 'Trắng', price: 799_000, targetStock: 6 },
      { sku: 'STY-GI-001-42', size: '42', color: 'Trắng', price: 799_000, targetStock: 0 },
    ],
  },
  {
    name: 'Giày slip-on',
    description: 'Slip-on vải canvas, lót êm.',
    category: 'Giày',
    brand: 'Basic Lab',
    imageIndexes: [0],
    variants: [
      { sku: 'STY-GI-002-38', size: '38', color: 'Be', price: 459_000, targetStock: 9 },
      { sku: 'STY-GI-002-39', size: '39', color: 'Be', price: 459_000, targetStock: 7 },
      { sku: 'STY-GI-002-40', size: '40', color: 'Be', price: 459_000, targetStock: 6 },
    ],
  },
  {
    name: 'Dép quai ngang',
    description: 'Dép quai ngang đế mềm, chống trượt.',
    category: 'Giày',
    brand: 'Local Brand',
    imageIndexes: [1],
    variants: [
      { sku: 'STY-GI-003-38', size: '38', color: 'Đen', price: 259_000, targetStock: 2 },
      { sku: 'STY-GI-003-39', size: '39', color: 'Đen', price: 259_000, targetStock: 9 },
      { sku: 'STY-GI-003-40', size: '40', color: 'Đen', price: 259_000, targetStock: 7 },
    ],
  },
  {
    name: 'Túi tote canvas',
    description: 'Túi tote canvas dày, quai dài.',
    category: 'Phụ kiện',
    brand: 'Stylo',
    status: 'INACTIVE',
    imageIndexes: [2],
    variants: [
      { sku: 'STY-PK-001-FREE', size: 'FREE', color: 'Kem', price: 189_000, targetStock: 0 },
      { sku: 'STY-PK-001-M', size: 'M', color: 'Kem', price: 199_000, targetStock: 0 },
      { sku: 'STY-PK-001-L', size: 'L', color: 'Kem', price: 199_000, targetStock: 0 },
    ],
  },
  {
    name: 'Mũ lưỡi trai',
    description: 'Mũ lưỡi trai vải kaki, khoá sau.',
    category: 'Phụ kiện',
    brand: 'Basic Lab',
    status: 'INACTIVE',
    imageIndexes: [3],
    variants: [
      { sku: 'STY-PK-002-FREE', size: 'FREE', color: 'Đen', price: 149_000, targetStock: 0 },
      { sku: 'STY-PK-002-M', size: 'M', color: 'Đen', price: 149_000, targetStock: 0 },
    ],
  },
  {
    name: 'Thắt lưng da',
    description: 'Thắt lưng da bò, khoá kim loại.',
    category: 'Phụ kiện',
    brand: 'Urban Viet',
    status: 'INACTIVE',
    imageIndexes: [4],
    variants: [
      { sku: 'STY-PK-003-M', size: 'M', color: 'Nâu', price: 249_000, targetStock: 0 },
      { sku: 'STY-PK-003-L', size: 'L', color: 'Nâu', price: 249_000, targetStock: 0 },
    ],
  },
  {
    name: 'Áo thun oversize',
    description: 'Áo thun oversize đã ngừng kinh doanh, còn lịch sử đơn.',
    category: 'Áo',
    brand: 'Stylo',
    status: 'INACTIVE',
    imageIndexes: [5],
    variants: [
      { sku: 'STY-AO-004-M', size: 'M', color: 'Xanh rêu', price: 249_000, targetStock: 3 },
      { sku: 'STY-AO-004-L', size: 'L', color: 'Xanh rêu', price: 249_000, targetStock: 3 },
      { sku: 'STY-AO-004-XL', size: 'XL', color: 'Đen', price: 259_000, targetStock: 2 },
    ],
  },
  {
    name: 'Áo ba lỗ thể thao',
    description: 'Áo ba lỗ thể thao mới lên kế hoạch, chưa có phân loại.',
    category: 'Áo',
    brand: 'Basic Lab',
    imageIndexes: [0],
    variants: [],
  },
  {
    name: 'Áo khoác dù',
    description: 'Áo khoác dù chống nước nhẹ, chưa chụp ảnh.',
    category: 'Áo khoác',
    brand: 'Urban Viet',
    imageIndexes: [],
    variants: [
      { sku: 'STY-AK-004-M', size: 'M', color: 'Xanh', price: 549_000, targetStock: 8 },
      { sku: 'STY-AK-004-L', size: 'L', color: 'Xanh', price: 549_000, targetStock: 10 },
      // Never received: no receipt line, so every cost report shows it with a null cost.
      { sku: 'STY-AK-004-XL', size: 'XL', color: 'Đen', price: 559_000, targetStock: 0 },
    ],
  },
]

const ORDERS: SeedOrder[] = [
  {
    code: 'STY-261010-0001',
    customer: 1,
    daysAgo: 28,
    status: 'DELIVERED',
    method: 'COD',
    items: [
      { sku: 'STY-AO-001-M', quantity: 1 },
      { sku: 'STY-QU-002-30', quantity: 1 },
    ],
  },
  {
    code: 'STY-261011-0002',
    customer: 2,
    daysAgo: 27,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-QU-001-30', quantity: 1 },
      { sku: 'STY-AO-001-S', quantity: 2 },
    ],
  },
  {
    code: 'STY-261013-0003',
    customer: 3,
    daysAgo: 25,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'FAILED_THEN_SUCCEEDED',
    note: 'Khách đổi thẻ sau lần thanh toán đầu.',
    items: [
      { sku: 'STY-AK-001-L', quantity: 1 },
      { sku: 'STY-AO-002-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261014-0004',
    customer: 4,
    daysAgo: 24,
    status: 'DELIVERED',
    method: 'COD',
    items: [
      { sku: 'STY-AO-001-M', quantity: 2 },
      { sku: 'STY-GI-002-39', quantity: 1 },
    ],
  },
  {
    code: 'STY-261016-0005',
    customer: 5,
    daysAgo: 22,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-QU-004-30', quantity: 1 },
      { sku: 'STY-AO-003-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261018-0006',
    customer: 6,
    daysAgo: 20,
    status: 'DELIVERED',
    method: 'COD',
    items: [
      { sku: 'STY-GI-001-40', quantity: 1 },
      { sku: 'STY-AO-002-L', quantity: 1 },
    ],
  },
  {
    code: 'STY-261020-0007',
    customer: 7,
    daysAgo: 18,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-AO-001-L', quantity: 2 },
      { sku: 'STY-QU-003-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261022-0008',
    customer: 8,
    daysAgo: 16,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-AK-002-M', quantity: 1 },
      { sku: 'STY-QU-001-29', quantity: 1 },
    ],
  },
  {
    code: 'STY-261024-0009',
    customer: 1,
    daysAgo: 14,
    status: 'DELIVERED',
    method: 'COD',
    items: [
      { sku: 'STY-AO-001-S', quantity: 3 },
      { sku: 'STY-QU-002-29', quantity: 1 },
    ],
  },
  {
    code: 'STY-261026-0010',
    customer: 9,
    daysAgo: 12,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-GI-001-38', quantity: 1 },
      { sku: 'STY-AO-003-L', quantity: 1 },
    ],
  },
  {
    code: 'STY-261028-0011',
    customer: 10,
    daysAgo: 10,
    status: 'DELIVERED',
    method: 'COD',
    items: [
      { sku: 'STY-AK-003-M', quantity: 1 },
      { sku: 'STY-QU-005-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261030-0012',
    customer: 2,
    daysAgo: 8,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-GI-001-42', quantity: 1 },
      { sku: 'STY-AO-004-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261101-0013',
    customer: 3,
    daysAgo: 6,
    status: 'DELIVERED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-QU-004-31', quantity: 1 },
      { sku: 'STY-AK-004-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261102-0014',
    customer: 4,
    daysAgo: 5,
    status: 'CANCELLED',
    method: 'COD',
    note: 'Khách huỷ trước khi giao.',
    items: [
      { sku: 'STY-GI-001-41', quantity: 1 },
      { sku: 'STY-AO-001-M', quantity: 1 },
    ],
  },
  {
    code: 'STY-261103-0015',
    customer: 5,
    daysAgo: 4,
    status: 'SHIPPING',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-AO-002-XL', quantity: 1 },
      { sku: 'STY-QU-003-L', quantity: 1 },
    ],
  },
  {
    code: 'STY-261104-0016',
    customer: 6,
    daysAgo: 3,
    status: 'SHIPPING',
    method: 'COD',
    items: [
      { sku: 'STY-AK-001-M', quantity: 1 },
      { sku: 'STY-GI-003-38', quantity: 1 },
    ],
  },
  {
    code: 'STY-261105-0017',
    customer: 7,
    daysAgo: 2,
    status: 'CONFIRMED',
    method: 'ONLINE',
    payment: 'SUCCEEDED',
    items: [
      { sku: 'STY-QU-001-30', quantity: 1 },
      { sku: 'STY-AO-003-S', quantity: 1 },
    ],
  },
  {
    code: 'STY-261105-0018',
    customer: 8,
    daysAgo: 2,
    status: 'CANCELLED',
    method: 'ONLINE',
    payment: 'FAILED',
    note: 'Thanh toán thất bại, khách không thử lại.',
    items: [{ sku: 'STY-AK-002-L', quantity: 1 }],
  },
  {
    code: 'STY-261106-0019',
    customer: 9,
    daysAgo: 1,
    status: 'PENDING',
    method: 'ONLINE',
    payment: 'PENDING',
    items: [{ sku: 'STY-AO-001-M', quantity: 2 }],
  },
  {
    code: 'STY-261107-0020',
    customer: 10,
    daysAgo: 0,
    status: 'DELIVERED',
    method: 'COD',
    items: [
      { sku: 'STY-GI-001-42', quantity: 1 },
      { sku: 'STY-AO-004-L', quantity: 1 },
    ],
  },
]

const RECEIPTS = [
  {
    code: 'PN-261003-0001',
    supplier: 'Xưởng may Hà Nội',
    note: 'Lô đầu mùa thu.',
    daysAgo: 35,
    share: 0 as const,
  },
  {
    code: 'PN-261009-0002',
    supplier: 'Kho tổng Sài Gòn',
    note: 'Bổ sung sizes bán chạy.',
    daysAgo: 29,
    share: 1 as const,
  },
  {
    code: 'PN-261014-0003',
    supplier: 'Nhà phân phối Đông Á',
    note: 'Nhập thêm với giá điều chỉnh.',
    daysAgo: 24,
    share: 2 as const,
  },
]

const SEED_IMAGES = ['catalog-01.png', 'catalog-02.png', 'catalog-03.png', 'catalog-04.png', 'catalog-05.png', 'catalog-06.png']

function imageUrl(filename: string): string {
  return `/uploads/products/${filename}`
}

function clientOrderId(index: number): string {
  return `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`
}

function addressFor(customerIndex: number): { defaultLine: string; secondaryLine: string; phone: string } {
  const phone = `09${String(1_000_000 + customerIndex).padStart(8, '0')}`
  return {
    defaultLine: `Số ${10 + customerIndex} Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh`,
    secondaryLine: `Số ${20 + customerIndex} Lê Lợi, Phường Tân Định, Quận 3, TP. Hồ Chí Minh`,
    phone,
  }
}

interface VariantRow {
  id: number
  productId: number
  productName: string
  sku: string
  size: string
  color: string
  price: number
}

interface ReceiptLinePlan {
  variant: VariantRow
  quantity: number
  unitCost: number
  share: 0 | 1 | 2
}

function copySeedImages(): void {
  const assetsDir = fileURLToPath(new URL('../seed-assets/', import.meta.url))
  const uploadDir = path.resolve(getEnv().UPLOAD_DIR, 'products')
  mkdirSync(uploadDir, { recursive: true })

  for (const filename of SEED_IMAGES) {
    const target = path.join(uploadDir, filename)
    if (existsSync(target)) continue
    copyFileSync(path.join(assetsDir, filename), target)
  }
}

export async function seed(): Promise<void> {
  const env = getEnv()
  const adminEmail = env.SEED_ADMIN_EMAIL.toLowerCase()
  const adminPasswordHash = await argon2.hash(env.SEED_ADMIN_PASSWORD)
  const customerPasswordHash = await argon2.hash(CUSTOMER_PASSWORD)

  const admin = await prisma.user.upsert({
    where: { email: adminEmail },
    update: {
      fullName: env.SEED_ADMIN_NAME,
      passwordHash: adminPasswordHash,
      role: 'ADMIN',
      status: 'ACTIVE',
    },
    create: {
      email: adminEmail,
      passwordHash: adminPasswordHash,
      fullName: env.SEED_ADMIN_NAME,
      role: 'ADMIN',
      status: 'ACTIVE',
    },
  })

  const customers = new Map<number, { id: number; fullName: string }>()
  for (const [index, email] of CUSTOMER_EMAILS.entries()) {
    const number = index + 1
    const status: UserStatus = number === DISABLED_CUSTOMER_INDEX ? 'DISABLED' : 'ACTIVE'
    const user = await prisma.user.upsert({
      where: { email },
      update: { fullName: CUSTOMER_NAMES[index], passwordHash: customerPasswordHash, status },
      create: {
        email,
        passwordHash: customerPasswordHash,
        fullName: CUSTOMER_NAMES[index],
        phone: addressFor(number).phone,
        role: 'CUSTOMER',
        status,
      },
    })
    customers.set(number, { id: user.id, fullName: user.fullName })
  }

  for (const number of customers.keys()) {
    if (number === DISABLED_CUSTOMER_INDEX) continue
    const customer = customers.get(number)
    if (!customer) throw new Error(`Missing seeded customer ${number}`)
    const address = addressFor(number)
    const rows = [
      { line: address.defaultLine, isDefault: true },
      { line: address.secondaryLine, isDefault: false },
    ]
    for (const row of rows) {
      const existing = await prisma.address.findFirst({ where: { userId: customer.id, line: row.line } })
      if (existing) {
        await prisma.address.update({
          where: { id: existing.id },
          data: { recipientName: customer.fullName, phone: address.phone, isDefault: row.isDefault },
        })
      } else {
        await prisma.address.create({
          data: {
            userId: customer.id,
            recipientName: customer.fullName,
            phone: address.phone,
            line: row.line,
            isDefault: row.isDefault,
          },
        })
      }
    }
  }

  const categories = new Map<string, number>()
  for (const entry of CATEGORIES) {
    const category = await prisma.category.upsert({
      where: { name: entry.name },
      update: { status: entry.status },
      create: { name: entry.name, status: entry.status },
    })
    categories.set(entry.name, category.id)
  }

  const brands = new Map<string, number>()
  for (const name of BRANDS) {
    const brand = await prisma.brand.upsert({
      where: { name },
      update: {},
      create: { name },
    })
    brands.set(name, brand.id)
  }

  copySeedImages()

  const variantsBySku = new Map<string, VariantRow>()
  for (const entry of PRODUCTS) {
    const categoryId = categories.get(entry.category)
    const brandId = brands.get(entry.brand)
    if (categoryId === undefined || brandId === undefined) {
      throw new Error(`Seed product ${entry.name} references an unknown category or brand`)
    }

    let product = await prisma.product.findFirst({ where: { name: entry.name } })
    if (product) {
      product = await prisma.product.update({
        where: { id: product.id },
        data: {
          description: entry.description,
          categoryId,
          brandId,
          status: entry.status ?? 'ACTIVE',
        },
      })
    } else {
      product = await prisma.product.create({
        data: {
          name: entry.name,
          description: entry.description,
          categoryId,
          brandId,
          status: entry.status ?? 'ACTIVE',
        },
      })
    }

    for (const [position, imageIndex] of entry.imageIndexes.entries()) {
      const filename = SEED_IMAGES[imageIndex]
      await prisma.productImage.upsert({
        where: { productId_position: { productId: product.id, position } },
        update: { url: imageUrl(filename) },
        create: { productId: product.id, position, url: imageUrl(filename) },
      })
    }

    for (const variant of entry.variants) {
      const saved = await prisma.productVariant.upsert({
        where: { sku: variant.sku },
        update: {
          productId: product.id,
          size: variant.size,
          color: variant.color,
          price: variant.price,
        },
        create: {
          productId: product.id,
          sku: variant.sku,
          size: variant.size,
          color: variant.color,
          price: variant.price,
        },
      })
      variantsBySku.set(variant.sku, {
        id: saved.id,
        productId: product.id,
        productName: entry.name,
        sku: saved.sku,
        size: saved.size,
        color: saved.color,
        price: saved.price,
      })
    }
  }

  const soldBySku = new Map<string, number>()
  for (const order of ORDERS) {
    if (order.status === 'CANCELLED') continue
    for (const item of order.items) {
      soldBySku.set(item.sku, (soldBySku.get(item.sku) ?? 0) + item.quantity)
    }
  }

  const targetBySku = new Map<string, number>()
  for (const product of PRODUCTS) {
    for (const variant of product.variants) targetBySku.set(variant.sku, variant.targetStock)
  }

  const receiptLines: ReceiptLinePlan[] = []
  const costTotals = new Map<string, { quantity: number; cost: number }>()
  const variants = [...variantsBySku.values()]
  for (const [index, variant] of variants.entries()) {
    const quantity = (targetBySku.get(variant.sku) ?? 0) + (soldBySku.get(variant.sku) ?? 0)
    if (quantity <= 0) continue

    const costs = [
      Math.round(variant.price * 0.42),
      Math.round(variant.price * 0.48),
      Math.round(variant.price * 0.55),
    ] as const

    const third = index % 4 === 0 && quantity >= 4 ? 2 : 0
    const remaining = quantity - third
    const first = Math.ceil(remaining / 2)
    const second = remaining - first

    const shares: Array<[0 | 1 | 2, number]> = [
      [0, first],
      [1, second],
      [2, third],
    ]
    for (const [share, count] of shares) {
      if (count <= 0) continue
      const unitCost = costs[share]
      receiptLines.push({ variant, quantity: count, unitCost, share })
      const total = costTotals.get(variant.sku) ?? { quantity: 0, cost: 0 }
      total.quantity += count
      total.cost += count * unitCost
      costTotals.set(variant.sku, total)
    }
  }

  const averageCostBySku = new Map<string, number>()
  for (const [sku, total] of costTotals) {
    averageCostBySku.set(sku, Math.round(total.cost / total.quantity))
  }

  for (const receiptPlan of RECEIPTS) {
    const existing = await prisma.inventoryReceipt.findUnique({ where: { code: receiptPlan.code } })
    if (existing) continue

    const lines = receiptLines.filter((line) => line.share === receiptPlan.share)
    const totalCost = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0)
    const createdAt = daysAgoAt(receiptPlan.daysAgo, 2)
    const receipt = await prisma.inventoryReceipt.create({
      data: {
        code: receiptPlan.code,
        supplier: receiptPlan.supplier,
        note: receiptPlan.note,
        totalCost,
        createdBy: admin.id,
        createdAt,
        items: {
          create: lines.map((line) => ({
            variantId: line.variant.id,
            quantity: line.quantity,
            unitCost: line.unitCost,
          })),
        },
      },
    })

    await prisma.stockMovement.createMany({
      data: lines.map((line) => ({
        variantId: line.variant.id,
        delta: line.quantity,
        reason: 'RECEIPT',
        refType: 'RECEIPT',
        refId: receipt.id,
        unitCost: line.unitCost,
        createdAt,
      })),
    })
  }

  for (const [index, orderPlan] of ORDERS.entries()) {
    const existing = await prisma.order.findUnique({ where: { code: orderPlan.code } })
    if (existing) continue

    const customer = customers.get(orderPlan.customer)
    if (!customer) throw new Error(`Seed order ${orderPlan.code} references an unknown customer`)

    const address = await prisma.address.findFirst({
      where: { userId: customer.id, isDefault: true },
    })
    if (!address) throw new Error(`Seed order ${orderPlan.code} needs a default address`)

    const createdAt = orderTime(orderPlan.daysAgo)
    const items = orderPlan.items.map((item) => {
      const variant = variantsBySku.get(item.sku)
      if (!variant) throw new Error(`Seed order ${orderPlan.code} references unknown SKU ${item.sku}`)
      return { ...item, variant }
    })
    const subtotal = items.reduce((sum, item) => sum + item.variant.price * item.quantity, 0)
    const shippingFee = env.SHIPPING_FEE
    const total = subtotal + shippingFee

    let paidAt: Date | null = null
    if (orderPlan.method === 'ONLINE') {
      if (orderPlan.payment === 'SUCCEEDED') paidAt = addHours(createdAt, 0.1)
      if (orderPlan.payment === 'FAILED_THEN_SUCCEEDED') paidAt = addHours(createdAt, 1.1)
    } else if (orderPlan.status === 'DELIVERED') {
      paidAt = earlier(addHours(createdAt, 48), new Date(NOW.getTime() - 60_000))
    }

    const order = await prisma.order.create({
      data: {
        code: orderPlan.code,
        userId: customer.id,
        clientOrderId: clientOrderId(index + 1),
        status: orderPlan.status,
        paymentMethod: orderPlan.method,
        paidAt,
        recipientName: address.recipientName,
        recipientPhone: address.phone,
        shippingAddress: address.line,
        subtotal,
        shippingFee,
        total,
        note: orderPlan.note ?? null,
        createdAt,
        items: {
          create: items.map((item) => ({
            productId: item.variant.productId,
            variantId: item.variant.id,
            productName: item.variant.productName,
            variantSku: item.variant.sku,
            size: item.variant.size,
            color: item.variant.color,
            unitPrice: item.variant.price,
            unitCost: averageCostBySku.get(item.variant.sku) ?? null,
            quantity: item.quantity,
          })),
        },
      },
    })

    await prisma.stockMovement.createMany({
      data: items.map((item) => ({
        variantId: item.variant.id,
        delta: -item.quantity,
        reason: 'SALE',
        refType: 'ORDER',
        refId: order.id,
        unitCost: null,
        createdAt,
      })),
    })

    if (orderPlan.status === 'CANCELLED') {
      await prisma.stockMovement.createMany({
        data: items.map((item) => ({
          variantId: item.variant.id,
          delta: item.quantity,
          reason: 'CANCEL_RESTOCK',
          refType: 'ORDER',
          refId: order.id,
          unitCost: null,
          createdAt: addHours(createdAt, 2),
        })),
      })
    }

    if (orderPlan.method === 'ONLINE' && orderPlan.payment) {
      const failureAt = addHours(createdAt, 0.05)
      const successAt = addHours(createdAt, 1.1)
      const attempts: Array<{ status: 'SUCCEEDED' | 'FAILED' | 'PENDING'; createdAt: Date; payDate: Date | null }> = []
      if (orderPlan.payment === 'SUCCEEDED') {
        attempts.push({ status: 'SUCCEEDED', createdAt: failureAt, payDate: failureAt })
      } else if (orderPlan.payment === 'FAILED') {
        attempts.push({ status: 'FAILED', createdAt: failureAt, payDate: null })
      } else if (orderPlan.payment === 'PENDING') {
        attempts.push({ status: 'PENDING', createdAt: failureAt, payDate: null })
      } else {
        attempts.push({ status: 'FAILED', createdAt: failureAt, payDate: null })
        attempts.push({ status: 'SUCCEEDED', createdAt: successAt, payDate: successAt })
      }

      for (const [attemptIndex, attempt] of attempts.entries()) {
        await prisma.payment.create({
          data: {
            orderId: order.id,
            provider: 'MOCK',
            status: attempt.status,
            amount: total,
            txnRef: `SEED-${orderPlan.code}-${attemptIndex + 1}`,
            providerTxnNo: attempt.status === 'SUCCEEDED' ? `MOCK-${orderPlan.code}-${attemptIndex + 1}` : null,
            bankCode: attempt.status === 'SUCCEEDED' ? 'NCB' : null,
            payDate: attempt.payDate,
            failureReason: attempt.status === 'FAILED' ? 'SEED_FAILED' : null,
            createdAt: attempt.createdAt,
          },
        })
      }
    }
  }

  // Make the ledger the single source of truth for stock, even when a partial seed is re-run.
  await prisma.$executeRaw`
    UPDATE product_variant v
    LEFT JOIN (
      SELECT variant_id, SUM(delta) AS total
      FROM stock_movement
      GROUP BY variant_id
    ) m ON m.variant_id = v.id
    SET v.stock = COALESCE(m.total, 0)
  `

  const variantCount = PRODUCTS.reduce((sum, product) => sum + product.variants.length, 0)
  console.log(
    `Seed complete: 1 admin, ${CUSTOMER_EMAILS.length} customers, ${CATEGORIES.length} categories, ` +
      `${BRANDS.length} brands, ${PRODUCTS.length} products, ${variantCount} variants, ` +
      `${RECEIPTS.length} receipts, ${ORDERS.length} orders`,
  )
}

const isDirectRun = process.argv[1] !== undefined && /seed\.(?:ts|js)$/.test(process.argv[1])
if (isDirectRun) {
  seed()
    .catch((error: unknown) => {
      console.error('Seed failed', error)
      process.exitCode = 1
    })
    .finally(async () => {
      await prisma.$disconnect()
    })
}
