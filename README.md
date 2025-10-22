# mh-cutout

`mh-cutout` is a standalone JVM module for background removal that exposes a stable API and a headless CLI utility. The project is organised as a Gradle multi-module build:

- **cutout-core** – public data types and the `RemoveBgEngine` contract.
- **cutout-engine-heur** – heuristic engine that estimates the background by colour similarity.
- **cutout-cli** – command line tool for batch processing and manual testing.

The initial release (`v0.1.x`) provides a deterministic heuristic pipeline that operates on ARGB 8-bit images without any ML dependencies.

## Building

This repository uses Gradle with Kotlin DSL. To run the complete verification suite locally:

```bash
./gradlew clean check
```

All modules target JVM 17 and publish Maven artefacts under the `io.inneren.mh` group. Publishing is handled by the `publish` GitHub Actions workflow when a semantic version tag (e.g. `v0.1.0`) is pushed.

## Core API

The public API lives in the `cutout-core` module and stays binary-compatible within the `v0.x` series.

```kotlin
val image = ImageBuffer(width = w, height = h, argb = pixels)
val engine: RemoveBgEngine = HeurRemoveBgEngine()
val result: MatteResult = engine.removeBackground(image, CutoutConfig())
```

- `ImageBuffer` always stores ARGB pixels in row-major order.
- `AlphaMask` keeps one byte per pixel (`width * height`).
- `MatteResult.preview` contains the original colours with updated alpha (when available).

## CLI usage

The `cutout-cli` module exposes a headless CLI that reads PNG or JPEG input, respects EXIF orientation and writes an RGBA PNG with the computed alpha channel.

```bash
./gradlew :cutout-cli:run --args "input.jpg output.png --mode object --soft 0.25"
```

Usage reference:

```
cutout-cli <in.(png|jpg)> <out.png> [--mode auto|portrait|object] [--soft 0..1] [--hard 0..255]
```

Exit code `0` indicates success; any non-zero code signals an error with a human-friendly message on `stderr`.

## Heuristic engine overview

The heuristic engine estimates the background colour from the border pixels, computes RGB distances, converts them into alpha using a logistic curve (or a hard threshold), applies a lightweight 3×3 smoothing pass and produces an optional preview with the updated alpha channel. Input images above 50 MP are rejected with a descriptive error to prevent memory issues.

## Testing strategy

Unit tests cover validation logic in the core module, heuristic behaviour (foreground detection, hard thresholding and bounds checks), as well as CLI argument parsing and end-to-end execution using a temporary image.

Run tests with:

```bash
./gradlew test
```
