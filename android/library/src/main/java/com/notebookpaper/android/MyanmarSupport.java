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
     * ICU's character instance is not enough for Myanmar: it splits U+102B/U+102C
     * (aa/tall aa) away from their syllable, and breaks after U+1039 (virama).
     * A lone combining mark is drawn with a dotted circle, so after ICU
     * segmentation, any cluster that starts with a Myanmar combining mark is
     * merged into the previous cluster, and so is the consonant following a virama.
     */
    public static List<String> graphemeClusters(String text) {
        List<String> clusters = new ArrayList<>();
        BreakIterator bi = BreakIterator.getCharacterInstance(Locale.ROOT);
        bi.setText(text);
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            String cluster = text.substring(start, end);
            int last = clusters.size() - 1;
            boolean merge = last >= 0
                    && !cluster.trim().isEmpty()
                    && !clusters.get(last).trim().isEmpty()
                    && (clusters.get(last).endsWith(MYANMAR_VIRAMA)
                        || isMyanmarCombiningMark(cluster.codePointAt(0)));
            if (merge) {
                clusters.set(last, clusters.get(last) + cluster);
            } else {
                clusters.add(cluster);
            }
        }
        return clusters;
    }

    /** True for Myanmar vowel signs, medials, tone marks, asat and virama. */
    private static boolean isMyanmarCombiningMark(int cp) {
        boolean inMyanmar = (cp >= 0x1000 && cp <= 0x109F)
                || (cp >= 0xA9E0 && cp <= 0xA9FF)
                || (cp >= 0xAA60 && cp <= 0xAA7F);
        if (!inMyanmar) return false;
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
    }
}
