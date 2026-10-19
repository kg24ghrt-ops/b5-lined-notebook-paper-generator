// notebook_paper.h
// C++20 native notebook paper generator for Android NDK.
// Renders a realistic B5 lined page and overlays typed text that rides
// along the ruled lines like writing on school notebook paper.
#pragma once

#include <android/bitmap.h>
#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace notebookpaper {

struct PaperSpec {
    int width_px = 0;
    int height_px = 0;
    float scale_x = 1.0f;   // px per B5 mm (width)
    float scale_y = 1.0f;   // px per B5 mm (height)

    // Notebook measurements in B5 mm.
    float top_margin_mm = 25.0f;
    float header_gap_mm = 4.0f;
    float line_spacing_mm = 8.0f;
    float left_margin_mm = 20.0f;
    float hole_x_mm = 8.0f;
    float hole_radius_mm = 3.0f;
    float first_hole_y_mm = 30.0f;
    float last_hole_y_mm = 220.0f;
    int buckle_count = 2;
};

// A single line of typed text positioned on a ruled line.
struct TextLine {
    std::string text;
    int line_index = 0;      // 0-based ruled line index from top margin
    float indent_mm = 0.0f;  // extra left indent in B5 mm
    float font_size_mm = 3.5f;
    uint32_t color = 0xFF1F1F1F; // ARGB
};

struct RenderOptions {
    bool draw_paper = true;
    bool draw_text = true;
    std::vector<TextLine> lines;
};

// Renders the paper (and optional text) into an AndroidBitmap.
// The bitmap must be ARGB_8888 and sized to width_px x height_px.
bool render_to_bitmap(const PaperSpec& spec, const RenderOptions& options, void* bitmap);

// Convenience: returns the ruled line Y (in px) for a given line index.
inline float line_y_for_index(const PaperSpec& spec, int index) {
    float top = spec.top_margin_mm * spec.scale_y;
    float spacing = spec.line_spacing_mm * spec.scale_y;
    return top + index * spacing;
}

}  // namespace notebookpaper