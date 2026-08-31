package com.nbs.hebsubdl;

public final class AppVersion {
    private static final String FALLBACK = "dev";

    private AppVersion() {
    }

    public static String get() {
        // Only populated when running from the packaged jar; IDE/`gradle run` have no manifest.
        String version = AppVersion.class.getPackage().getImplementationVersion();
        return (version == null || version.isBlank()) ? FALLBACK : version;
    }
}
