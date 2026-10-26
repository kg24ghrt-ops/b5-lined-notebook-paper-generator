package com.notebookpaper.android;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.icu.text.BreakIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Myanmar text support: font resolution and grapheme clustering.
 * <p>
 * Myanmar is a complex script: character stacks must stay whole, so
 * handwriting jitter is applied per-cluster, never per-codepoint.
 * <p>
 * A bundled Noto Serif Myanmar font is preferred so rendering does not
 * depend on the device shipping a Myanmar-capable typeface. The resolved
 * typeface is cached after first resolution.
 */
public final class MyanmarSupport {

    private static final String MYANMAR_PROBE = "မြန်မာစာ";
    private static final String MYANMAR_VIRAMA = "\u1039";
    private static final String BUNDLED_FONT_ASSET = "fonts/NotoSerifMyanmar-Regular.ttf";
    private static final String[] FONT_CANDIDATES = {
            "NotoSerifMyanmar-Regular",
            "NotoSansMyanmar-Regular",
            "NotoSerif-Regular",
            "serif",
            "sans-serif"
    };

    private static Typeface sTypeface;

    private MyanmarSupport() {
    }

    /**
     * Returns a typeface that can render Myanmar. The bundled asset is tried
     * first, then system candidates, then the default. Result is cached.
     */
    public static Typeface resolveTypeface(Context context) {
        if (sTypeface != null) return sTypeface;
        Typeface bundled = Typeface.createFromAsset(context.getAssets(), BUNDLED_FONT_ASSET);
        if (canDisplay(bundled)) {
            return sTypeface = bundled;
        }
        for (String name : FONT_CANDIDATES) {
            Typeface tf = Typeface.create(name, Typeface.NORMAL);
            if (tf != null && canDisplay(tf)) {
                return sTypeface = tf;
            }
        }
        return sTypeface = Typeface.DEFAULT;
    }

    /**
     * True if {@code tf} can render every codepoint of the Myanmar probe.
     * {@link Paint#measureText} reports a positive advance even for tofu boxes,
     * so {@code hasGlyph} is used so resolution actually falls back when a font
     * lacks Myanmar glyphs.
     */
    public static boolean canDisplay(Typeface tf) {
        Paint p = new Paint();
        p.setTypeface(tf);
        return p.hasGlyph(MYANMAR_PROBE);
    }

    /**
     * Splits {@code text} into grapheme clusters so complex-script stacks
     * (e.g. Myanmar) are never broken apart during rendering.
     * <p>
     * ICU breaks after U+1039 (virama), so a cluster is merged into the
     * previous one when the previous ends with the virama, keeping stacks like
     * ဗုဒ္ဓ whole.
     */
    public static List<String> graphemeClusters(String text) {
        List<String> clusters = new ArrayList<>();
        BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
        bi.setText(text);
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            String cluster = text.substring(start, end);
            int last = clusters.size() - 1;
            boolean merge = !cluster.isEmpty() && last >= 0
                    && clusters.get(last).endsWith(MYANMAR_VIRAMA)
                    && !cluster.trim().isEmpty();
            if (merge) {
                clusters.set(last, clusters.get(last) + cluster);
            } else {
                clusters.add(cluster);
            }
        }
        return clusters;
    }
}
