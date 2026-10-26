package com.notebookpaper.android;

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
 * The resolved typeface is cached so lookup happens once per process.
 */
public final class MyanmarSupport {

    private static final String MYANMAR_PROBE = "မြန်မာစာ";
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
     * Returns a typeface that can render Myanmar, falling back to the
     * system default. Result is cached after first resolution.
     */
    public static Typeface resolveTypeface() {
        if (sTypeface != null) return sTypeface;
        for (String name : FONT_CANDIDATES) {
            Typeface tf = Typeface.create(name, Typeface.NORMAL);
            if (tf != null && canDisplay(tf)) {
                return sTypeface = tf;
            }
        }
        return sTypeface = Typeface.DEFAULT;
    }

    /**
     * True if {@code tf} can render the Myanmar probe string.
     */
    public static boolean canDisplay(Typeface tf) {
        Paint p = new Paint();
        p.setTypeface(tf);
        return p.measureText(MYANMAR_PROBE) > 0;
    }

    /**
     * Splits {@code text} into grapheme clusters so complex-script stacks
     * (e.g. Myanmar) are never broken apart during rendering.
     */
    public static List<String> graphemeClusters(String text) {
        List<String> clusters = new ArrayList<>();
        BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
        bi.setText(text);
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            clusters.add(text.substring(start, end));
        }
        return clusters;
    }
}
