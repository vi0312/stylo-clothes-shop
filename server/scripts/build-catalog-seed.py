#!/usr/bin/env python3
"""Build the committed seed catalog from a public, real-product dataset.

Source: Fashion Product Images Dataset (Kaggle, paramaggarwal), read through the
Hugging Face dataset mirror mecha2019/fashion-product-images-small.

The script is a one-off maintenance tool. The seed itself never downloads
anything: it reads `seed-assets/catalog/catalog.json` and the committed images.

Usage:
    python server/scripts/build-catalog-seed.py

Requires Pillow. No API key and no dataset download is needed.
"""

from __future__ import annotations

import hashlib
import io
import json
import sys
import tempfile
import time
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import date
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "seed-assets" / "catalog"
IMAGE_DIR = OUT_DIR / "images"
CATALOG_PATH = OUT_DIR / "catalog.json"

DATASET = "mecha2019%2Ffashion-product-images-small"
ROWS_URL = (
    "https://datasets-server.huggingface.co/rows"
    f"?dataset={DATASET}&config=default&split=train"
)
USER_AGENT = "stylo-seed-builder/1.0 (+coursework demo)"

BRANDS = ["Stylo", "Local Brand", "Urban Viet", "Basic Lab"]

# (category, articleType) -> how many products to take.
# Clothing only; the pinned T-shirt counts inside Tshirts.
QUOTAS: dict[tuple[str, str], int] = {
    ("Áo", "Tshirts"): 47,
    ("Áo", "Shirts"): 28,
    ("Áo", "Tops"): 12,
    ("Áo", "Sweaters"): 8,
    ("Quần", "Jeans"): 16,
    ("Quần", "Trousers"): 16,
    ("Quần", "Shorts"): 12,
    ("Quần", "Track Pants"): 10,
    ("Quần", "Leggings"): 4,
    ("Quần", "Capris"): 2,
    ("Áo khoác", "Jackets"): 16,
    ("Áo khoác", "Sweatshirts"): 14,
}

ARTICLE: dict[str, tuple[str, str, tuple[str, str]]] = {
    # articleType -> (category, Vietnamese noun, price range VND)
    "Tshirts": ("Áo", "Áo thun", (149_000, 349_000)),
    "Shirts": ("Áo", "Áo sơ mi", (299_000, 549_000)),
    "Tops": ("Áo", "Áo kiểu", (199_000, 399_000)),
    "Sweaters": ("Áo", "Áo len", (349_000, 599_000)),
    "Jackets": ("Áo khoác", "Áo khoác", (499_000, 1_299_000)),
    "Sweatshirts": ("Áo khoác", "Áo nỉ", (399_000, 699_000)),
    "Jeans": ("Quần", "Quần jean", (399_000, 799_000)),
    "Trousers": ("Quần", "Quần dài", (349_000, 699_000)),
    "Shorts": ("Quần", "Quần short", (199_000, 399_000)),
    "Track Pants": ("Quần", "Quần thể thao", (299_000, 599_000)),
    "Leggings": ("Quần", "Quần legging", (199_000, 349_000)),
    "Capris": ("Quần", "Quần capri", (249_000, 399_000)),
}

COLORS = {
    "Black": "Đen",
    "White": "Trắng",
    "Blue": "Xanh dương",
    "Brown": "Nâu",
    "Grey": "Xám",
    "Red": "Đỏ",
    "Green": "Xanh lá",
    "Pink": "Hồng",
    "Navy Blue": "Xanh navy",
    "Purple": "Tím",
    "Silver": "Bạc",
    "Beige": "Be",
    "Yellow": "Vàng",
    "Gold": "Vàng ánh kim",
    "Maroon": "Đỏ đô",
    "Orange": "Cam",
    "Multi": "Đa sắc",
    "Steel": "Xám thép",
    "Cream": "Kem",
    "Olive": "Xanh olive",
    "Off White": "Trắng ngà",
    "Charcoal": "Xám than",
    "Peach": "Đào",
    "Grey Melange": "Xám melange",
    "Khaki": "Kaki",
    "Teal": "Xanh mòng két",
    "Copper": "Đồng",
    "Magenta": "Đỏ hồng",
    "Mustard": "Vàng mù tạt",
    "Skin": "Da",
    "Tan": "Nâu nhạt",
    "Turquoise Blue": "Xanh ngọc",
    "Mauve": "Tím mauve",
    "Lavender": "Oải hương",
    "Rust": "Nâu gỉ",
    "Bronze": "Đồng ánh kim",
    "Mushroom Brown": "Nâu nấm",
    "Metallic": "Ánh kim",
    "Sea Green": "Xanh biển",
    "Burgundy": "Đỏ rượu",
    "Nude": "Nude",
    "Rose": "Hồng phấn",
    "Taupe": "Nâu xám",
}

GENDER = {"Men": "nam", "Women": "nữ", "Unisex": "unisex"}

USAGE = {
    "Casual": "dạo phố",
    "Sports": "thể thao",
    "Ethnic": "truyền thống",
    "Formal": "công sở",
    "Smart Casual": "smart casual",
    "Party": "dự tiệc",
    "Travel": "du lịch",
}

# displayName fragments -> Vietnamese descriptors, checked in order
DESCRIPTORS = [
    ("check", "ca-rô"),
    ("strip", "kẻ sọc"),
    ("print", "in họa tiết"),
    ("graphic", "in hình"),
    ("embroider", "thêu"),
    ("pleat", "xếp ly"),
    ("puffer", "phao"),
    ("hood", "có mũ"),
    ("round neck", "cổ tròn"),
    ("v-neck", "cổ V"),
    ("polo", "cổ bẻ"),
    ("slim", "dáng slim"),
    ("regular", "dáng regular"),
    ("skinny", "dáng skinny"),
    ("distress", "bụi"),
    ("washed", "bạc màu"),
    ("ripped", "rách"),
    ("high waist", "lưng cao"),
    ("low waist", "lưng thấp"),
    ("running", "chạy bộ"),
    ("training", "tập luyện"),
    ("leather", "da"),
    ("suede", "da lộn"),
    ("canvas", "vải canvas"),
    ("mesh", "lưới"),
    ("ankle", "cổ thấp"),
    ("boot", "cổ cao"),
    ("solid", "trơn"),
]

FALLBACK_DESCRIPTORS = ["form regular", "form rộng", "phong cách tối giản", "dễ phối đồ"]

SIZES = {
    "Áo": ["S", "M", "L", "XL"],
    "Áo khoác": ["S", "M", "L", "XL"],
    "Quần": ["28", "29", "30", "31", "32"],
}

DESCRIPTIONS = {
    "Tshirts": "Áo thun {gender} màu {color}, chất liệu cotton co giãn, {style}. Phù hợp mặc {usage} hằng ngày.",
    "Shirts": "Áo sơ mi {gender} màu {color}, chất liệu linen pha cotton, {style}. Phối tốt với quần tây hoặc jean.",
    "Tops": "Áo kiểu {gender} màu {color}, thiết kế {style}, chất liệu mềm nhẹ. Phù hợp mặc {usage}.",
    "Sweaters": "Áo len {gender} màu {color}, chất liệu giữ ấm nhẹ, {style}. Phù hợp thời tiết se lạnh.",
    "Jackets": "Áo khoác {gender} màu {color}, {style}, giữ ấm và chắn gió tốt.",
    "Sweatshirts": "Áo nỉ {gender} màu {color}, {style}, chất liệu nỉ bông mềm, phù hợp mặc hằng ngày.",
    "Jeans": "Quần jean {gender} màu {color}, {style}, chất liệu denim bền, dễ phối đồ.",
    "Trousers": "Quần dài {gender} màu {color}, form đứng, {style}. Phù hợp công sở và dạo phố.",
    "Shorts": "Quần short {gender} màu {color}, chất liệu nhẹ, {style}. Thoáng mát cho ngày nóng.",
    "Track Pants": "Quần thể thao {gender} màu {color}, co giãn, {style}. Phù hợp vận động.",
    "Leggings": "Quần legging {gender} màu {color}, co giãn ôm vừa, {style}. Dễ mặc hằng ngày.",
    "Capris": "Quần capri {gender} màu {color}, dáng lửng, {style}. Phù hợp mùa hè.",
}

SKU_PREFIX = {"Áo": "AO", "Quần": "QU", "Áo khoác": "AK"}


def stable_int(*parts: object) -> int:
    digest = hashlib.sha256("|".join(str(p) for p in parts).encode()).hexdigest()
    return int(digest[:12], 16)


def fetch_rows(batches: int = 40, batch: int = 100) -> list[dict]:
    cache_path = Path(tempfile.gettempdir()) / "stylo-catalog-rows.json"
    cached: dict = {"offsets": [], "rows": {}}
    if cache_path.exists():
        cached = json.loads(cache_path.read_text(encoding="utf-8"))
        cached["rows"] = {int(row["id"]): row for row in cached["rows"]}
        print(f"  using cached rows ({len(cached['rows'])})", flush=True)

    rows: dict[int, dict] = cached["rows"]
    fetched: set[int] = set(cached["offsets"])
    step = 42_426 // batches
    for index in range(batches):
        offset = index * step
        if offset in fetched:
            continue
        url = f"{ROWS_URL}&offset={offset}&length={batch}"
        for attempt in range(8):
            try:
                with urllib.request.urlopen(url, timeout=45) as response:
                    payload = json.load(response)
                for item in payload["rows"]:
                    row = item["row"]
                    rows[int(row["id"])] = row
                fetched.add(offset)
                break
            except urllib.error.HTTPError as error:
                if error.code == 429:
                    wait = int(error.headers.get("Retry-After") or 60)
                    print(f"  rate limited, waiting {wait}s", flush=True)
                    time.sleep(wait)
                    continue
                if attempt == 7:
                    raise SystemExit(f"dataset fetch failed at offset {offset}: {error}") from error
                time.sleep(2 * (attempt + 1))
            except (urllib.error.URLError, TimeoutError, KeyError, json.JSONDecodeError) as error:
                if attempt == 7:
                    raise SystemExit(f"dataset fetch failed at offset {offset}: {error}") from error
                time.sleep(2 * (attempt + 1))
        print(f"  fetched offset {offset} ({len(rows)} unique rows)", flush=True)
        cache_path.write_text(
            json.dumps({"offsets": sorted(fetched), "rows": list(rows.values())}, ensure_ascii=False),
            encoding="utf-8",
        )
        time.sleep(1.2)
    return [rows[key] for key in sorted(rows)]


def descriptor(source_name: str) -> str:
    lowered = source_name.lower()
    found = [vietnamese for token, vietnamese in DESCRIPTORS if token in lowered]
    return found[0] if found else "trơn"


def all_descriptors(source_name: str) -> list[str]:
    lowered = source_name.lower()
    found = [vietnamese for token, vietnamese in DESCRIPTORS if token in lowered]
    return found or ["trơn"]


def variants_for(row: dict, category: str, price: int) -> list[dict]:
    sizes = SIZES[category]

    seed = stable_int(row["id"], "sizes")
    count = 2 + seed % min(3, len(sizes) - 1)
    start = (seed // 7) % (len(sizes) - count + 1)
    chosen = sizes[start : start + count]

    profile = stable_int(row["id"], "stock") % 20
    base_stock = 0 if profile == 0 else (1 + seed % 4 if profile in (1, 2) else 5 + seed % 31)

    variants = []
    for size in chosen:
        stock = 0 if base_stock == 0 else max(0, base_stock - (seed % 3))
        variants.append(
            {
                "size": size,
                "color": COLORS[row["baseColour"]],
                "price": price,
                "targetStock": stock,
            }
        )
    return variants


def pick_ordered(pool: list[dict]) -> list[dict]:
    """Interleave men and women so the catalog has both."""
    pools = {
        "Men": [r for r in pool if r["gender"] == "Men"],
        "Women": [r for r in pool if r["gender"] == "Women"],
        "Unisex": [r for r in pool if r["gender"] == "Unisex"],
    }
    ordered: list[dict] = []
    while any(pools.values()):
        for gender in ("Men", "Women", "Unisex"):
            if pools[gender]:
                ordered.append(pools[gender].pop(0))
    return ordered


def download_image(row: dict, target: Path) -> bool:
    if target.exists() and target.stat().st_size > 10_000:
        return True
    request = urllib.request.Request(row["image"], headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read()
    except (urllib.error.URLError, TimeoutError):
        return False
    try:
        image = Image.open(io.BytesIO(data)).convert("RGB")
    except Exception:
        return False
    image = image.resize((600, 800), Image.LANCZOS)
    image.save(target, "JPEG", quality=82, optimize=True, progressive=True)
    return True


def main() -> None:
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)

    print("Fetching dataset rows...", flush=True)
    rows = fetch_rows()
    print(f"  {len(rows)} unique rows", flush=True)

    candidates: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for row in rows:
        article = row.get("articleType")
        mapped = ARTICLE.get(article)
        if not mapped:
            continue
        if row.get("gender") not in GENDER:
            continue
        if row.get("baseColour") not in COLORS:
            continue
        if not row.get("image"):
            continue
        category, _, _ = mapped
        candidates[(category, article)].append(row)
    for key in candidates:
        candidates[key] = pick_ordered(candidates[key])

    # Pinned product: a real men's black T-shirt image, exact name kept for tests/E2E.
    pinned_row = None
    for row in candidates[("Áo", "Tshirts")]:
        if row["gender"] == "Men" and row["baseColour"] in ("Black", "White"):
            pinned_row = row
            break
    if pinned_row is None:
        raise SystemExit("could not find a pinned T-shirt candidate")

    used_ids: set[int] = set()
    products: list[dict] = []

    def add_product(row: dict, pinned: bool = False) -> bool:
        if row["id"] in used_ids:
            return False
        image_name = f"p{row['id']}.jpg"
        if not download_image(row, IMAGE_DIR / image_name):
            print(f"  skip image {row['id']}", flush=True)
            return False
        used_ids.add(row["id"])

        article = row["articleType"]
        category, noun, price_range = ARTICLE[article]
        color = COLORS[row["baseColour"]]
        gender = GENDER[row["gender"]]
        style = descriptor(row["productDisplayName"])
        usage = USAGE.get(row["usage"], "hằng ngày")

        if pinned:
            name = "Áo thun basic"
            description = "Áo thun cotton mềm, form regular, dễ phối đồ hằng ngày."
            variants = [
                {"size": "S", "color": "Đen", "price": 199_000, "targetStock": 12},
                {"size": "M", "color": "Đen", "price": 199_000, "targetStock": 13},
                {"size": "L", "color": "Trắng", "price": 209_000, "targetStock": 3},
            ]
            product_status = "ACTIVE"
        else:
            low, high = price_range
            seed = stable_int(row["id"], "price")
            price = int(round((low + (high - low) * (seed % 1000) / 999) / 10_000) * 10_000)
            name = f"{noun} {gender} {style} màu {color.lower()}"
            description = DESCRIPTIONS[article].format(
                gender=gender, color=color.lower(), style=style, usage=usage
            )
            variants = variants_for(row, category, price)
            product_status = "INACTIVE" if len(products) % 8 == 5 else "ACTIVE"

        # Names must stay unique because the seed matches products by name.
        if any(p["name"] == name for p in products):
            for fallback in FALLBACK_DESCRIPTORS:
                candidate_name = f"{name} {fallback}"
                if not any(p["name"] == candidate_name for p in products):
                    name = candidate_name
                    break
            else:
                suffix = 2
                while any(p["name"] == f"{name} {suffix}" for p in products):
                    suffix += 1
                name = f"{name} {suffix}"

        products.append(
            {
                "datasetId": row["id"],
                "sourceName": row["productDisplayName"],
                "name": name,
                "description": description,
                "category": category,
                "brand": BRANDS[len(products) % len(BRANDS)],
                "status": product_status,
                "image": image_name,
                "variants": variants,
            }
        )
        return True

    if not add_product(pinned_row, pinned=True):
        raise SystemExit("could not download pinned image")

    # Category counters assign stable SKUs; the pinned product is first, so AO-001.
    counters = {category: 0 for category in SKU_PREFIX}

    def assign_skus(product: dict) -> None:
        category = product["category"]
        prefix = SKU_PREFIX[category]
        counters[category] += 1
        for variant in product["variants"]:
            variant["sku"] = (
                f"STY-{prefix}-{counters[category]:03d}-{variant['size']}"
            )

    for (category, article), quota in QUOTAS.items():
        remaining = quota - 1 if (category, article) == ("Áo", "Tshirts") else quota
        if remaining <= 0:
            continue
        taken = 0
        for row in candidates.get((category, article), []):
            if taken >= remaining:
                break
            if add_product(row):
                taken += 1
        if taken < remaining:
            raise SystemExit(
                f"not enough {category}/{article} candidates: wanted {remaining}, got {taken}"
            )

    # SKUs are assigned after all products exist, in catalog order.
    for product in products:
        assign_skus(product)

    target_total = sum(QUOTAS.values())
    if len(products) != target_total:
        raise SystemExit(f"expected {target_total} products, built {len(products)}")

    catalog = {
        "source": {
            "name": "Fashion Product Images Dataset (Kaggle, paramaggarwal)",
            "mirror": "https://huggingface.co/datasets/mecha2019/fashion-product-images-small",
            "original": "https://www.kaggle.com/datasets/paramaggarwal/fashion-product-images-dataset",
            "license": (
                "Unknown. Product images originate from Myntra and the mirror declares no "
                "license. Use for coursework/demo only; do not redistribute commercially."
            ),
            "retrievedAt": date.today().isoformat(),
            "selection": (
                f"{len(products)} products from 42,426 rows; clothing only (Áo, Quần, Áo khoác); "
                "Men/Women/Unisex; mapped to the Stylo category and brand model; prices, "
                "sizes and stock are deterministic demo values, not source facts."
            ),
        },
        "products": products,
    }
    with CATALOG_PATH.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    referenced = {product["image"] for product in products}
    for stale in IMAGE_DIR.glob("*.jpg"):
        if stale.name not in referenced:
            stale.unlink()
    image_bytes = sum(path.stat().st_size for path in IMAGE_DIR.glob("*.jpg"))
    print(
        f"Wrote {len(products)} products, "
        f"{len(list(IMAGE_DIR.glob('*.jpg')))} images, "
        f"{image_bytes / 1024 / 1024:.1f} MB -> {CATALOG_PATH}",
        flush=True,
    )


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
