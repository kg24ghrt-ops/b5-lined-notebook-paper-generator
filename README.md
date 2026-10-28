# B5 Lined Notebook Paper Generator

An Android app that generates realistic B5 lined notebook paper and lets you type on it, with full Myanmar script support (bundled font + grapheme-aware wrapping).

## Specifications
- Paper size: B5 (176mm × 250mm)
- Working resolution: 150 DPI for fluid editing; 300 DPI on save
- Output: PNG to the gallery (scoped storage, no storage permission needed)
- Features:
  - Light blue ruled lines (8mm spacing) with subtle ink bleed
  - Red left margin line (20mm)
  - Ring binder holes ("buckles")
  - Paper fiber texture and vignette
  - Myanmar-aware text wrapping (grapheme-cluster boundaries; never splits a stack)
  - Light and dark themes
  - Notes persist across process death

## Android
Build and install the debug APK:

```bash
cd android && ./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Continuous integration

CI compiles `:library` and `:app` on every push to `main` via
`.github/workflows/compile-check.yml` (JDK 17, Android SDK 34, Gradle 8.5).
A green Compile Check is required before changes are considered merged; CI
proves compilation only, not device behavior.

## Checking the build locally

The project includes `tools/check_build.py`, which performs a real Gradle
compilation of `:library` and `:app` inside a throwaway scratch folder (so it
never touches your normal `~/.gradle` or Android SDK):

```bash
python tools/check_build.py
```

By default it compiles `:library:compileDebugJavaWithJavac` and
`:app:compileDebugJavaWithJavac`. Add `--lint` to also run
`:app:lintDebug`.

The first run needs the Android SDK licenses accepted before Gradle can
download platform pieces:

```bash
python tools/check_build.py --accept-licenses
```

This writes the standard SDK license acceptance files into the scratch folder
(default `~/.cache/notebookpaper-build`). The scratch folder can be removed at
any time with `python tools/check_build.py --clean`.

See `python tools/check_build.py --help` for all options.

## Usage
- Tap any ruled line to type directly on it (multi-line supported).
- Use **Clear line** / **Clear all** from the overflow menu.
- Pinch to zoom, drag to pan, double-tap to toggle fit/full size.
- Tap **Save to Gallery** to export the current page as a high-resolution PNG.

## Modules
- `android/library` — pure static paper generator (`Canvas`/`Bitmap`), no Android UI dependencies.
- `android/app` — the notebook UI: zoomable paper view, inline line editor, theme support, persistence.
