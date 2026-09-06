package app.linkstripper;

public class ParserTest {
    private static int checks;
    private static void eq(String expected, String actual) { checks++; if (!expected.equals(actual)) throw new AssertionError("Canonical mismatch"); }
    private static void reject(String input) { checks++; try { LinkCleaner.clean(input); throw new AssertionError("Accepted unsupported input"); } catch (IllegalArgumentException expected) {} }
    public static void main(String[] args) {
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
