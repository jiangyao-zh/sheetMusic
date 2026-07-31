#!/usr/bin/env python3
"""
乐谱图片预处理：为 Audiveris OMR 生成优化输入图。

推荐流程（经 music_1 验证）：
  1. 本脚本生成 *_omr.png
  2. Audiveris -batch -save  转写
  3. Audiveris -batch -export 导出 MusicXML

注意：
  - Audiveris 会自行二值化，请勿对图片做激进二值化或过度去铅笔，
    否则会破坏五线谱导致 "No system found"。
  - 本脚本仅做：放大 + 背景归一化 + 对比度拉伸。

依赖：
  pip install -r scripts/requirements-omr.txt

示例：
  python3 scripts/prepare_sheet_for_omr.py assets/music_1_original.png
  python3 scripts/prepare_sheet_for_omr.py input.jpg -o output.png --scale 3
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

# 经 music_1 验证的默认参数
DEFAULT_SCALE = 3.0
BG_BLUR_SIGMA = 25.0
CONTRAST_LO_PERCENTILE = 2.0
CONTRAST_HI_PERCENTILE = 98.0


@dataclass(frozen=True)
class PrepareOptions:
  """OMR 预处理参数。"""

  scale: float = DEFAULT_SCALE
  bg_blur_sigma: float = BG_BLUR_SIGMA
  contrast_lo: float = CONTRAST_LO_PERCENTILE
  contrast_hi: float = CONTRAST_HI_PERCENTILE


def upscale(gray: np.ndarray, scale: float) -> np.ndarray:
  if scale == 1.0:
    return gray
  return cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)


def normalize_background(gray: np.ndarray, sigma: float) -> np.ndarray:
  blur = cv2.GaussianBlur(gray, (0, 0), sigmaX=sigma)
  return cv2.divide(gray, blur, scale=255)


def stretch_contrast(
    gray: np.ndarray,
    lo_percentile: float,
    hi_percentile: float,
) -> np.ndarray:
  p_lo, p_hi = np.percentile(gray, [lo_percentile, hi_percentile])
  out = (gray.astype(np.float32) - p_lo) / (p_hi - p_lo + 1e-6) * 255.0
  return np.clip(out, 0, 255).astype(np.uint8)


def prepare_sheet_for_omr(
    image: np.ndarray,
    options: PrepareOptions | None = None,
) -> np.ndarray:
  """
  将灰度乐谱图预处理为 Audiveris 友好格式。

  处理步骤：放大 → 背景归一化 → 对比度拉伸（保持灰度，不二值化）。
  """
  opts = options or PrepareOptions()
  gray = upscale(image, opts.scale)
  gray = normalize_background(gray, opts.bg_blur_sigma)
  return stretch_contrast(gray, opts.contrast_lo, opts.contrast_hi)


def default_output_path(input_path: Path, output_dir: Path | None = None) -> Path:
  parent = output_dir if output_dir else input_path.parent
  return parent / f"{input_path.stem}_omr.png"


def load_grayscale(path: Path) -> np.ndarray:
  image = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE)
  if image is None:
    raise FileNotFoundError(f"无法读取图片: {path}")
  return image


def save_image(path: Path, image: np.ndarray) -> None:
  path.parent.mkdir(parents=True, exist_ok=True)
  if not cv2.imwrite(str(path), image):
    raise OSError(f"无法写入图片: {path}")


def process_file(
    input_path: Path,
    output_path: Path | None = None,
    options: PrepareOptions | None = None,
) -> Path:
  """读取输入图、预处理并保存，返回输出路径。"""
  opts = options or PrepareOptions()
  src = input_path.expanduser().resolve()
  dst = (output_path or default_output_path(src)).expanduser().resolve()

  gray = load_grayscale(src)
  result = prepare_sheet_for_omr(gray, opts)
  save_image(dst, result)
  return dst


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description="为乐谱 OMR（Audiveris）预处理扫描/拍照图片",
    formatter_class=argparse.RawDescriptionHelpFormatter,
    epilog=(
      "Audiveris 示例:\n"
      "  ./Audiveris -batch -save  -output /opt/data/result  input_omr.png\n"
      "  ./Audiveris -batch -export -output /opt/data/result  input_omr.png\n"
    ),
  )
  parser.add_argument("input", type=Path, help="输入乐谱图片（jpg/png 等）")
  parser.add_argument(
    "-o", "--output",
    type=Path,
    default=None,
    help="输出路径（默认：<输入名>_omr.png）",
  )
  parser.add_argument(
    "--scale",
    type=float,
    default=DEFAULT_SCALE,
    help=f"放大倍数（默认 {DEFAULT_SCALE}）",
  )
  parser.add_argument(
    "--bg-sigma",
    type=float,
    default=BG_BLUR_SIGMA,
    help=f"背景归一化模糊系数（默认 {BG_BLUR_SIGMA}）",
  )
  parser.add_argument(
    "--contrast-lo",
    type=float,
    default=CONTRAST_LO_PERCENTILE,
    help=f"对比度拉伸下百分位（默认 {CONTRAST_LO_PERCENTILE}）",
  )
  parser.add_argument(
    "--contrast-hi",
    type=float,
    default=CONTRAST_HI_PERCENTILE,
    help=f"对比度拉伸上百分位（默认 {CONTRAST_HI_PERCENTILE}）",
  )
  return parser


def main(argv: list[str] | None = None) -> int:
  args = build_parser().parse_args(argv)
  opts = PrepareOptions(
    scale=args.scale,
    bg_blur_sigma=args.bg_sigma,
    contrast_lo=args.contrast_lo,
    contrast_hi=args.contrast_hi,
  )

  try:
    out = process_file(args.input, args.output, opts)
  except (FileNotFoundError, OSError) as exc:
    print(f"错误: {exc}", file=sys.stderr)
    return 1

  image = cv2.imread(str(out), cv2.IMREAD_GRAYSCALE)
  shape = image.shape if image is not None else "unknown"
  print(f"已生成: {out}")
  print(f"尺寸: {shape}")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
