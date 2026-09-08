package app.linkstripper;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.ClipData;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.util.concurrent.atomic.AtomicReference;

/** Device regression checks. Only synthetic URLs; chooser intercepted, no recipient apps opened. */
public class ShareFlowTest extends Instrumentation {
    private int checks;
    private Activity activity;
    private final AtomicReference<String> shared = new AtomicReference<>();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle output = new Bundle();
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (Intent.ACTION_CHOOSER.equals(intent.getAction())) {
                    Intent payload = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    shared.set(payload.getStringExtra(Intent.EXTRA_TEXT));
                    return new ActivityResult(Activity.RESULT_CANCELED, null);
                }
                return null;
            }
        };
        addMonitor(monitor);
        boolean original = getTargetContext().getSharedPreferences("MainActivity",0).getBoolean("preview",false);
        try {
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("preview",false).commit();
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://instagram.com/p/SYNTHETIC/?stkn=SYNTHETIC_TOKEN&igsh=REMOVED"));
            check("https://www.instagram.com/p/SYNTHETIC/".equals(shared.get()), "Incoming share must automatically forward clean URL");
            shared.set(null);
            Intent clipIntent = new Intent(Intent.ACTION_SEND).setType("text/plain");
            clipIntent.setClipData(ClipData.newPlainText("Post", "https://threads.com/@example/post/SYNTHETIC/?xmt=REMOVED"));
            deliver(clipIntent);
            check("https://www.threads.com/@example/post/SYNTHETIC/".equals(shared.get()), "ClipData share and reused activity must work");
            shared.set(null);
            activity.getPreferences(0).edit().putBoolean("preview",true).commit();
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://instagram.com/reel/SYNTHETIC/?igsh=REMOVED"));
            check(shared.get() == null, "Preview must not auto-forward");
            runOnMainSync(() -> {
                Button button = findShare(activity.getWindow().getDecorView());
                if (button == null || !button.isEnabled() || !button.isShown()) throw new AssertionError("Visible enabled share button required");
                button.performClick();
            });
            check("https://www.instagram.com/reel/SYNTHETIC/".equals(shared.get()), "Preview share button must forward clean URL");
            shared.set(null);
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://threads.com/"));
            check(shared.get() == null, "Unrecognized content must respect preview");
            runOnMainSync(() -> findShare(activity.getWindow().getDecorView()).performClick());
            check("https://threads.com/".equals(shared.get()), "Unrecognized homepage must pass through unchanged");
            shared.set(null);
            activity.getPreferences(0).edit().putBoolean("preview",false).commit();
            String unknown = "Original caption\nhttps://example.org/post?keep=VALUE#fragment\n";
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,unknown));
            check(unknown.equals(shared.get()), "Unknown link must preserve complete original content");
            shared.set(null);
            String multiple = "https://x.com/example/status/123?s=19 https://example.org/";
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,multiple));
            check(multiple.equals(shared.get()), "Multiple URLs must pass through without partial cleaning");
            shared.set(null);
            Intent unknownClip = new Intent(Intent.ACTION_SEND).setType("text/plain");
            unknownClip.setClipData(ClipData.newPlainText("Post",unknown));
            deliver(unknownClip);
            check(unknown.equals(shared.get()), "ClipData passthrough must not append whitespace");
            shared.set(null);
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://twitter.com/example/status/123456789?s=19&t=SYNTHETIC"));
            check("https://x.com/example/status/123456789/".equals(shared.get()), "Twitter must clean and automatically forward");
            shared.set(null);
            runOnMainSync(() -> ((MainActivity)activity).resolver = url -> "https://www.threads.com/@example/post/SYNTHETIC/");
            activity.getPreferences(0).edit().putBoolean("preview",false).commit();
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://threads.com/share/SYNTHETIC"));
            long until = System.currentTimeMillis() + 3000;
            while (shared.get() == null && System.currentTimeMillis() < until) { Thread.sleep(20); waitForIdleSync(); }
            check("https://www.threads.com/@example/post/SYNTHETIC/".equals(shared.get()), "Short link must resolve and auto-forward without taps");
            shared.set(null);
            activity.getPreferences(0).edit().putBoolean("preview",true).commit();
            deliver(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://threads.com/share/SYNTHETIC"));
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            until = System.currentTimeMillis() + 3000;
            while (!ready.get() && System.currentTimeMillis() < until) {
                runOnMainSync(() -> ready.set(findShare(activity.getWindow().getDecorView()).isEnabled())); Thread.sleep(20);
            }
            check(ready.get() && shared.get() == null, "Short-link preview must expose Share without opening chooser");
            activity.getPreferences(0).edit().putBoolean("preview",false).commit();
            shared.set(null);
            deliver(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"Heading\n(https://instagram.com/p/SYNTHETIC?igsh=REMOVE).\nSource: Example  "));
            check("Heading\n(https://www.instagram.com/p/SYNTHETIC/).\nSource: Example  ".equals(shared.get()),"Captions and punctuation must survive cleaning");
            shared.set(null);
            runOnMainSync(()->((MainActivity)activity).resolver=url->"https://example.org/article?id=123");
            deliver(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"Heading\nhttps://share.google/SYNTHETIC\nSource: Example"));
            until=System.currentTimeMillis()+3000;
            while(shared.get()==null && System.currentTimeMillis()<until) {Thread.sleep(20);waitForIdleSync();}
            check("Heading\nhttps://example.org/article?id=123\nSource: Example".equals(shared.get()),"Google lookup preserves surrounding text and destination query");
            runOnMainSync(()->activity.finish());
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{
                ((android.content.ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Test","Synthetic paste"));
                findPaste(activity.getWindow().getDecorView()).performClick();
            });
            check(findInput(activity.getWindow().getDecorView()).getText().toString().equals("Synthetic paste"),"Paste button reads clipboard on tap");
            runOnMainSync(()->activity.finish());
            // An absent setting must forward with no attached app editor.
            getTargetContext().getSharedPreferences("MainActivity",0).edit().remove("preview").commit();
            activity=startActivitySync(new Intent(getTargetContext(),ShareActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(findShare(activity.getWindow().getDecorView())==null,"Default share entry must not attach editor");
            shared.set(null);
            deliver(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"https://x.com/example/status/123?s=20"));
            check("https://x.com/example/status/123/".equals(shared.get()) && activity.isFinishing(),"Default relay must forward and finish");
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("preview",true).commit();
            activity=startActivitySync(new Intent(getTargetContext(),ShareActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            shared.set(null);
            deliver(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,"https://x.com/example/status/123?s=19"));
            check(shared.get()==null && findShare(activity.getWindow().getDecorView())!=null,"Relay preview must show editor and wait");
            runOnMainSync(()->findShare(activity.getWindow().getDecorView()).performClick());
            check(activity.isFinishing(),"Preview relay must close after forwarding");
            output.putString("stream", "\n" + checks + " share-flow checks passed. No recipient app opened.\n");
            finish(Activity.RESULT_OK, output);
        } catch (Throwable error) {
            output.putString("stream", "\nShare-flow regression failed after " + checks + " checks: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, output);
        } finally {
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("preview",original).commit();
            removeMonitor(monitor);
        }
    }
    private View findPaste(View v) {
        if("Paste".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) {View found=findPaste(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private Button findButton(View v,String text) {
        if(v instanceof Button && text.contentEquals(((Button)v).getText())) return (Button)v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) {Button b=findButton(((ViewGroup)v).getChildAt(i),text);if(b!=null)return b;}
        return null;
    }
    private android.widget.EditText findInput(View v) {
        if(v instanceof android.widget.EditText) return (android.widget.EditText)v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) {android.widget.EditText e=findInput(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}
        return null;
    }
    private void deliver(Intent intent) { runOnMainSync(() -> ((MainActivity)activity).onNewIntent(intent)); waitForIdleSync(); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private Button findShare(View view) {
        if (view instanceof Button && ("Share clean link".contentEquals(((Button)view).getText()) || "Share original content".contentEquals(((Button)view).getText()))) return (Button)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) { Button b=findShare(((ViewGroup)view).getChildAt(i)); if(b!=null)return b; }
        return null;
    }
}
