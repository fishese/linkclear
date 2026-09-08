package app.linkstripper;

/** Optional live probe: URL enters via stdin and is never printed or saved. */
public class ResolverProbe {
    public static void main(String[] args) {
        try {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            String input = reader.readLine();
            String expected = reader.readLine();
            String result = LinkResolver.resolve(input);
            if(expected!=null && !expected.equals(result)) throw new Exception();
            System.out.println("Resolved successfully" + (expected==null ? "" : "; exact destination verified") + "; query=" + (java.net.URI.create(result).getQuery()!=null));
        } catch (Exception ignored) { System.out.println("Resolution failed safely (no URL logged)."); System.exit(1); }
    }
}
