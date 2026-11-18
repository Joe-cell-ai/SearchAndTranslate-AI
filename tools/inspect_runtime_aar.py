#!/usr/bin/env python3
"""Produce an auditable operator and Android AAR capability inventory."""
from __future__ import annotations
import collections
import json
import pathlib
import sys
import zipfile

def main() -> int:
    import onnx
    model_path = pathlib.Path(sys.argv[1])
    aar_path = pathlib.Path(sys.argv[2])
    model = onnx.load(str(model_path), load_external_data=False)
    ops = collections.Counter((node.domain or "ai.onnx", node.op_type) for node in model.graph.node)
    operators = [
        {"domain": domain, "op_type": op, "count": count}
        for (domain, op), count in sorted(ops.items())
    ]
    with zipfile.ZipFile(aar_path) as aar:
        names = sorted(aar.namelist())
        native = []
        evidence = []
        for name in names:
            if name.startswith("jni/") and name.endswith(".so"):
                data = aar.read(name)
                native.append({"path": name, "bytes": len(data)})
                for token in (b"Xnnpack", b"XNNPACK", b"onnxruntime_providers_shared",
                              b"OrtSessionOptionsAppendExecutionProvider"):
                    if token in data:
                        evidence.append({"path": name, "marker": token.decode("ascii")})
        jars = [name for name in names if name.endswith(".jar")]
        java_classes = []
        for jar_name in jars:
            import io
            with zipfile.ZipFile(io.BytesIO(aar.read(jar_name))) as jar:
                java_classes.extend(n for n in jar.namelist()
                                    if n.endswith(".class") and
                                    any(k in n.lower() for k in ("onnxruntime", "environment", "session")))
        report = {
            "model": {
                "file": model_path.name,
                "ir_version": model.ir_version,
                "opsets": [{"domain": x.domain or "ai.onnx", "version": x.version}
                           for x in model.opset_import],
                "node_count": len(model.graph.node),
                "unique_operator_kinds": len(operators),
                "operators": operators,
            },
            "android_aar": {
                "file": aar_path.name,
                "bytes": aar_path.stat().st_size,
                "native_libraries": native,
                "native_bytes_total": sum(x["bytes"] for x in native),
                "xnnpack_binary_markers": evidence,
                "java_api_classes": sorted(java_classes),
                "aar_entries": names,
            },
            "interpretation": {
                "xnnpack_confirmed": bool(evidence),
                "warning": "Absence of a printable marker does not prove provider absence; confirm with runtime API/native registration smoke test."
            }
        }
    print(json.dumps(report, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
