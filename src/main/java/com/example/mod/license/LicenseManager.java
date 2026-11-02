package com.example.mod.license;

import com.example.mod.LimitlessClient;
import java.util.concurrent.CompletableFuture;

/** Compatibility surface for modules that were written while limitless required a license. */
public final class LicenseManager {
    public static volatile String licenseStatusMessage = "limitless is free to use";

    private LicenseManager() { }

    public static boolean isLicensed() {
        return LimitlessClient.isRuntimeReady();
    }

    public static boolean hasValidLease() {
        return true;
    }

    public static boolean isChecking() {
        return false;
    }

    public static void init() { }

    public static void initBlocking() { }

    public static void tick() { }

    public static CompletableFuture<Boolean> validateKeyAsync(String ignored) {
        return CompletableFuture.completedFuture(true);
    }
}
