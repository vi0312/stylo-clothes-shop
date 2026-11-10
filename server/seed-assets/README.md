# Seed assets

`prisma/seed.ts` reads the committed catalog snapshot from `catalog/catalog.json`.
The seed never downloads anything: it copies the images below into
`$UPLOAD_DIR/products/` and inserts the snapshot rows.

## Source

- Dataset: [Fashion Product Images Dataset](https://www.kaggle.com/datasets/paramaggarwal/fashion-product-images-dataset)
  (Kaggle, `paramaggarwal`), read through the
  [Hugging Face mirror](https://huggingface.co/datasets/mecha2019/fashion-product-images-small).
- Selection: 185 clothing products from 4,000 sampled rows of 42,426, Apparel
  only, Men/Women/Unisex only, mapped to the Stylo categories (`Áo`, `Quần`,
  `Áo khoác`) and brands (`Stylo`, `Local Brand`, `Urban Viet`, `Basic Lab`).
  Footwear is excluded.
- Vietnamese names and descriptions are translated and normalized by the generator;
  source product names are kept in `sourceName` for provenance.
- Real source facts: product type, base colour (the product's first colour), gender,
  usage, image.
- Demo values: price, sizes, 1-2 additional colours per product, stock, orders, receipts,
  payments. The dataset has no price, colour-option or inventory data.

## Layout

| Path | Contents |
|---|---|
| `catalog/catalog.json` | 185 products, 1330 variants, source metadata |
| `catalog/images/p<id>.jpg` | 600x800 JPEG, quality 82, about 6.9 MB total |
| `../scripts/build-catalog-seed.py` | one-off generator, not used at seed time |

## Colours and SKUs

Every product carries its source base colour plus one or two generated colours (2-3
colours, 4-12 variants), so the product detail screen has a colour selector. Variants are
the cross product of the chosen sizes and colours. SKUs include a colour token, e.g.
`STY-AO-002-XAM-M`; the pinned product is the only exception.

## Pinned product

`Áo thun basic` (`STY-AO-001-S/M/L`) is a fixed snapshot row with known price and
stock. The integration catalog test and the Kaspresso journeys search for it, so do
not rename it without updating those tests. The seed creates it last so the public
"newest" sort shows it first and the E2E grid does not need to scroll.

## Regenerate

```bash
python server/scripts/build-catalog-seed.py
```

Requires Pillow. The script caches dataset rows in the system temp directory
(`stylo-catalog-rows.json`), reuses already downloaded images, and rewrites the snapshot
deterministically with LF line endings. Run it only when changing the selection or
normalization rules; it reallocates SKUs and product order, so afterwards reset the database
with `npm run db:reset`.

## License

The Hugging Face mirror declares **no license**, and the images originate from
Myntra. Treat this snapshot as coursework/demo data only, do not redistribute it
commercially. For a public release, replace these images with licensed stock photos
or your own product shots and keep the JSON structure.
