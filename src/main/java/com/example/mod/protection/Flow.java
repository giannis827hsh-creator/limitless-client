package com.example.mod.protection;

/** Small branch predicates used by the release-only control-flow pass. */
public final class Flow {
    private Flow() { }

    public static boolean eq0(int value) { return value == 0; }
    public static boolean ne0(int value) { return value != 0; }
    public static boolean lt0(int value) { return value < 0; }
    public static boolean ge0(int value) { return value >= 0; }
    public static boolean gt0(int value) { return value > 0; }
    public static boolean le0(int value) { return value <= 0; }
    public static boolean eq(int left, int right) { return left == right; }
    public static boolean ne(int left, int right) { return left != right; }
    public static boolean lt(int left, int right) { return left < right; }
    public static boolean ge(int left, int right) { return left >= right; }
    public static boolean gt(int left, int right) { return left > right; }
    public static boolean le(int left, int right) { return left <= right; }
    public static boolean same(Object left, Object right) { return left == right; }
    public static boolean different(Object left, Object right) { return left != right; }
    public static boolean isNull(Object value) { return value == null; }
    public static boolean nonNull(Object value) { return value != null; }
}
