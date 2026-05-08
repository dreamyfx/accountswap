package dev.dreamyfx.accountswap.animation;

public class AnimationUtil {

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    public static float easeInOutCubic(float t) {
        if (t < 0.5f) return 4f * t * t * t;
        float f = (2f * t) - 2f;
        return 0.5f * f * f * f + 1f;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float f = t - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    }

    public static float easeOutQuart(float t) {
        float f = 1f - t;
        return 1f - f * f * f * f;
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float smoothStep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        int r = (int) lerp(ar, br, t);
        int g = (int) lerp(ag, bg, t);
        int bl = (int) lerp(ab, bb, t);
        int al = (int) lerp(aa, ba, t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
