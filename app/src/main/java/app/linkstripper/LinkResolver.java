package app.linkstripper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/** Anonymous, bounded lookup; no cookies, login, WebView, or URL logging. */
public final class LinkResolver {
    static String googleDestination(String destination) throws Exception {
        URI uri=SiteRule.url(destination);
        // Google may wrap its external destination in a standard /url redirect.
        if(("www.google.com".equals(uri.getHost()) || "google.com".equals(uri.getHost())) && "/url".equals(uri.getPath()) && uri.getRawQuery()!=null) {
            for(String pair:uri.getRawQuery().split("&")) {
                String[] parts=pair.split("=",2);
                if(parts.length==2 && (parts[0].equals("url") || parts[0].equals("q"))) {
                    destination=java.net.URLDecoder.decode(parts[1],StandardCharsets.UTF_8.name());
                    uri=SiteRule.url(destination); break;
                }
            }
        }
        String host=uri.getHost();
        if(host.equals("share.google") || host.equals("search.app") || host.equals("google.com") || host.endsWith(".google.com"))
            throw new Exception("Google did not provide an external destination. Try the browser.");
        try {return LinkCleaner.clean(destination);} catch(IllegalArgumentException ignored) {return destination;}
    }
    public static String resolve(String original) throws Exception {
        if(LinkCleaner.googleShare(original)) {
            URI source=URI.create(original);
            // Fetch only Google's shortener. The external destination is returned, never fetched.
            UrlInspector.Result result=UrlInspector.inspect("https://"+source.getHost()+source.getRawPath(), java.util.Set.of("share.google","search.app","www.google.com","google.com"),
                uri -> LinkCleaner.googleShare(uri.toString()) ||
                    ((uri.getHost().equals("www.google.com") || uri.getHost().equals("google.com")) && uri.getPath().equals("/share.google") && uri.getRawQuery()!=null));
            if(result.needsHost()==null) throw new Exception("Google did not provide a destination.");
            return googleDestination(result.url());
        }
        URI input = LinkCleaner.validated(original);
        String next = "https://" + input.getHost() + input.getRawPath();
        long deadline = System.nanoTime() + 25_000_000_000L;
        for (int hop = 0; hop < 5; hop++) {
            if (System.nanoTime() > deadline) throw new Exception();
            URI uri = LinkCleaner.validated(next);
            if (!LinkCleaner.sameProvider(uri, input)) throw new Exception();
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new Exception();
            try { return LinkCleaner.clean(next); } catch (IllegalArgumentException ignored) {}
            // Only known share tokens can be fetched. Never follow login or arbitrary paths.
            if (!LinkCleaner.needsResolution(next)) throw new Exception();
            HttpsURLConnection c = (HttpsURLConnection) uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(7000); c.setReadTimeout(7000);
            browserHeaders(c);
            boolean threadsHead = (uri.getHost().endsWith("threads.com") || uri.getHost().endsWith("threads.net")) && LinkCleaner.needsResolution(next);
            if (threadsHead) c.setRequestMethod("HEAD");
            try {
                int status = c.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new Exception();
                    next = uri.resolve(location).toString();
                    continue;
                }
                if (status != 200) throw new Exception();
                byte[] bytes;
                try (var stream = c.getInputStream(); var buffer = new java.io.ByteArrayOutputStream()) {
                    byte[] chunk = new byte[8192]; int n;
                    while ((n = stream.read(chunk)) != -1) {
                        if (System.nanoTime() > deadline || buffer.size() + n > 524288) throw new Exception();
                        buffer.write(chunk, 0, n);
                    }
                    bytes = buffer.toByteArray();
                }
                String html = new String(bytes, StandardCharsets.UTF_8);
                var tags = Pattern.compile("<(?:link|meta)\\b[^>]{0,8192}>", Pattern.CASE_INSENSITIVE).matcher(html);
                while (tags.find()) {
                    String tag = tags.group();
                    var attrs = Pattern.compile("([A-Za-z]+)\\s*=\\s*([\"'])(.*?)\\2").matcher(tag);
                    java.util.Map<String,String> values = new java.util.HashMap<>();
                    while (attrs.find()) values.put(attrs.group(1).toLowerCase(java.util.Locale.ROOT), attrs.group(3));
                    String candidate = "canonical".equalsIgnoreCase(values.get("rel")) ? values.get("href") :
                        "og:url".equalsIgnoreCase(values.get("property")) ? values.get("content") : null;
                    if (candidate != null) {
                        candidate = uri.resolve(candidate.replace("&amp;", "&")).toString();
                        if (!LinkCleaner.sameProvider(LinkCleaner.validated(candidate), input)) continue;
                        try { return LinkCleaner.clean(candidate); } catch (IllegalArgumentException ignored) {}
                    }
                }
                throw new Exception();
            } finally { c.disconnect(); }
        }
        throw new Exception();
    }

    static void browserHeaders(HttpsURLConnection connection) {
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; LinkClear/0.3; +https://linkclear.fishese.cc/)");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
    }
}
