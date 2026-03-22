---
name: sheet-image-to-pages
description: Batch import sheet-music images into a UniApp project and generate local static page data files. Use when users provide scanned/shot sheet images and need one-command conversion into `static/scores` and `src/data/scores.json` for offline TV browsing.
---

# sheet-image-to-pages

Use this skill to turn local sheet image folders into front-end static assets and metadata.

## Workflow

1. Ensure source images are organized as one folder per score:
`sheet-source/<score-title>/*.jpg|png`
2. Run:
`node tools/generate-sheets.mjs --input sheet-source`
3. Confirm generated outputs:
- `static/scores/<score-id>/001.jpg...`
- `src/data/scores.json`
- `src/data/sheets/<score-id>.json`

## Behavior Rules

1. Keep original image quality by default (copy file directly, no re-compress).
2. Keep pages sorted by natural filename order (`1.jpg`, `2.jpg`, `10.jpg`).
3. Generate deterministic score IDs from folder names.
4. If a score folder has no images, skip it and log the reason.
5. Never call remote APIs or databases.

## Validation

1. Check `src/data/scores.json` has `id/title/cover/totalPages/pages`.
2. Open app list page and verify ordering.
3. Enter preview and verify every page can be turned by remote left/right.
