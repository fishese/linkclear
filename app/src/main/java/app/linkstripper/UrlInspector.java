package app.linkstripper;

import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** User-initiated, anonymous lookup. Every fetched host must be explicitly allowed. */
public final class UrlInspector {
    public record Result(String url, List<String> steps, String needsHost) {}
    public static Result inspect(String input, Set<String> allowed) throws Exception {
        String next=input; List<String> steps=new ArrayList<>(); Set<String> seen=new HashSet<>();
        long deadline=System.nanoTime()+25_000_000_000L;
        for(int hop=0;hop<6;hop++) {
            URI uri=SiteRule.url(next);
            if(!"https".equalsIgnoreCase(uri.getScheme())) throw new Exception("Use HTTPS for online lookup.");
            if(!allowed.contains(uri.getHost().toLowerCase(Locale.ROOT))) return new Result(next,steps,uri.getHost());
            if(!seen.add(next)) throw new Exception("Redirect loop detected.");
            if(System.nanoTime()>deadline || Thread.currentThread().isInterrupted()) throw new Exception("Lookup timed out.");
            for(InetAddress address:InetAddress.getAllByName(uri.getHost())) {
                byte[] bytes=address.getAddress();
                if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress() ||
                    (bytes.length==16 && (bytes[0]&0xfe)==0xfc) || (bytes.length==4 && (bytes[0]&255)==100 && (bytes[1]&255)>=64 && (bytes[1]&255)<=127)) throw new Exception("Lookup only supports public internet addresses.");
            }
            steps.add(next);
            HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(6000); c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (compatible; LinkClear/0.2)");
            try {
                int code=c.getResponseCode();
                if(code>=300 && code<400) {
                    String location=c.getHeaderField("Location"); if(location==null) throw new Exception("Redirect has no destination.");
                    next=uri.resolve(location).toString(); continue;
                }
                if(code!=200) throw new Exception("Site returned HTTP "+code+". Try the browser if login is required.");
                String type=c.getContentType();
                if(type!=null && type.toLowerCase(Locale.ROOT).contains("text/html")) {
                    java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
                    try(var stream=c.getInputStream()) {
                        byte[] chunk=new byte[8192]; int n;
                        while(buffer.size()<524288 && (n=stream.read(chunk,0,Math.min(chunk.length,524288-buffer.size())))>0) {
                            if(System.nanoTime()>deadline) throw new Exception("Lookup timed out."); buffer.write(chunk,0,n);
                        }
                    }
                    String canonical=canonical(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
                    if(canonical!=null) {
                        String candidate=uri.resolve(canonical).toString(); SiteRule.url(candidate);
                        if(!candidate.equals(next) && !seen.contains(candidate)) { next=candidate; continue; }
                    }
                }
                return new Result(next,steps,null);
            } finally { c.disconnect(); }
        }
        throw new Exception("Too many redirects. Try the built-in browser.");
    }
    static String canonical(String html) {
        var tags=java.util.regex.Pattern.compile("<(?:link|meta)\\b[^>]{0,8192}>",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        String og=null;
        while(tags.find()) {
            Map<String,String> attributes=new HashMap<>();
            var attrs=java.util.regex.Pattern.compile("([A-Za-z]+)\\s*=\\s*([\"'])(.*?)\\2").matcher(tags.group());
            while(attrs.find()) attributes.put(attrs.group(1).toLowerCase(Locale.ROOT),attrs.group(3).replace("&amp;","&"));
            if("canonical".equalsIgnoreCase(attributes.get("rel")) && attributes.get("href")!=null) return attributes.get("href");
            if("og:url".equalsIgnoreCase(attributes.get("property"))) og=attributes.get("content");
        }
        return og;
    }
    public static String apply(SiteRule rule,String source) throws Exception {
        if(!rule.resolveFirst()) return rule.apply(source);
        Result result=inspect(source,SiteRule.csv(rule.allowedHosts()));
        if(result.needsHost()!=null) throw new Exception("This rule needs an additional allowed lookup host. Edit it in Site rules.");
        return rule.apply(result.url());
    }
}
