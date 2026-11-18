# AI image restoration implementation status

This document is the implementation ledger for the consolidated AI restoration work.

## Existing verified repository groundwork

- Optional model files are stored outside the APK by `RestorationModelStore`.
- Model downloads require HTTPS metadata, a bounded byte count, and a SHA-256 match before atomic installation.
- `RestorationExecutionPolicy` applies input/output pixel budgets and creates overlap-aware tile plans.
- `RealEsrganRgbTensorPreprocessor` converts ARGB input tiles to planar RGB float tensors.
- `RgbFloatTensorPostprocessor` converts explicitly described RGB float tensors to ARGB pixels.
- `RestorationTileStitcher` assembles validated tile cores.
- `RestorationEngineRunner` coordinates model presence, cancellation, progress, and output validation.
- A Python ONNX archive inspector and unit tests are present under `tools/`.

These are infrastructure components. They do not constitute an executable restoration pipeline.

## Admission status

| Milestone | Status | Evidence / remaining work |
|---|---|---|
| Identify upstream Real-ESRGAN candidate and license | Partially verified | Qualcomm's Hugging Face model card identifies Real-ESRGAN-x4plus and BSD-3-Clause metadata. Verify the exact weight artifact's own license and immutable revision before catalog admission. |
| Inspect the exact ONNX archive | Blocked | The published binary archive could not be retrieved in the current development environment. Tensor names, element types, dimensions, opset, graph operators, and SHA-256 must be read from the exact artifact. |
| Select Android inference runtime | Not started | Do not add a native runtime until graph operators and Android ABI/provider compatibility are known. Measure per-ABI APK impact. |
| Implement actual Real-ESRGAN inference | Not started | The current `RestorationEngine` is an interface; no concrete ONNX engine is wired. |
| Model download/install/update/remove | Infrastructure exists | Store is implemented, but there is no production catalog entry until artifact admission passes. |
| User-facing restoration flow | Not started | Add explicit image selection, before/after comparison, progress, cancellation, and errors without changing existing search/translation flows. |
| GFPGAN | Not started | Treat as a separate optional model and inference pipeline; verify its own license, export, runtime, and memory requirements. |
| Device performance and quality validation | Not started | Requires real model inference on a Pixel-class arm64 device. Qualcomm NPU figures are not evidence of Pixel CPU performance. |

## Hard requirements

1. Keep model weights out of the APK and download them only after an explicit user action.
2. Preserve existing screen selection, OCR, offline translation, and reverse-image-search features.
3. Keep restoration on-device. Never upload an image as part of restoration.
4. Keep reverse-image-search upload separate and user-initiated.
5. Enforce image/tile/memory limits, coroutine cancellation, and native resource cleanup.
6. Keep GFPGAN separate from Real-ESRGAN.
7. Do not publish a model URL or checksum until the exact artifact is inspected and its provenance is recorded.
8. Do not describe the feature as working until end-to-end inference and output validation pass.

## Release gate

The feature is ready for a release candidate only when the exact model artifact is inspected, a compatible Android backend is implemented, automated tests pass, the APK is built for supported ABIs, and on-device inference is measured. If device testing is unavailable in CI, report that explicitly and provide the APK for manual testing rather than claiming device validation.
