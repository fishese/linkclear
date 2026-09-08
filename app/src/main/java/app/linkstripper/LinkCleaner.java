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
            Pattern.compile("^/@[A-Za-z0-9._]+/post/[A-Za-z0-9_-]+/?$")),
        new Rule(List.of("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "m.twitter.com"), "x.com",
            Pattern.compile("^/(?:[A-Za-z0-9_]{1,15}/status|i/web/status|i/status)/[0-9]+(?:/(?:photo|video)/[1-4])?/?$"))
    );
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\\"\\u201c\\u201d]+", Pattern.CASE_INSENSITIVE);
    public static String extract(String text) {
        if (text == null || text.length() > 32768) throw new IllegalArgumentException("Share one post link at a time (up to 32 KB).");
        Matcher m = URL.matcher(text);
        if (!m.find()) throw new IllegalArgumentException("No web link found.");
        String url = m.group().replaceAll("[.,!;:)\\]}'\\u2019]+$", "");
        if (m.find()) throw new IllegalArgumentException("Multiple links found. Share one post link at a time.");
        return url;
    }
    public static String replaceUrl(String text, String original, String replacement) {
        int start=text.indexOf(original);
        if(start<0) return text;
        return text.substring(0,start)+replacement+text.substring(start+original.length());
    }
    public static boolean googleShare(String url) {
        try {
            URI u=URI.create(url);
            return ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) &&
                ("share.google".equalsIgnoreCase(u.getHost()) || "search.app".equalsIgnoreCase(u.getHost())) &&
                u.getUserInfo()==null && u.getPort()==-1 && u.getRawPath().matches("/[A-Za-z0-9_-]+/?");
        } catch(Exception e) { return false; }
    }
    public static URI validated(String url) {
        try {
            URI u = URI.create(url);
            if (!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) ||
                u.getHost() == null || u.getUserInfo() != null || u.getPort() != -1) throw new IllegalArgumentException();
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if (RULES.stream().noneMatch(r -> r.hosts.contains(host))) throw new IllegalArgumentException();
            return u;
        } catch (Exception e) { throw new IllegalArgumentException("Unsupported link."); }
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
        throw new IllegalArgumentException("This is not a recognized post URL.");
    }
    public static boolean needsResolution(String url) {
        if(googleShare(url)) return true;
        URI u = validated(url);
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith("instagram.com")) return u.getRawPath().matches("/share/(?:p|reel|r)/[A-Za-z0-9_-]+/?");
        return (host.endsWith("threads.com") || host.endsWith("threads.net")) &&
            u.getRawPath().matches("/(?:share|t)/[A-Za-z0-9_-]+/?");
    }
    static boolean sameProvider(URI first, URI second) {
        String a = first.getHost().toLowerCase(Locale.ROOT), b = second.getHost().toLowerCase(Locale.ROOT);
        return RULES.stream().anyMatch(r -> r.hosts.contains(a) && r.hosts.contains(b));
    }
}
