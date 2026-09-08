package app.linkstripper;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Exercises the editor and share integration using synthetic URLs only. */
public final class RuleEditorTest extends ShareFlowTest {
    private Activity current;
    private int checks;
    private boolean shareSuite;
    @Override public void onCreate(Bundle args){shareSuite=args!=null && "share".equals(args.getString("suite"));super.onCreate(args);}
    private void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private View find(View view,String text){
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    private View control(String text){return find(current.getWindow().getDecorView(),text);}
    private void click(String text){runOnMainSync(()->control(text).performClick());waitForIdleSync();}
    private void field(String label,String value){runOnMainSync(()->{TextView t=(TextView)control(label);((EditText)current.findViewById(t.getLabelFor())).setText(value);});}
    private boolean enabled(String text){AtomicBoolean result=new AtomicBoolean();runOnMainSync(()->result.set(control(text).isEnabled()));return result.get();}
    private void waitEnabled(String text)throws Exception{long end=System.currentTimeMillis()+4000;while(!enabled(text)&&System.currentTimeMillis()<end){Thread.sleep(20);waitForIdleSync();}check(enabled(text),"Test must enable save");}
    @Override public void onStart(){
        if(shareSuite){super.onStart();return;}
        var prefs=getTargetContext().getSharedPreferences("site_rules",0);
        String saved=prefs.getString("rules","[]"), previous=prefs.getString("previous","[]");
        boolean preview=getTargetContext().getSharedPreferences("MainActivity",0).getBoolean("preview",false);
        Bundle output=new Bundle(); int code=Activity.RESULT_OK;
        AtomicReference<String> shared=new AtomicReference<>();
        ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CHOOSER.equals(intent.getAction())){Intent content=intent.getParcelableExtra(Intent.EXTRA_INTENT);shared.set(content.getStringExtra(Intent.EXTRA_TEXT));return new ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
        addMonitor(monitor);
        try{
            prefs.edit().putString("rules","[]").putString("previous","[]").commit();
            current=startActivitySync(new Intent(getTargetContext(),RulesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            click("Add a site from a link");
            field("Original shared URL","https://example.com/post/FIRST_ID?utm_source=SYNTHETIC_SECRET");
            click("Use pasted URL without lookup");
            click("Build editable rule");
            check(!enabled("Save tested rule"),"Untested rule cannot save");
            click("Test rule");waitEnabled("Save tested rule");
            field("Output path template","/post/{missing}");
            check(!enabled("Save tested rule"),"Editing must invalidate test");
            field("Output path template","/post/{part2}");
            click("Test rule");waitEnabled("Save tested rule");
            click("Save tested rule");
            List<SiteRule> rules=RuleStore.load(getTargetContext());check(rules.size()==1,"Rule persisted");
            check(!prefs.getString("rules","").contains("FIRST_ID")&&!prefs.getString("rules","").contains("SYNTHETIC_SECRET"),"Samples must not persist");
            check("https://example.com/post/SECOND_ID".equals(rules.get(0).apply("https://example.com/post/SECOND_ID?anything=REMOVED")),"Rule generalizes to a new post");
            click("Disable");check(RuleStore.find(getTargetContext(),"https://example.com/post/SECOND_ID")==null,"Disable rule");
            click("Undo last rule change");check(RuleStore.find(getTargetContext(),"https://example.com/post/SECOND_ID")!=null,"Undo restores rule");
            current.finish();
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("preview",false).commit();
            current=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->((MainActivity)current).onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://example.com/post/THIRD_ID?tracking=REMOVED")));
            waitForIdleSync();check("https://example.com/post/THIRD_ID".equals(shared.get()),"Saved rule used in normal share");
            SiteRule override=new SiteRule("test-override","Test override","www.instagram.com","/p/{id}","www.instagram.com","/p/{id}","www.instagram.com","/p/{id}","img_index","www.instagram.com",false,true);
            rules.add(0,override);RuleStore.save(getTargetContext(),rules);
            runOnMainSync(()->((MainActivity)current).onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://www.instagram.com/p/EXAMPLE?img_index=2&igsh=REMOVED")));
            waitForIdleSync();check("https://www.instagram.com/p/EXAMPLE?img_index=2".equals(shared.get()),"Custom rule overrides builtin");
            runOnMainSync(()->current.finish());
            current=startActivitySync(new Intent(getTargetContext(),BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            var webField=BrowserActivity.class.getDeclaredField("browser");webField.setAccessible(true);
            android.webkit.WebView web=(android.webkit.WebView)webField.get(current);
            AtomicBoolean safeSettings=new AtomicBoolean();
            runOnMainSync(()->{
                safeSettings.set(!web.getSettings().getAllowFileAccess()&&!web.getSettings().getAllowContentAccess()&&web.getSettings().getJavaScriptEnabled());
                web.getSettings().setBlockNetworkLoads(true);
                web.loadDataWithBaseURL("https://example.test/post/BROWSER_SAMPLE","<html><body>Synthetic post</body></html>","text/html","UTF-8","https://example.test/post/BROWSER_SAMPLE");
            });
            check(safeSettings.get(),"Browser permits login scripts but not local file access");
            AtomicReference<String> browserUrl=new AtomicReference<>();long deadline=System.currentTimeMillis()+5000;
            while(browserUrl.get()==null && System.currentTimeMillis()<deadline){runOnMainSync(()->{String url=web.getUrl();if(url!=null&&url.contains("BROWSER_SAMPLE"))browserUrl.set(url);});Thread.sleep(20);}
            check("https://example.test/post/BROWSER_SAMPLE".equals(browserUrl.get()),"Browser exposes final URL from synthetic page");
            java.util.concurrent.CountDownLatch cookieSet=new java.util.concurrent.CountDownLatch(1);
            runOnMainSync(()->android.webkit.CookieManager.getInstance().setCookie("https://example.test","linkclear_test=SESSION; Secure; SameSite=Lax",ok->cookieSet.countDown()));
            check(cookieSet.await(3,java.util.concurrent.TimeUnit.SECONDS),"Browser cookie set completes");
            runOnMainSync(()->current.finish());
            current=startActivitySync(new Intent(getTargetContext(),BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            AtomicBoolean cookieRetained=new AtomicBoolean();
            runOnMainSync(()->{String cookie=android.webkit.CookieManager.getInstance().getCookie("https://example.test");cookieRetained.set(cookie!=null&&cookie.contains("linkclear_test=SESSION"));android.webkit.CookieManager.getInstance().setCookie("https://example.test","linkclear_test=; Max-Age=0; Secure",null);});
            check(cookieRetained.get(),"Browser retains its own login cookies across opens");
            output.putString("stream","\n"+checks+" editor, integration and browser checks passed. Only synthetic pages and cookies; no recipient apps.\n");
        }catch(Throwable e){code=Activity.RESULT_CANCELED;output.putString("stream","\nRule editor failed after "+checks+" checks: "+e.getClass().getSimpleName()+(e instanceof AssertionError?" — "+e.getMessage():"")+"\n");}
        finally{
            prefs.edit().putString("rules",saved).putString("previous",previous).commit();
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("preview",preview).commit();removeMonitor(monitor);
            if(current!=null)runOnMainSync(()->current.finish());
        }
        finish(code,output);
    }
}
