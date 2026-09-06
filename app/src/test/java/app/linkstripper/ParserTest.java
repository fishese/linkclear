package app.linkstripper;

public class ParserTest {
    private static int checks;
    private static void eq(String expected, String actual) { checks++; if (!expected.equals(actual)) throw new AssertionError("Canonical mismatch"); }
    private static void reject(String input) { checks++; try { LinkCleaner.clean(input); throw new AssertionError("Accepted unsupported input"); } catch (IllegalArgumentException expected) {} }
    public static void main(String[] args) {
        eq("https://x.com/example/status/123456789/", LinkCleaner.clean("https://twitter.com/example/status/123456789?s=20"));
        eq("https://x.com/example/status/123456789/", LinkCleaner.clean("https://x.com/example/status/123456789?s=19&t=SYNTHETIC#fragment"));
        eq("https://x.com/example/status/123456789/photo/2/", LinkCleaner.clean("https://mobile.twitter.com/example/status/123456789/photo/2?s=20"));
        eq("https://x.com/i/web/status/123456789/", LinkCleaner.clean("https://www.twitter.com/i/web/status/123456789?s=19"));
        eq("https://x.com/i/status/123456789/", LinkCleaner.clean("https://x.com/i/status/123456789?t=SYNTHETIC"));
        eq("false", Boolean.toString(LinkCleaner.needsResolution("https://x.com/share/SYNTHETIC")));
        eq("false", Boolean.toString(LinkCleaner.sameProvider(java.net.URI.create("https://threads.com/"), java.net.URI.create("https://x.com/"))));
        reject("https://x.com.evil.test/example/status/123456789?s=20");
        reject("https://x.com/example/status/not_a_post?s=20");
        eq("https://www.instagram.com/p/TEST_123/", LinkCleaner.clean("https://www.instagram.com/p/TEST_123/?stkn=SYNTHETIC_TOKEN"));
        eq("https://www.instagram.com/p/TEST_123/", LinkCleaner.clean("https://www.instagram.com/p/TEST_123/?stkn=SYNTHETIC_TOKEN&igsh=REMOVED&img_index=2#REMOVED"));
        eq("https://www.instagram.com/p/TEST_123/", LinkCleaner.clean("https://www.instagram.com/p/TEST_123/?igsh=SYNTHETIC&utm_source=test#fragment"));
        eq("https://www.instagram.com/reel/TEST-123/", LinkCleaner.clean("http://m.instagram.com/reels/TEST-123?igshid=SYNTHETIC"));
        eq("https://www.threads.com/@example/post/TEST123/", LinkCleaner.clean("https://www.threads.net/@example/post/TEST123?xmt=SYNTHETIC"));
        eq("https://www.threads.com/@example/post/TEST123/", LinkCleaner.clean("https://threads.com/@example/post/TEST123/#fragment"));
        eq("https://instagram.com/p/TEST/", LinkCleaner.extract("A caption (https://instagram.com/p/TEST/)."));
        eq("true", Boolean.toString(LinkCleaner.needsResolution("https://www.instagram.com/share/p/SYNTHETIC/?igsh=TEST")));
        eq("true", Boolean.toString(LinkCleaner.needsResolution("https://www.threads.com/share/SYNTHETIC")));
        eq("true", Boolean.toString(LinkCleaner.needsResolution("https://threads.net/t/TEST_123?xmt=SYNTHETIC")));
        reject("https://www.threads.com/");
        reject("https://www.threads.com/share/SYNTHETIC");
        for (String url : new String[]{"https://instagram.com.evil.test/p/TEST/", "https://evil.test/p/TEST/", "https://user@instagram.com/p/TEST/", "https://instagram.com:443/p/TEST/", "https://instagram.com/example/", "https://instagram.com/stories/example/123/", "https://instagram.com/share/p/SYNTHETIC/", "https://instagram.com/p/TEST/extra", "https://threads.com/@example/", "https://instagram.com/p/%2F/", "file:///p/TEST/", "https://instagram.com/p/../"}) reject(url);
        checks++; try { LinkCleaner.extract("https://instagram.com/p/A/ https://instagram.com/p/B/"); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        checks++; try { LinkCleaner.extract("no link"); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        System.out.println(checks + " parser checks passed (synthetic fixtures).");
    }
}
