
package com.example.mod.spotify;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import com.example.mod.spotify.LrclibTrack;
import com.example.mod.spotify.LyricsQuery;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class LyricsMatcher {
    static final double DURATION_TOLERANCE_SECONDS = 3.0;
    private static final int TITLE_EXACT = 40;
    private static final int TITLE_BASE = 30;
    private static final int TITLE_CONTAINS = 12;
    private static final int ARTIST_EXACT = 30;
    private static final int ARTIST_SHARED = 20;
    private static final int ARTIST_CONTAINS = 10;
    private static final int DURATION_MAX = 20;
    private static final int SYNCED = 25;
    private static final int INSTRUMENTAL = 8;
    private static final int PLAIN = 5;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern APOSTROPHES = Pattern.compile("['\u2019`\u00b4]");
    private static final Pattern BRACKETED = Pattern.compile("\\s*[(\\[][^)\\]]*\\b(feat|ft|featuring|with|remaster(ed)?|explicit|clean|mono|stereo|bonus|deluxe|anniversary|edition|single version|album version)\\b[^)\\]]*[)\\]]", 2);
    private static final Pattern DASHED = Pattern.compile("\\s+[-\u2013\u2014]\\s+[^-\u2013\u2014]*\\b(remaster(ed)?|explicit|clean|mono|stereo|bonus track|deluxe|anniversary|edition|single version|album version)\\b.*$", 2);
    private static final Pattern FEATURING = Pattern.compile("\\s+(feat\\.?|ft\\.|featuring)\\s+.*$", 2);
    private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(?:,|;|/|&|\u2022|\\+|\\bx\\b|\\band\\b|\\bwith\\b|\\bfeat\\b\\.?|\\bft\\b\\.?|\\bfeaturing\\b)\\s*", 2);
    private static final Pattern CREDIT_SPLIT = Pattern.compile("\\s*[,;\u2022]\\s*|\\s+(?:&|x|feat\\.?|ft\\.?|featuring|with)\\s+", 2);

    private LyricsMatcher() {
    }

    static LrclibTrack best(List<LrclibTrack> candidates, LyricsQuery query) {
        if (candidates == null || candidates.isEmpty() || query == null) {
            return null;
        }
        LrclibTrack best = null;
        int bestScore = -1;
        double bestDrift = Double.MAX_VALUE;
        for (LrclibTrack candidate : candidates) {
            int score;
            if (candidate == null || (score = LyricsMatcher.score(candidate, query)) < 0) continue;
            double drift = LyricsMatcher.drift(candidate, query);
            if (score <= bestScore && (score != bestScore || !(drift < bestDrift))) continue;
            best = candidate;
            bestScore = score;
            bestDrift = drift;
        }
        return best;
    }

    static int score(LrclibTrack candidate, LyricsQuery query) {
        if (!candidate.usable()) {
            return -1;
        }
        int duration = LyricsMatcher.durationScore(candidate, query);
        if (duration < 0) {
            return -1;
        }
        int title = LyricsMatcher.titleScore(query.title(), candidate.trackName());
        if (title <= 0) {
            return -1;
        }
        int artist = LyricsMatcher.artistScore(query.artist(), candidate.artistName());
        if (artist <= 0) {
            return -1;
        }
        int content = candidate.hasSynced() ? 25 : (candidate.hasPlain() ? 5 : 8);
        return title + artist + duration + content;
    }

    private static int durationScore(LrclibTrack candidate, LyricsQuery query) {
        if (!query.hasDuration() || candidate.duration() <= 0.0) {
            return 0;
        }
        double drift = Math.abs(candidate.duration() - (double)query.durationSeconds());
        if (drift > 3.0) {
            return -1;
        }
        return (int)Math.round(20.0 * (1.0 - drift / 6.0));
    }

    private static double drift(LrclibTrack candidate, LyricsQuery query) {
        if (!query.hasDuration() || candidate.duration() <= 0.0) {
            return 3.0;
        }
        return Math.abs(candidate.duration() - (double)query.durationSeconds());
    }

    static int titleScore(String wanted, String offered) {
        String a = LyricsMatcher.normalize(wanted);
        String b = LyricsMatcher.normalize(offered);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 40;
        }
        String baseA = LyricsMatcher.normalize(LyricsMatcher.baseTitle(wanted));
        String baseB = LyricsMatcher.normalize(LyricsMatcher.baseTitle(offered));
        if (!baseA.isEmpty() && baseA.equals(baseB)) {
            return 30;
        }
        if (LyricsMatcher.containsWords(baseA, baseB) || LyricsMatcher.containsWords(baseB, baseA)) {
            return 12;
        }
        return 0;
    }

    static int artistScore(String wanted, String offered) {
        String first;
        String a = LyricsMatcher.normalize(wanted);
        String b = LyricsMatcher.normalize(offered);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 30;
        }
        Set<String> wantedArtists = LyricsMatcher.artists(wanted);
        Set<String> offeredArtists = LyricsMatcher.artists(offered);
        for (String artist : wantedArtists) {
            if (!offeredArtists.contains(artist)) continue;
            return 20;
        }
        String string = first = wantedArtists.isEmpty() ? a : wantedArtists.iterator().next();
        if (LyricsMatcher.containsWords(b, first) || LyricsMatcher.containsWords(a, b)) {
            return 10;
        }
        return 0;
    }

    static String baseTitle(String title) {
        if (title == null) {
            return "";
        }
        String base = BRACKETED.matcher(title).replaceAll("");
        base = DASHED.matcher(base).replaceFirst("");
        base = FEATURING.matcher(base).replaceFirst("");
        return (base = base.strip()).isEmpty() ? title.strip() : base;
    }

    static String primaryArtist(String artist) {
        String[] parts;
        if (artist == null) {
            return "";
        }
        for (String part : parts = CREDIT_SPLIT.split(artist.strip())) {
            if (part.isBlank()) continue;
            return part.strip();
        }
        return artist.strip();
    }

    static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String folded = text.toLowerCase(Locale.ROOT).replace('\u0131', 'i').replace("\u00df", "ss").replace("\u00e6", "ae").replace("\u0153", "oe").replace('\u00f8', 'o').replace('\u0142', 'l').replace('\u0111', 'd').replace("&", " and ");
        folded = MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        folded = APOSTROPHES.matcher(folded).replaceAll("");
        return NON_WORD.matcher(folded).replaceAll(" ").strip();
    }

    private static Set<String> artists(String credit) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (String part : ARTIST_SPLIT.split(credit)) {
            String name = LyricsMatcher.normalize(part);
            if (name.isEmpty()) continue;
            names.add(name);
        }
        return names;
    }

    private static boolean containsWords(String haystack, String needle) {
        if (needle.length() < 3 || haystack.length() < needle.length()) {
            return false;
        }
        return (" " + haystack + " ").contains(" " + needle + " ");
    }

    static List<String[]> searchTerms(LyricsQuery query) {
        ArrayList<String[]> terms = new ArrayList<String[]>(2);
        terms.add(new String[]{query.title(), query.artist()});
        String title = LyricsMatcher.baseTitle(query.title());
        String artist = LyricsMatcher.primaryArtist(query.artist());
        if (!title.equals(query.title()) || !artist.equals(query.artist())) {
            terms.add(new String[]{title, artist});
        }
        return terms;
    }
}
