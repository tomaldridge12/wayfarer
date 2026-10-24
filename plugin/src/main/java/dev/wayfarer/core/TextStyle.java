package dev.wayfarer.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared text styling for every module: per-character gradients and colour maths. Adventure only. */
public final class TextStyle {
    private TextStyle() {
    }

    /** Text with each character coloured along a gradient from {@code from} to {@code to}. */
    public static TextComponent gradient(String text, int from, int to) {
        TextComponent.Builder out = Component.text();
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            double t = cps.length == 1 ? 0 : (double) i / (cps.length - 1);
            out.append(Component.text(new String(Character.toChars(cps[i])), TextColor.color(mix(from, to, t))));
        }
        return out.build().decoration(TextDecoration.ITALIC, false);
    }

    /** Blends two RGB colours: t = 0 gives a, t = 1 gives b. */
    public static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static double luminance(int rgb) {
        return (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255;
    }

    /** Text broken into lines of at most {@code width} characters at spaces (a longer word gets a line of its own). */
    public static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
