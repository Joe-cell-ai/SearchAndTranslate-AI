#!/usr/bin/env python3
"""Inspect a Real-ESRGAN ONNX file or ZIP without extracting archive members.

Install the optional inspection dependency with:
    python -m pip install onnx

This is developer tooling only; it is not imported by the Android app.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path
from typing import Any

MAX_ARCHIVE_BYTES = 200 * 1024 * 1024
MAX_MODEL_BYTES = 160 * 1024 * 1024


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def dimension_value(dim: Any) -> int | str | None:
    if dim.HasField("dim_value"):
        return int(dim.dim_value)
    if dim.HasField("dim_param"):
        return str(dim.dim_param)
    return None


def value_info(value: Any) -> dict[str, Any]:
    tensor = value.type.tensor_type
    return {
        "name": value.name,
        "element_type": int(tensor.elem_type),
        "shape": [dimension_value(dim) for dim in tensor.shape.dim],
    }


def inspect_model_bytes(model_bytes: bytes) -> dict[str, Any]:
    try:
        import onnx
    except ImportError as exc:
        raise RuntimeError("Install optional dependency: python -m pip install onnx") from exc

    model = onnx.load_model_from_string(model_bytes)
    initializer_names = {item.name for item in model.graph.initializer}
    inputs = [value_info(item) for item in model.graph.input
              if item.name not in initializer_names]
    outputs = [value_info(item) for item in model.graph.output]
    return {
        "ir_version": int(model.ir_version),
        "opsets": [{"domain": item.domain, "version": int(item.version)}
                   for item in model.opset_import],
        "inputs": inputs,
        "outputs": outputs,
        "node_count": len(model.graph.node),
    }


def read_model(path: Path) -> tuple[bytes, str]:
    if path.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError(f"Input exceeds {MAX_ARCHIVE_BYTES} byte safety limit")
    raw = path.read_bytes()
    if raw[:4] == b"\x08\x00\x12\x00" or path.suffix.lower() == ".onnx":
        if len(raw) > MAX_MODEL_BYTES:
            raise ValueError("ONNX model exceeds safety limit")
        return raw, path.name

    with zipfile.ZipFile(path) as archive:
        candidates = [item for item in archive.infolist()
                      if not item.is_dir() and item.filename.lower().endswith(".onnx")]
        if len(candidates) != 1:
            raise ValueError(f"Expected exactly one .onnx file; found {len(candidates)}")
        item = candidates[0]
        if item.file_size > MAX_MODEL_BYTES:
            raise ValueError("ONNX member exceeds safety limit")
        if item.file_size > max(1, item.compress_size) * 200:
            raise ValueError("ONNX member compression ratio exceeds safety limit")
        with archive.open(item, "r") as stream:
            model_bytes = stream.read(MAX_MODEL_BYTES + 1)
        if len(model_bytes) > MAX_MODEL_BYTES:
            raise ValueError("ONNX member exceeds safety limit")
        return model_bytes, item.filename


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path, help="ONNX file or ZIP containing one ONNX file")
    args = parser.parse_args()
    try:
        model_bytes, member_name = read_model(args.model)
        report = inspect_model_bytes(model_bytes)
        report.update({
            "source_file": args.model.name,
            "model_member": member_name,
            "model_bytes": len(model_bytes),
            "model_sha256": sha256(model_bytes),
            "container_sha256": sha256(args.model.read_bytes()),
        })
        print(json.dumps(report, indent=2))
        return 0
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as exc:
        print(f"inspection failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
