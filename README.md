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

CI builds the signed APK on every push to `main` via `.github/workflows/generate-paper.yml` (JDK 17, Android SDK 34).

## Usage
- Tap any ruled line to type directly on it (multi-line supported).
- Use **Clear line** / **Clear all** from the bottom bar.
- Pinch to zoom, drag to pan, double-tap to toggle fit/full size.
- Tap **Save to Gallery** to export the current page as a high-resolution PNG.

## Modules
- `android/library` — pure static paper generator (`Canvas`/`Bitmap`), no Android UI dependencies.
- `android/app` — the notebook UI: zoomable paper view, inline line editor, theme support, persistence.
