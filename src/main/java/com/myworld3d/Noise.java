package com.myworld3d;

/** Tiny deterministic value-noise implementation used for terrain and color variation. */
public final class Noise {
    private Noise() {}

    public static double fbm(double x, double z, long seed, int octaves) {
        double total = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double normalizer = 0.0;
        for (int i = 0; i < octaves; i++) {
            total += smooth(x * frequency, z * frequency, seed + i * 7919L) * amplitude;
            normalizer += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return total / normalizer;
    }

    public static double smooth(double x, double z, long seed) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        double tx = fade(x - x0);
        double tz = fade(z - z0);
        double a = lerp(hashUnit(x0, z0, seed), hashUnit(x0 + 1, z0, seed), tx);
        double b = lerp(hashUnit(x0, z0 + 1, seed), hashUnit(x0 + 1, z0 + 1, seed), tx);
        return lerp(a, b, tz);
    }

    public static double hashUnit(int x, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h & 0xFFFFFFL) / (double) 0x7FFFFF) - 1.0;
    }

    private static int fastFloor(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    private static double fade(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
