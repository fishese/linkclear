package app.linkstripper;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java parser. New providers belong in RULES, not the Android share flow. */
public final class LinkCleaner {
    public record Rule(List<String> hosts, String canonicalHost, Pattern path) {}
    private static final List<Rule> RULES = List.of(
        new Rule(List.of("instagram.com", "www.instagram.com", "m.instagram.com"), "www.instagram.com",
            Pattern.compile("^/(p|reel|reels|tv)/([A-Za-z0-9_-]+)/?$")),
        new Rule(List.of("threads.net", "www.threads.net", "threads.com", "www.threads.com"), "www.threads.com",
            Pattern.compile("^/@[A-Za-z0-9._]+/post/[A-Za-z0-9_-]+/?$"))
    );
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"\\u201c\\u201d]+", Pattern.CASE_INSENSITIVE);
    public static String extract(String text) {
        if (text == null || text.length() > 32768) throw new IllegalArgumentException("Share one post link at a time (up to 32 KB).");
        Matcher m = URL.matcher(text);
        if (!m.find()) throw new IllegalArgumentException("No web link found. Paste or share a full Instagram or Threads post URL.");
        String url = m.group().replaceAll("[.,!;:)\\]}'\\u2019]+$", "");
        if (m.find()) throw new IllegalArgumentException("Multiple links found. Share one post link at a time.");
        return url;
    }
    public static URI validated(String url) {
        try {
            URI u = URI.create(url);
            if (!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) ||
                u.getHost() == null || u.getUserInfo() != null || u.getPort() != -1) throw new IllegalArgumentException();
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if (RULES.stream().noneMatch(r -> r.hosts.contains(host))) throw new IllegalArgumentException();
            return u;
        } catch (Exception e) { throw new IllegalArgumentException("Unsupported link. Only Instagram and Threads post links are supported."); }
    }
    public static String clean(String url) {
        URI u = validated(url);
        for (Rule r : RULES) {
            if (!r.hosts.contains(u.getHost().toLowerCase(Locale.ROOT))) continue;
            String path = u.getRawPath();
            if (r.path.matcher(path).matches()) {
                path = path.replaceFirst("^/reels/", "/reel/");
                return "https://" + r.canonicalHost + path + (path.endsWith("/") ? "" : "/");
            }
        }
        throw new IllegalArgumentException("This is not a recognized post URL. Profiles, stories and unknown formats cannot be safely forwarded.");
    }
    public static boolean needsResolution(String url) {
        URI u = validated(url);
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith("instagram.com")) return u.getRawPath().matches("/share/(?:p|reel|r)/[A-Za-z0-9_-]+/?");
        return u.getRawPath().matches("/(?:share|t)/[A-Za-z0-9_-]+/?");
    }
}
