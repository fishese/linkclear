package app.linkstripper;

import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/** Declarative segment templates: no downloaded code or user-provided regular expressions. */
public record SiteRule(String id, String name, String sourceHost, String sourcePath,
        String targetHost, String targetPath, String outputHost, String outputPath,
        String keepQuery, String allowedHosts, boolean resolveFirst, boolean enabled) {
    private static final Set<String> ROUTES = Set.of("p", "reel", "reels", "tv", "post", "posts", "status", "i", "web", "share", "t", "r", "watch", "video", "videos", "photo", "article", "articles", "item", "view", "shorts");
    public SiteRule {
        if (id == null || name == null || name.trim().isEmpty() || name.length() > 80) fail("Enter a rule name (up to 80 characters).");
        host(sourceHost); host(targetHost); host(outputHost);
        template(sourcePath); Set<String> captures = template(targetPath);
        if (!captures.containsAll(template(outputPath))) fail("Output placeholders must exist in the target path.");
        if (keepQuery == null || keepQuery.length() > 1024 || allowedHosts == null || allowedHosts.length() > 2048) fail("Rule fields are too long.");
        for (String h : csv(allowedHosts)) host(h);
        if (resolveFirst && (!csv(allowedHosts).contains(sourceHost) || !csv(allowedHosts).contains(targetHost))) fail("Allowed lookup hosts must include the source and target hosts.");
    }
    public static URI url(String text) {
        try {
            if (text == null || text.length() > 8192) throw new Exception();
            URI u = URI.create(text.trim());
            if (!("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) || u.getHost() == null || u.getUserInfo() != null || u.getPort() != -1) throw new Exception();
            host(u.getHost().toLowerCase(Locale.ROOT));
            for (String p : segments(u.getRawPath())) if (p.equals(".") || p.equals("..")) throw new Exception();
            String path=u.getRawPath().isEmpty()?"/":u.getRawPath();
            return URI.create(u.getScheme().toLowerCase(Locale.ROOT)+"://"+u.getHost().toLowerCase(Locale.ROOT)+path+(u.getRawQuery()==null?"":"?"+u.getRawQuery())+(u.getRawFragment()==null?"":"#"+u.getRawFragment()));
        } catch (Exception e) { throw new IllegalArgumentException("Use a full public HTTP(S) URL without credentials or an explicit port."); }
    }
    private static void host(String h) {
        if (h == null || h.length() > 253 || !h.equals(h.toLowerCase(Locale.ROOT)) || !h.matches("(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63}") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) fail("Use an exact public hostname, such as www.example.com.");
    }
    private static Set<String> template(String path) {
        if (path == null || !path.startsWith("/") || path.length() > 1024 || path.contains("?") || path.contains("#") || path.contains("\\")) fail("Paths must start with / and contain no query or fragment.");
        Set<String> names = new HashSet<>();
        for (String part : segments(path)) {
            if (variable(part)) { if (!names.add(part)) fail("Use each placeholder once per path."); }
            else if (!part.matches("[A-Za-z0-9_@.~-]*") || part.equals(".") || part.equals("..")) fail("Use whole-segment placeholders such as /post/{id}.");
        }
        return names;
    }
    private static boolean variable(String s) { return s.matches("\\{[A-Za-z][A-Za-z0-9_]*\\}"); }
    private static String[] segments(String path) { String value=path.startsWith("/")?path.substring(1):path; if(value.endsWith("/"))value=value.substring(0,value.length()-1); return value.split("/",-1); }
    private static Map<String,String> match(String pattern, String path) {
        String[] a=segments(pattern), b=segments(path);
        if (a.length != b.length) return null;
        Map<String,String> values = new HashMap<>();
        for (int i=0;i<a.length;i++) {
            if (variable(a[i])) { if (b[i].isEmpty() || !b[i].matches("[A-Za-z0-9_@.~-]+") || b[i].equals(".") || b[i].equals("..")) return null; values.put(a[i],b[i]); }
            else if (!a[i].equals(b[i])) return null;
        }
        return values;
    }
    public boolean matches(String input) {
        try { return matches(url(input)); }
        catch (IllegalArgumentException e) { return false; }
    }
    boolean matches(URI u) { return enabled && sourceHost.equalsIgnoreCase(u.getHost()) && match(sourcePath,u.getRawPath()) != null; }
    public String apply(String input) {
        URI u=url(input);
        Map<String,String> captures=match(targetPath,u.getRawPath());
        if (!targetHost.equalsIgnoreCase(u.getHost()) || captures == null) fail("The resolved URL does not match this rule's target host and path.");
        String path=outputPath;
        for (var entry:captures.entrySet()) path=path.replace(entry.getKey(),entry.getValue());
        String query=filterQuery(u.getRawQuery(),csv(keepQuery));
        return "https://"+outputHost+path+(query.isEmpty()?"":"?"+query);
    }
    public static Set<String> csv(String input) { Set<String> result=new LinkedHashSet<>(); for(String s:input.split(",")) if(!s.trim().isEmpty()) result.add(s.trim()); return result; }
    private static String filterQuery(String query, Set<String> keep) {
        if(query==null) return "";
        List<String> parts=new ArrayList<>();
        for(String p:query.split("&")) { String name=decode(p.split("=",2)[0]); if(keep.contains(name)) parts.add(p); }
        return String.join("&",parts);
    }
    public static String suggestion(String input) {
        URI u=url(input); Set<String> keep=new LinkedHashSet<>();
        if(u.getRawQuery()!=null) for(String p:u.getRawQuery().split("&")) {
            String name=decode(p.split("=",2)[0]), lower=name.toLowerCase(Locale.ROOT);
            if(!lower.startsWith("utm_") && !Set.of("igsh","igshid","stkn","xmt","fbclid","gclid","mc_cid","mc_eid").contains(lower)
                && !((u.getHost().equals("twitter.com") || u.getHost().endsWith(".twitter.com") || u.getHost().equals("x.com") || u.getHost().endsWith(".x.com")) && Set.of("s","t").contains(lower))) keep.add(name);
        }
        String q=filterQuery(u.getRawQuery(),keep);
        return "https://"+u.getHost().toLowerCase(Locale.ROOT)+u.getRawPath()+(q.isEmpty()?"":"?"+q);
    }
    private static String infer(String path) {
        String[] parts=segments(path);
        for(int i=0;i<parts.length;i++) if(!parts[i].isEmpty() && !ROUTES.contains(parts[i])) parts[i]="{part"+(i+1)+"}";
        return "/"+String.join("/",parts)+(path.endsWith("/") && !path.equals("/")?"/":"");
    }
    public static SiteRule fromExamples(String source, String resolved, String desired, boolean lookup, String allowed) {
        URI a=url(source), b=url(resolved), c=url(desired);
        String target=infer(b.getRawPath()), output=c.getRawPath();
        String[] raw=segments(b.getRawPath()), pattern=segments(target), out=segments(output);
        for(int i=0;i<out.length;i++) for(int j=0;j<raw.length;j++) if(variable(pattern[j]) && out[i].equals(raw[j])) { out[i]=pattern[j]; break; }
        output="/"+String.join("/",out)+(output.endsWith("/") && !output.equals("/")?"/":"");
        Set<String> keep=new LinkedHashSet<>();
        if(c.getRawQuery()!=null) for(String p:c.getRawQuery().split("&")) keep.add(decode(p.split("=",2)[0]));
        if(!template(target).isEmpty() && template(output).isEmpty() && keep.isEmpty()) fail("The edited URL no longer contains a changing post identifier. This builder supports whole path segments, not trimming part of an ID.");
        SiteRule rule=new SiteRule(UUID.randomUUID().toString(),a.getHost(),a.getHost(),lookup?infer(a.getRawPath()):target,b.getHost(),target,c.getHost(),output,String.join(",",keep),allowed,lookup,true);
        if(!rule.apply(resolved).equals("https://"+c.getHost()+c.getRawPath()+(c.getRawQuery()==null?"":"?"+c.getRawQuery()))) fail("This example changes query values. Rules can keep or remove query keys, but cannot rewrite their values.");
        return rule;
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
    private static String decode(String value) { try{return URLDecoder.decode(value,"UTF-8");}catch(Exception e){throw new IllegalArgumentException("Invalid query key encoding.");} }
}
