# B5 Lined Notebook Paper Generator

A Java program that generates realistic B5 lined notebook paper as a high-resolution PNG image.

## Specifications
- Paper size: B5 (176mm × 250mm)
- Resolution: 300 DPI
- Format: PNG
- Features:
  - Light blue ruled lines (8mm spacing) with ink bleed
  - Red left margin line (20mm)
  - Subtle ring binder holes
  - Paper fiber texture and vignette

## Usage

### Local (if Java/Maven available)
```bash
mvn compile exec:java
```
The generated image will be saved to `output/notebook-paper-b5-lined.png`.

### GitHub Actions (automatic)
Push to `main` or manually trigger the workflow via "Actions > Generate Notebook Paper > Run workflow". The workflow will:
1. Compile and run the Java program on Ubuntu with JDK 11
2. Upload the PNG and jar as artifacts
3. Build the Android app and upload the APK as an artifact

## Output
- Dimensions: ~2079 × ~2953 pixels at 300 DPI
- File: `output/notebook-paper-b5-lined.png`

## Android
An Android library module (`android/library`) ports the generator to `Canvas`/`Bitmap`, and an app (`android/app`) consumes it. Build with:

```bash
cd android && ./gradlew :app:assembleDebug
```