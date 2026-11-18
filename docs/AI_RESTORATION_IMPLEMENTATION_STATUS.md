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
| Identify upstream Real-ESRGAN candidate and license | Source/provenance verified; app admission pending | A newer SkillSafe export identifies the upstream Xintao Real-ESRGAN-x4plus checkpoint, pinned source revision, reproducible conversion recipe, BSD-3-Clause attribution, and immutable model revision `107475e49b7d46412cc966481efccc3500955801`. The publisher reports a 63.95 MB FP32 ONNX artifact and SHA-256 `4851ec156207d271f5328605d0582eeb851e656227da8aca093ced9e60789291`. This is a stronger candidate than the earlier Qualcomm export for generic CPU evaluation, but publisher metadata is not independent byte verification. Do not publish it in the in-app download catalog until the actual bytes are downloaded and hashed in CI or a trusted build environment. |
| Inspect the exact ONNX artifact | Contract documented by publisher; independent binary inspection pending | The SkillSafe model card reports input `input`: float32 NCHW `[batch,3,height,width]`, output `output`: float32 `[batch,3,height_x4,width_x4]`, and opset 17. Its model page reports CPU parity against the PyTorch reference (about 128 dB PSNR for FP32) and immutable file hash above. The container could not resolve external DNS in this session, so the model binary was not downloaded, independently SHA-256 checked, or passed through `tools/inspect_realesrgan_onnx.py`. Treat tensor metadata and hash as publisher-asserted until that gate passes. |
| Select Android inference runtime | Candidate selected for prototype; package decision pending | Official ONNX Runtime mobile guidance supports Android CPU by default and XNNPACK/NNAPI providers, and recommends measuring binary size, latency, power, and memory. The full Android package is simplest but adds material native size; a custom reduced-operator package can be smaller only after the exact graph operator config is generated and validated. Start with an arm64 CPU/XNNPACK prototype, keep runtime optional/separate from model weights, and measure actual APK impact before choosing a production package. Do not assume NNAPI acceleration or Snapdragon QNN on the user's Pixel-class device. |
| Implement actual Real-ESRGAN inference | Not started | The current `RestorationEngine` is an interface; no concrete ONNX engine is wired. |
| Model download/install/update/remove | Partially implemented | Added `SafRestorationModelStore`: persisted user-selected SAF folder, bounded HTTPS download, streaming SHA-256 verification, URI index, delete, and temporary cache staging for inference. The current development batch adds app-indexed installed-model inventory, size/access status, and delete controls. Still needs Android build/provider testing, folder-access recovery UX, and verification across document providers. The legacy app-private store remains for compatibility; production UI must use SAF. |
| User-facing restoration flow | Partially implemented | Home entry, SAF model management, local image selection, downsampled original preview, and task-mode selection are implemented. The restore action remains explicitly non-operational until a verified inference engine and model catalog are available. Before/after comparison, real progress, cancellation, and runtime error handling remain outstanding. |
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


## User-selected model storage (required UX)

All downloadable model families, including Real-ESRGAN and GFPGAN, must use the same storage manager and Android Storage Access Framework (SAF) flow:

1. When the user taps **Download** for a model, launch the system folder picker (`ACTION_OPEN_DOCUMENT_TREE`). Do not ask for broad storage permission.
2. Persist the granted tree URI permission so the app can continue using that folder after process restarts.
3. Download into a temporary document in the selected folder, enforce the model's maximum size while streaming, verify the SHA-256, then rename/move it to its final model filename. A failed or cancelled download must not replace an existing verified model.
4. Keep a small app-private index containing model ID, display name, task, version, checksum, document URI, and selected tree URI. The index is metadata only; model bytes remain in the user-selected folder.
5. On app start and before inference, confirm the document still exists and matches the expected model metadata. If the user moved/deleted the folder or revoked access, ask them to select the model folder again; never silently download another copy.
6. **Manage models** must list installed models and their storage sizes, allow the user to delete an individual model, and allow changing the model folder. Deleting a model removes its document from the selected folder and its index entry.
7. If an inference runtime requires a filesystem path rather than a SAF URI, stage the selected model into app cache only for the active inference session, then delete the temporary copy when the runtime session closes or fails. Do not keep a second permanent private copy.
8. If the user selects a folder that cannot be written, show a clear error and let them choose another folder.

The system folder picker is intentionally shown at model-download time, not on every app launch. Persisted SAF permission is used for subsequent discovery, inference, and deletion. The app must never scan unrelated folders or access files outside the folder the user selected.

This storage design is shared by every model family and is a release requirement. It keeps model weights out of the APK and lets users reclaim storage by removing models they no longer need.

## Latest implementation commits\n\n- `65eb75b`: add SAF-backed optional model storage with persisted folder permission, bounded downloads, checksum validation, model URI metadata, deletion, and temporary cache staging.\n- `f94eadf`: allow the inference runner to use SAF models and scope the staged file to the inference call.\n- `21fb81a`: create model documents under the selected tree's root document URI.\n\nThese changes have not yet passed Android CI and are not device-validated. The SAF model store is infrastructure only; no folder-picker UI or production inference engine is wired yet.\n\n## Release gate

The feature is ready for a release candidate only when the exact model artifact is inspected, a compatible Android backend is implemented, automated tests pass, the APK is built for supported ABIs, and on-device inference is measured. If device testing is unavailable in CI, report that explicitly and provide the APK for manual testing rather than claiming device validation.


## Latest development batch (PR #12)

- Adds a SAF-index-backed inventory API that lists only model records registered by this app; it does not scan unrelated user folders.
- Displays registered model name, task, stored size, and whether the selected folder/model document remains accessible.
- Adds per-model deletion from the Manage Models screen and refreshes the inventory after folder changes or deletion.
- Does not add model binaries, URLs, checksums, or an inference dependency. Downloads remain disabled pending independent artifact and runtime verification.
- Android CI and document-provider behavior must pass before this batch is considered verified.


## Latest development batch (PR #13)

- Adds Android image selection through the system document picker for image files.
- Decodes a downsampled local preview, rejecting invalid images and files above a conservative 100-megapixel source limit; preview decoding is capped at approximately 8 megapixels.
- Adds an explicit mode selector for image upscaling versus face restoration.
- Makes the local-only privacy behavior visible in the UI.
- Does not upload the selected image and does not fabricate a restored output. The restore action clearly reports that inference is unavailable until the verified engine/model pipeline is connected.
- Android CI and manual picker/large-image validation remain required.


## Development batch: PR #14 research and admission decision

- Verified the PR #13 merged commit's GitHub Actions run: unit tests, debug APK assembly, and artifact upload all completed successfully. Debug APK artifact: `searchandtranslate-debug-apks`, 158,072,291 bytes as a ZIP artifact; artifact digest `sha256:76df222be143b884e1348150d46f92654f2f5817b9f31085c075e3ef77b4b84e`. This is a CI artifact, not a device-tested release.
- Replaced the earlier Qualcomm-only model candidate with a documented generic Real-ESRGAN x4plus FP32 candidate from SkillSafe, pinned at `107475e49b7d46412cc966481efccc3500955801`; published artifact size 63.95 MB and SHA-256 `4851ec156207d271f5328605d0582eeb851e656227da8aca093ced9e60789291`.
- Publisher-documented graph contract: `input` float32 NCHW [batch,3,height,width] to `output` float32 [batch,3,height_x4,width_x4], opset 17. Publisher also reports FP32 CPU parity around 128 dB PSNR against its PyTorch reference.
- The model binary could not be fetched from this execution environment because outbound DNS resolution failed. Therefore its bytes and graph are not independently verified, and the candidate is not yet admitted to the app catalog. The old Qualcomm candidate remains unapproved too.
- Runtime decision: prototype ONNX Runtime Android CPU/XNNPACK first after artifact inspection. Keep model weights outside the APK and use the existing SAF folder picker and per-session cache staging. Measure runtime APK impact; do not assume NNAPI/QNN acceleration.
- GFPGAN remains a separate model/backend. Its export, exact graph, weight license notices, runtime compatibility, and face-cropping/alignment pipeline still require independent validation.
- No inference dependency, model URL, or unverified checksum was added to the app. This is deliberate: shipping a guessed contract would risk a non-working app and APK bloat.


## Development batch 1: CI-gated exact model verification

- Added a CI step that downloads the immutable FP32 ONNX artifact from the publisher's content-addressed URL.
- CI recomputes SHA-256 and fails on any mismatch before inspection.
- CI installs the optional Python ONNX parser, runs the repository's archive/model inspector, and asserts one float32 NCHW RGB input named `input`, one float32 RGB output named `output`, rank-four tensors, and opset 17.
- The downloaded model remains in the ephemeral GitHub runner temp directory; it is not committed, cached as an app asset, or packaged into either APK.
- This is an independent verification attempt performed by GitHub-hosted CI. A successful run is required before the candidate can be admitted to the app catalog.
- Android inference runtime integration remains blocked until the exact graph/operator inventory and CI verification results are available.
