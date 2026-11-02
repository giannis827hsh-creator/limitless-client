package com.example.mod.module.antivanish;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text parsing deliberately accepts only explicit departure messages, not arbitrary chat mentions. */
public final class StaffText {
    private static final Pattern LEAVE = Pattern.compile(
            "(?i)^\\s*(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)?([A-Z0-9_]{3,16})\\s+(?:left the (?:game|server)|disconnected|quit)[.!\\s]*$");
    public static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public static Set<String> names(String csv) {
        return Arrays.stream(csv.split("[,;\\s]+"))
                .map(StaffText::normalize).filter(s -> s.matches("[a-z0-9_]{3,16}"))
                .limit(128).collect(Collectors.toUnmodifiableSet());
    }
    public static String departed(String text) {
        if (text == null || text.length() > 256) return "";
        String clean = text.replaceAll("(?i)\u00a7[0-9a-fk-orx]", "");
        Matcher matcher = LEAVE.matcher(clean);
        return matcher.matches() ? normalize(matcher.group(1)) : "";
    }
    public static boolean containsName(String decorated, String name) {
        if (decorated == null || name == null || name.isBlank()) return false;
        return Pattern.compile("(?i)(?<![a-z0-9_])" + Pattern.quote(name) + "(?![a-z0-9_])").matcher(decorated).find();
    }
    private StaffText() { }
}
