# On-device image restoration architecture

The Android app keeps restoration weights out of the APK. The app-side model store accepts
only HTTPS model metadata with a SHA-256 digest and a bounded maximum download size. It
downloads to a temporary file, supports coroutine cancellation, verifies the digest, and
only then exposes the completed model file.

## Runtime boundary

`RestorationEngine` is the inference boundary and receives the verified model file explicitly. `RestorationEngineRunner` checks model metadata, requires an installed model, applies input/output pixel budgets, preserves coroutine cancellation, clamps progress to a monotonic 0–99% until output validation, and rejects an output bitmap that aliases the input. A concrete backend must be added only
after a compatible Android model export and runtime have been validated on arm64 Android.
It must implement face restoration and/or image upscaling without changing the existing
OCR, translation, or image-search pipelines. It must also enforce bounded input dimensions,
tile large images using `RestorationExecutionPolicy`, report progress, and release native resources on cancellation/failure. The runner does not yet provide a concrete inference backend or a user-facing restoration screen.

`RestorationExecutionPolicy` provides conservative input/output pixel budgets and a
tile planner. Tile plans contain a non-overlapping core and an expanded inference region
with overlap; a backend must crop inferred results to each core before stitching. The
planner is not itself an inference implementation and does not claim that a model can
currently run in the app.

## Real-ESRGAN x4plus model admission

The first candidate is Qualcomm's published Real-ESRGAN x4plus ONNX export. The current
admission contract is deliberately limited to the FLOAT32 CPU-compatible graph:
`input` / `output`, static NCHW tensors, input [1, 3, 128, 128], output
[1, 3, 512, 512], RGB values normalized to [0, 1]. The app rejects any export that does
not match this contract rather than guessing tensor names, layout, precision, or scale.

The Qualcomm w8a8 quantized assets are not treated as generic CPU models: Qualcomm's
sample implementation documents that its w8a8 path requires the HTP runtime. The Pixel
10a target therefore needs the float graph and a verified CPU execution provider unless
a separate compatible backend is demonstrated. No runtime dependency or model download
catalog entry is added in this batch.

The fixed 128px graph requires an image tiling/preprocessing adapter that preserves aspect
ratio and correctly removes padding after x4 inference. The existing generic 256px
restoration tile planner must not be passed directly to this model. The adapter, tensor
contract inspection against the actual downloaded ONNX file, image-quality tests, CPU
latency, peak RAM and APK-size impact remain admission gates.

## Android runtime evaluation

The initial runtime candidate is ONNX Runtime for Android because an Android AAR and
official Android deployment documentation are available. A full ONNX Runtime AAR may
increase APK size materially, so it is not added to the app dependency graph yet. A
reduced ONNX Runtime Mobile package requires ORT-format models and a matching reduced
operator configuration; it is not a drop-in replacement for arbitrary ONNX exports.

Before adoption, verify the exact model archive and immutable revision, license/weight
provenance, SHA-256 checksum, Android ABI support, native library footprint, and measured
peak memory. The first supported model should be selected only after a Pixel-class arm64
device test. GFPGAN remains a separate model/backend admission task.

## Model admission checklist

Before adding a model to the in-app catalog, verify and record:

- Upstream source and immutable model version.
- Model architecture/export format and supported input/output tensor shapes.
- License for both code and pretrained weights, with required notices.
- SHA-256 checksum and maximum download size.
- Android runtime compatibility and measured peak RAM on a mid-range device.
- A small test image and expected output dimensions.

No model URL or checksum is published in the catalog until those checks pass. The download
store is infrastructure only; it does not claim that a model is currently available or
that inference is implemented.

## Privacy

Restoration is designed for on-device processing. Reverse-image-search uploads remain a
separate, user-initiated action and must disclose the selected destination.
