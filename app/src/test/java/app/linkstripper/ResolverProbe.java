package app.linkstripper;

/** Optional live probe: URL enters via stdin and is never printed or saved. */
public class ResolverProbe {
    public static void main(String[] args) {
        try {
            String input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
            String result = LinkResolver.resolve(input);
            System.out.println("Resolved to recognized post; query=" + (java.net.URI.create(result).getQuery() != null));
        } catch (Exception ignored) { System.out.println("Resolution failed safely (no URL logged)."); System.exit(1); }
    }
}
