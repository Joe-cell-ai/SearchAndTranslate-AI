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
tile large images using `RestorationExecutionPolicy`, report progress, and release native
resources on cancellation/failure. The runner does not yet provide a concrete inference backend or a user-facing restoration screen.

`RestorationExecutionPolicy` provides conservative input/output pixel budgets and a
tile planner. Tile plans contain a non-overlapping core and an expanded inference region
with overlap; a backend must crop inferred results to each core before stitching. The
planner is not itself an inference implementation and does not claim that a model can
currently run in the app.

## Real-ESRGAN x4plus model admission

The candidate model is Qualcomm's published Real-ESRGAN x4plus ONNX FLOAT32 export.
The checked-in admission contract expects a static NCHW tensor with 128x128 RGB input
and 512x512 RGB output, normalized to [0, 1]. The contract validator rejects mismatched
names, shapes and element types. This contract is a guardrail, not proof that a downloaded
archive has those exact metadata: the model ZIP still needs to be inspected and matched
against it before the model can be admitted to the app.

The Qualcomm w8a8 quantized assets are not treated as generic CPU models. Qualcomm's
sample documents that its w8a8 deployment path is HTP-specific. The Pixel 10a target
therefore needs a verified CPU execution provider unless a separate compatible backend
is demonstrated. No runtime dependency or model download catalog entry is added yet.

This batch adds a pure Kotlin RGB-to-NCHW tile preprocessor for the fixed 128px input.
It converts ARGB pixels to planar RGB float values in [0, 1] and replicates the nearest
edge pixel for partial tiles. It deliberately does not perform inference, output conversion,
or tile stitching. Those steps must be tested against the actual model archive before
enabling the feature.

The next output-stage utility converts an explicitly described single-image RGB float
tensor into an opaque ARGB pixel buffer. The caller must supply width, height, and NCHW
or NHWC layout; the utility validates exact buffer length, rejects NaN/infinity, and clamps
finite channel values to [0, 1]. It does not infer the ONNX graph's tensor layout or upscale
factor. It is standalone pure Kotlin groundwork, not an inference backend or tile stitcher.

## Android runtime evaluation

The initial runtime candidate is ONNX Runtime for Android. Its official mobile guidance
supports CPU by default and lists XNNPACK and NNAPI as Android execution providers.
A full ONNX Runtime Android package contains the full operator set; a reduced custom
package requires a model-specific operator configuration and build. The runtime is not
added to the app dependency graph until its APK-size impact and required operators are
measured against the actual graph.

Before adoption, verify the exact model archive and immutable revision, tensor names and
shapes, license/weight provenance, SHA-256 checksum, Android ABI support, native library
footprint, CPU latency, and measured peak memory. The first supported model should be
selected only after a Pixel-class arm64 device test. GFPGAN remains a separate model/backend
admission task.

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
