package app.linkstripper;

public class SiteRuleTest {
    private static int checks;
    private static void eq(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError("Rule result mismatch: "+expected+" vs "+actual);}
    private static void rejects(Runnable action){checks++;try{action.run();throw new AssertionError("Invalid rule accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args) {
        SiteRule post=SiteRule.fromExamples("https://example.com/post/ONE?utm_source=test","https://example.com/post/ONE?utm_source=test","https://example.com/post/ONE",false,"example.com");
        eq("/post/{part2}",post.targetPath());
        eq("https://example.com/post/TWO",post.apply("https://example.com/post/TWO?secret=REMOVED"));
        eq(true,post.matches("https://example.com/post/TWO"));
        eq(false,post.matches("https://example.com.evil.test/post/TWO"));
        eq(false,post.matches("https://example.com/post/TWO/extra"));
        eq("https://video.example/watch?v=VIDEO",SiteRule.suggestion("https://video.example/watch?v=VIDEO&utm_campaign=REMOVED#fragment"));
        SiteRule query=SiteRule.fromExamples("https://video.example/watch?v=ONE&utm_source=test","https://video.example/watch?v=ONE&utm_source=test","https://video.example/watch?v=ONE",false,"video.example");
        eq("https://video.example/watch?v=TWO",query.apply("https://video.example/watch?v=TWO&utm_source=test"));
        SiteRule shortRule=SiteRule.fromExamples("https://short.example/share/OPAQUE","https://posts.example/post/ONE","https://posts.example/post/ONE",true,"short.example,posts.example");
        eq(true,shortRule.matches("https://short.example/share/OTHER"));
        eq("https://posts.example/post/TWO",shortRule.apply("https://posts.example/post/TWO"));
        rejects(()->shortRule.apply("https://wrong.example/post/TWO"));
        rejects(()->new SiteRule("id","name","example.com","/{id}","example.com","/{id}","example.com","/{missing}","","example.com",false,true));
        rejects(()->SiteRule.fromExamples("https://example.com/watch?v=ONE","https://example.com/watch?v=ONE","https://example.com/watch?v=TWO",false,"example.com"));
        rejects(()->SiteRule.url("https://user:password@example.com/post/ONE"));
        rejects(()->SiteRule.url("https://localhost/post/ONE"));
        rejects(()->SiteRule.url("https://127.0.0.1/post/ONE"));
        rejects(()->SiteRule.url("file:///post/ONE"));
        rejects(()->SiteRule.url("https://example.com:443/post/ONE"));
        SiteRule root=SiteRule.fromExamples("https://example.com/?id=ONE","https://example.com/?id=ONE","https://example.com/?id=ONE",false,"example.com");
        eq("https://example.com/?id=TWO",root.apply("https://example.com/?id=TWO&track=REMOVED"));
        eq("https://example.com/post/ONE",UrlInspector.canonical("<link href='https://example.com/post/ONE' rel='canonical'>"));
        eq("https://example.com/post/ONE?a=1&b=2",UrlInspector.canonical("<meta content='https://example.com/post/ONE?a=1&amp;b=2' property='og:url'>"));
        eq("https://example.com/?id=ONE",SiteRule.suggestion("https://EXAMPLE.com?id=ONE&utm_source=REMOVED"));
        eq("https://notx.com/post?s=KEEP&t=KEEP",SiteRule.suggestion("https://notx.com/post?s=KEEP&t=KEEP"));
        rejects(()->SiteRule.fromExamples("https://example.com/post/OPAQUE_ID","https://example.com/post/OPAQUE_ID","https://example.com/post/SHORTENED",false,"example.com"));
        try {
            UrlInspector.Result blocked=UrlInspector.inspect("https://example.com/post/SYNTHETIC",java.util.Set.of());
            eq("example.com",blocked.needsHost()); eq(0,blocked.steps().size());
        }catch(Exception e){throw new AssertionError(e);}
        System.out.println(checks+" custom-rule checks passed (synthetic fixtures).");
    }
}
