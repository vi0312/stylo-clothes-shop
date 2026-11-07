# Seed assets

`prisma/seed.ts` reads the committed catalog snapshot from `catalog/catalog.json`.
The seed never downloads anything: it copies the images below into
`$UPLOAD_DIR/products/` and inserts the snapshot rows.

## Source

- Dataset: [Fashion Product Images Dataset](https://www.kaggle.com/datasets/paramaggarwal/fashion-product-images-dataset)
  (Kaggle, `paramaggarwal`), read through the
  [Hugging Face mirror](https://huggingface.co/datasets/mecha2019/fashion-product-images-small).
- Selection: 100 products from 4,000 sampled rows of 42,426, Apparel and Footwear
  only, Men/Women/Unisex only, mapped to the Stylo categories (`Áo`, `Quần`,
  `Áo khoác`, `Giày`) and brands (`Stylo`, `Local Brand`, `Urban Viet`, `Basic Lab`).
- Vietnamese names and descriptions are translated and normalized by the generator;
  source product names are kept in `sourceName` for provenance.
- Real source facts: product type, base colour, gender, usage, image.
- Demo values: price, sizes, stock, orders, receipts, payments. The dataset has
  no price or inventory data.

## Layout

| Path | Contents |
|---|---|
| `catalog/catalog.json` | 100 products, 304 variants, source metadata |
| `catalog/images/p<id>.jpg` | 600x800 JPEG, quality 82, about 3.5 MB total |
| `../scripts/build-catalog-seed.py` | one-off generator, not used at seed time |

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
deterministically. Run it only when changing the selection or normalization rules; afterwards
reset the database with `npm run db:reset`.

## License

The Hugging Face mirror declares **no license**, and the images originate from
Myntra. Treat this snapshot as coursework/demo data only, do not redistribute it
commercially. For a public release, replace these images with licensed stock photos
or your own product shots and keep the JSON structure.
