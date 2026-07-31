#!/usr/bin/env python3
"""
MusicXML 解析：使用 music21 读取 .mxl / .xml 并导出结构化 JSON 与文本摘要。

依赖：
  pip install -r scripts/requirements-music21.txt

示例：
  python3 scripts/parse_musicxml.py assets/music_1_omr.mxl
  python3 scripts/parse_musicxml.py input.mxl -o assets/output
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from music21 import chord, converter, note, stream, tempo


@dataclass(frozen=True)
class ParseOutputPaths:
  """解析产物路径。"""

  json_path: Path
  summary_path: Path


def default_output_paths(input_path: Path, output_dir: Path | None = None) -> ParseOutputPaths:
  parent = output_dir if output_dir else input_path.parent
  stem = input_path.stem
  return ParseOutputPaths(
    json_path=parent / f"{stem}_parsed.json",
    summary_path=parent / f"{stem}_summary.txt",
  )


def _serialize_note_or_rest(element: Any, measure_offset: float) -> dict[str, Any]:
  base: dict[str, Any] = {
    "offset": round(measure_offset + float(element.offset), 4),
    "duration_quarters": float(element.quarterLength),
    "duration_type": element.duration.type,
  }

  if isinstance(element, note.Note):
    base.update({
      "kind": "note",
      "pitch": element.nameWithOctave,
      "midi": element.pitch.midi,
    })
  elif isinstance(element, chord.Chord):
    base.update({
      "kind": "chord",
      "pitches": [p.nameWithOctave for p in element.pitches],
      "midi": [p.midi for p in element.pitches],
    })
  elif isinstance(element, note.Rest):
    base.update({
      "kind": "rest",
      "pitch": None,
    })
  else:
    base["kind"] = type(element).__name__

  return base


def _format_time_signature(ts: Any) -> str:
  return getattr(ts, "ratioString", str(ts))


def _format_key_signature(ks: Any) -> str:
  return getattr(ks, "tonicPitchNameWithCase", str(ks))


def _format_tempo(mm: tempo.MetronomeMark) -> str:
  if mm.number is not None and mm.referent:
    return f"{mm.number} {mm.referent.fullName}"
  return str(mm)


def _format_clef(clef: Any) -> str:
  return getattr(clef, "sign", str(clef))


def _collect_signatures(part: stream.Part) -> dict[str, list[str]]:
  flat = part.flatten()
  return {
    "time_signatures": [
      _format_time_signature(ts)
      for ts in flat.getElementsByClass("TimeSignature")
    ],
    "key_signatures": [
      _format_key_signature(ks)
      for ks in flat.getElementsByClass("KeySignature")
    ],
    "tempos": [
      _format_tempo(mm)
      for mm in flat.getElementsByClass(tempo.MetronomeMark)
    ],
    "clefs": [_format_clef(c) for c in flat.getElementsByClass("Clef")],
  }


def _serialize_part(part: stream.Part, part_index: int) -> dict[str, Any]:
  signatures = _collect_signatures(part)
  measures: list[dict[str, Any]] = []

  for measure in part.getElementsByClass("Measure"):
    events = [
      _serialize_note_or_rest(element, float(measure.offset))
      for element in measure.notesAndRests
    ]
    measures.append({
      "number": measure.number,
      "offset_quarters": float(measure.offset),
      "duration_quarters": float(measure.duration.quarterLength),
      "events": events,
    })

  return {
    "index": part_index,
    "id": part.id,
    "name": part.partName or part.id,
    "measure_count": len(measures),
    "signatures": signatures,
    "measures": measures,
  }


def parse_musicxml(input_path: Path) -> dict[str, Any]:
  """解析 MusicXML，返回可 JSON 序列化的结构。"""
  score = converter.parse(str(input_path))
  metadata = score.metadata

  parts = [_serialize_part(part, index) for index, part in enumerate(score.parts)]
  all_measures = max((part["measure_count"] for part in parts), default=0)

  return {
    "source": input_path.name,
    "metadata": {
      "title": metadata.title,
      "composer": metadata.composer,
      "movement_name": metadata.movementName,
    },
    "score": {
      "part_count": len(parts),
      "measure_count": all_measures,
      "duration_quarters": float(score.duration.quarterLength),
    },
    "parts": parts,
  }


def build_summary(data: dict[str, Any]) -> str:
  """生成人类可读的文本摘要。"""
  lines: list[str] = []
  meta = data["metadata"]
  score_info = data["score"]

  title = meta.get("title") or "(无标题)"
  lines.append(f"源文件: {data['source']}")
  lines.append(f"标题: {title}")
  if meta.get("composer"):
    lines.append(f"作曲: {meta['composer']}")
  lines.append(
    f"声部: {score_info['part_count']}，小节: {score_info['measure_count']}，"
    f"总时长: {score_info['duration_quarters']} 四分音符"
  )
  lines.append("")

  for part in data["parts"]:
    lines.append(f"[{part['index']}] {part['name']}")
    sig = part["signatures"]
    if sig["time_signatures"]:
      lines.append(f"  拍号: {', '.join(sig['time_signatures'])}")
    if sig["key_signatures"]:
      lines.append(f"  调号: {', '.join(sig['key_signatures'])}")
    if sig["tempos"]:
      lines.append(f"  速度: {', '.join(sig['tempos'])}")

    preview_measures = part["measures"][:3]
    for measure in preview_measures:
      event_text = ", ".join(
        _format_event_preview(event)
        for event in measure["events"][:8]
      )
      suffix = " ..." if len(measure["events"]) > 8 else ""
      lines.append(f"  小节 {measure['number']}: {event_text}{suffix}")

    if len(part["measures"]) > len(preview_measures):
      lines.append(f"  ... 共 {part['measure_count']} 小节")
    lines.append("")

  return "\n".join(lines).rstrip() + "\n"


def _format_event_preview(event: dict[str, Any]) -> str:
  kind = event.get("kind", "?")
  duration = event.get("duration_type", "?")
  if kind == "note":
    return f"{event['pitch']}({duration})"
  if kind == "chord":
    return f"[{event['pitches'][0]}...]({duration})"
  if kind == "rest":
    return f"rest({duration})"
  return f"{kind}({duration})"


def write_outputs(data: dict[str, Any], paths: ParseOutputPaths) -> ParseOutputPaths:
  paths.json_path.parent.mkdir(parents=True, exist_ok=True)
  paths.summary_path.parent.mkdir(parents=True, exist_ok=True)

  with paths.json_path.open("w", encoding="utf-8") as fp:
    json.dump(data, fp, ensure_ascii=False, indent=2)
    fp.write("\n")

  summary = build_summary(data)
  paths.summary_path.write_text(summary, encoding="utf-8")
  return paths


def process_file(
    input_path: Path,
    output_dir: Path | None = None,
) -> ParseOutputPaths:
  """解析输入文件并写入 assets（或指定目录）。"""
  src = input_path.expanduser().resolve()
  if not src.is_file():
    raise FileNotFoundError(f"找不到 MusicXML 文件: {src}")

  suffix = src.suffix.lower()
  if suffix not in {".mxl", ".xml", ".musicxml"}:
    raise ValueError(f"不支持的文件类型: {suffix}（请使用 .mxl / .xml / .musicxml）")

  out_dir = (output_dir or src.parent).expanduser().resolve()
  paths = default_output_paths(src, out_dir)
  data = parse_musicxml(src)
  return write_outputs(data, paths)


def build_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description="使用 music21 解析 MusicXML（.mxl / .xml）并导出 JSON 与摘要",
  )
  parser.add_argument("input", type=Path, help="输入 MusicXML 文件")
  parser.add_argument(
    "-o", "--output-dir",
    type=Path,
    default=None,
    help="输出目录（默认与输入文件同目录）",
  )
  return parser


def main(argv: list[str] | None = None) -> int:
  args = build_parser().parse_args(argv)

  try:
    paths = process_file(args.input, args.output_dir)
  except (FileNotFoundError, ValueError, OSError) as exc:
    print(f"错误: {exc}", file=sys.stderr)
    return 1

  print(f"已生成 JSON: {paths.json_path}")
  print(f"已生成摘要: {paths.summary_path}")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
