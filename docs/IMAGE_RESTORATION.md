# On-device image restoration architecture

The Android app keeps restoration weights out of the APK. The app-side model store accepts
only HTTPS model metadata with a SHA-256 digest and a bounded maximum download size. It
downloads to a temporary file, supports coroutine cancellation, verifies the digest, and
only then exposes the completed model file.

## Runtime boundary

`RestorationEngine` is the inference boundary. A concrete backend must be added only
after a compatible Android model export and runtime have been validated on arm64 Android.
It must implement face restoration and/or image upscaling without changing the existing
OCR, translation, or image-search pipelines. It must also enforce bounded input dimensions,
tile large images, report progress, and release native resources on cancellation/failure.

## Model admission checklist

Before adding a model to the in-app catalog, verify and record:

- Upstream source and immutable model version.
- Model architecture/export format and supported input/output tensor shapes.
- License for both code and pretrained weights, with required notices.
- SHA-256 checksum and maximum download size.
- Android runtime compatibility and measured peak RAM on a mid-range device.
- A small test image and expected output dimensions.

No model URL or checksum is published in the catalog until those checks pass. The download
store is infrastructure only; it does not claim that a model is currently available or that
inference is implemented.

## Privacy

Restoration is designed for on-device processing. Reverse-image-search uploads remain a
separate, user-initiated action and must disclose the selected destination.
