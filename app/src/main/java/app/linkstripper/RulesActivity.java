package app.linkstripper;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.view.WindowManager;
import android.widget.*;
import java.util.*;

public final class RulesActivity extends Activity {
    private LinearLayout page;
    private EditText sample, resolved, desired, hosts;
    private TextView status;
    private int generation;
    private java.util.function.Consumer<String> browserResult;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newFixedThreadPool(2);
    @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); list(); }
    private void screen(String title) {
        generation++;
        browserResult=null;
        page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20),dp(16),dp(20),dp(24)); page.setBackgroundColor(0xfff5f7f3);
        ScrollView scroll=new ScrollView(this); scroll.addView(page); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        label(title,26);
    }
    private void list() {
        screen("Site rules");
        label("Custom rules run before the built-in parsers. The first matching enabled rule wins. Saved rules contain templates, not your example links.",16);
        button("Add a site from a link",()->wizard());
        button("Undo last rule change",()->{RuleStore.undo(this);list();});
        try {
            for(SiteRule rule:RuleStore.load(this)) {
                label(rule.name()+" · "+(rule.enabled()?"enabled":"disabled")+"\n"+rule.sourceHost()+rule.sourcePath(),16);
                button("Edit "+rule.name(),()->edit(rule,""));
                button(rule.enabled()?"Disable":"Enable",()->{
                    List<SiteRule> items=RuleStore.load(this); items.replaceAll(r->r.id().equals(rule.id())?copyEnabled(r,!r.enabled()):r); RuleStore.save(this,items);list();
                });
                button("Delete "+rule.name(),()->{List<SiteRule> items=RuleStore.load(this);items.removeIf(r->r.id().equals(rule.id()));RuleStore.save(this,items);list();});
            }
        } catch(Exception e) {
            label(e.getMessage(),16); button("Reset custom rules",()->{RuleStore.save(this,new ArrayList<>());list();});
        }
        label("Customize a built-in parser",20);
        label("These create editable overrides. Disable or delete an override to restore the built-in behavior. Add a rule from an example for other paths or host aliases.",16);
        preset("Instagram posts","www.instagram.com","/p/{id}/","https://www.instagram.com/p/EXAMPLE/?igsh=SYNTHETIC");
        preset("Instagram reels","www.instagram.com","/reel/{id}/","https://www.instagram.com/reel/EXAMPLE/?igsh=SYNTHETIC");
        preset("Threads posts","www.threads.com","/{author}/post/{id}/","https://www.threads.com/@example/post/EXAMPLE/?xmt=SYNTHETIC");
        preset("Twitter / X posts","x.com","/{author}/status/{id}/","https://x.com/example/status/123456789?s=20");
        button("Back to cleaner",this::finish);
    }
    private SiteRule copyEnabled(SiteRule r,boolean enabled) { return new SiteRule(r.id(),r.name(),r.sourceHost(),r.sourcePath(),r.targetHost(),r.targetPath(),r.outputHost(),r.outputPath(),r.keepQuery(),r.allowedHosts(),r.resolveFirst(),enabled); }
    private void preset(String name,String host,String path,String example) {
        button(name,()->edit(new SiteRule(UUID.randomUUID().toString(),name,host,path,host,path,host,path,"",host,false,true),example));
    }
    private void wizard() {
        screen("Build a rule from a link");
        label("1. Paste a sample. Resolve it anonymously, use the login browser, or continue without a lookup. Online lookup sends the sample URL to the allowed sites. Examples are not saved.",16);
        sample=field("Original shared URL","");
        hosts=field("Allowed lookup hosts (comma separated)","");
        button("Resolve online",()->inspect());
        button("Open login browser",()->{
            try { String url=SiteRule.url(sample.getText().toString()).toString(); startActivityForResult(new Intent(this,BrowserActivity.class).putExtra("url",url),101); } catch(Exception e) { error(e); }
        });
        button("Use pasted URL without lookup",()->{
            try { String url=SiteRule.url(sample.getText().toString()).toString(); resolved.setText(url); desired.setText(SiteRule.suggestion(url)); addHost(SiteRule.url(url).getHost()); status.setText("No online lookup performed."); } catch(Exception e) { error(e); }
        });
        status=label("",16);
        label("2. Review the final address and edit the desired clean URL. Suggestions remove known tracking keys but preserve unfamiliar keys that may identify the post.",16);
        resolved=field("Resolved URL (or paste the final address manually)","");
        desired=field("Desired clean URL","");
        button("Suggest cleanup of final URL",()->{try {desired.setText(SiteRule.suggestion(resolved.getText().toString()));}catch(Exception e){error(e);}});
        button("Build editable rule",()->{
            try {
                String a=SiteRule.url(sample.getText().toString()).toString(), b=SiteRule.url(resolved.getText().toString()).toString();
                addHost(SiteRule.url(a).getHost()); addHost(SiteRule.url(b).getHost());
                boolean lookup=!SiteRule.suggestion(a).equals(SiteRule.suggestion(b));
                edit(SiteRule.fromExamples(a,b,desired.getText().toString(),lookup,hosts.getText().toString()),a);
            } catch(Exception e) {error(e);}
        });
        button("Back to rules",this::list);
        android.text.TextWatcher sourceChanged=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){generation++;resolved.setText("");desired.setText("");status.setText("Source changed. Resolve again or use the pasted URL.");} public void afterTextChanged(android.text.Editable e){}};
        sample.addTextChangedListener(sourceChanged);
    }
    private void addHost(String host) { Set<String> values=SiteRule.csv(hosts.getText().toString());values.add(host);hosts.setText(String.join(",",values)); }
    private void inspect() {
        try {
            String source=SiteRule.url(sample.getText().toString()).toString(); addHost(SiteRule.url(source).getHost());
            Set<String> allowed=SiteRule.csv(hosts.getText().toString()); int request=++generation;
            status.setText("Looking up the address…");
            worker.execute(()->{
                try {
                    UrlInspector.Result result=UrlInspector.inspect(source,allowed);
                    runOnUiThread(()->{
                        if(isDestroyed() || request!=generation) return;
                        if(result.needsHost()!=null) {
                            status.setText("Redirect requests another host: "+result.needsHost()+". It has not been contacted.");
                            new AlertDialog.Builder(this).setMessage("Allow this lookup to contact "+result.needsHost()+"? The redirected address will be sent to that host.").setNegativeButton("Cancel",null).setPositiveButton("Allow",(d,w)->{addHost(result.needsHost());inspect();}).show();
                        } else {
                            resolved.setText(result.url()); desired.setText(SiteRule.suggestion(result.url()));
                            status.setText((source.equals(result.url())?"Address unchanged":"Address changed")+"\n"+String.join("\n→ ",result.steps()));
                        }
                    });
                } catch(Exception e) {runOnUiThread(()->{if(!isDestroyed() && request==generation) status.setText(e.getMessage()==null?"Lookup failed. Try the browser.":e.getMessage());});}
            });
        } catch(Exception e) {error(e);}
    }
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(request==103) {
            java.util.function.Consumer<String> handler=browserResult;browserResult=null;
            if(code==RESULT_OK && data!=null && handler!=null) handler.accept(data.getStringExtra("url"));
            return;
        }
        if(request==101 && code==RESULT_OK && data!=null && resolved!=null) {
            try {String url=SiteRule.url(data.getStringExtra("url")).toString();resolved.setText(url);desired.setText(SiteRule.suggestion(url));addHost(SiteRule.url(url).getHost());status.setText("Address taken from browser. Browser login and JavaScript steps cannot be replayed by an anonymous lookup; test the rule before saving.");}catch(Exception e){error(e);}
        }
    }
    private void edit(SiteRule rule,String example) {
        screen("Edit rule");
        label("A placeholder such as {id} matches one path segment and reuses it in the output. Review the guessed placeholders: a single example cannot prove which parts are always fixed.",16);
        Map<String,EditText> fields=new LinkedHashMap<>();
        fields.put("name",field("Rule name",rule.name()));
        fields.put("sourceHost",field("Match shared host (exact)",rule.sourceHost()));
        fields.put("sourcePath",field("Match shared path",rule.sourcePath()));
        android.widget.CheckBox lookup=new android.widget.CheckBox(this);lookup.setText("Resolve online before cleaning");lookup.setChecked(rule.resolveFirst());page.addView(lookup);
        fields.put("allowedHosts",field("Allowed lookup hosts",rule.allowedHosts()));
        fields.put("targetHost",field("Final address host",rule.targetHost()));
        fields.put("targetPath",field("Final address path pattern",rule.targetPath()));
        fields.put("outputHost",field("Output host",rule.outputHost()));
        fields.put("outputPath",field("Output path template",rule.outputPath()));
        fields.put("keepQuery",field("Keep these query keys (comma separated; blank removes all)",rule.keepQuery()));
        label("Query values are copied from each incoming/final link. Fragments are removed. Use exact hosts; add separate rules for aliases. No login cookies are used by automatic lookups.",16);
        EditText test=field("Test with a URL (not saved)",example);
        TextView feedback=label("Test this rule before saving. Check that the result still opens the same post.",16);
        Button save=button("Save tested rule",()->{});save.setEnabled(false);
        final SiteRule[] tested={null};
        Runnable invalidate=()->{generation++;tested[0]=null;save.setEnabled(false);};
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){invalidate.run();}public void afterTextChanged(android.text.Editable e){}};
        for(EditText f:fields.values()) f.addTextChangedListener(watcher);test.addTextChangedListener(watcher);lookup.setOnCheckedChangeListener((v,b)->invalidate.run());
        button("Test rule",()->{
            try {
                SiteRule candidate=new SiteRule(rule.id(),text(fields,"name"),text(fields,"sourceHost"),text(fields,"sourcePath"),text(fields,"targetHost"),text(fields,"targetPath"),text(fields,"outputHost"),text(fields,"outputPath"),text(fields,"keepQuery"),text(fields,"allowedHosts"),lookup.isChecked(),rule.enabled());
                String source=SiteRule.url(test.getText().toString()).toString();
                if(!copyEnabled(candidate,true).matches(source)) throw new IllegalArgumentException("Test URL does not match the shared host and path.");
                int request=++generation;save.setEnabled(false);feedback.setText("Testing…");
                worker.execute(()->{
                    try {String output=UrlInspector.apply(candidate,source);runOnUiThread(()->{if(!isDestroyed() && request==generation){feedback.setText("Result\n"+output+"\n\nSave stores only the rule fields above, not this sample.");tested[0]=candidate;save.setEnabled(true);}});}
                    catch(Exception e){runOnUiThread(()->{if(!isDestroyed() && request==generation)feedback.setText("Test failed. "+(e.getMessage()==null?"Check the rule or try the browser.":e.getMessage()));});}
                });
            }catch(Exception e){feedback.setText(e.getMessage());}
        });
        button("Test with login browser",()->{
            try {
                SiteRule candidate=new SiteRule(rule.id(),text(fields,"name"),text(fields,"sourceHost"),text(fields,"sourcePath"),text(fields,"targetHost"),text(fields,"targetPath"),text(fields,"outputHost"),text(fields,"outputPath"),text(fields,"keepQuery"),text(fields,"allowedHosts"),lookup.isChecked(),rule.enabled());
                String source=SiteRule.url(test.getText().toString()).toString();
                if(!copyEnabled(candidate,true).matches(source)) throw new IllegalArgumentException("Test URL does not match the shared host and path.");
                if(!candidate.resolveFirst()) throw new IllegalArgumentException("Use Test rule for an offline rule, or enable Resolve online before cleaning.");
                int request=++generation;save.setEnabled(false);
                browserResult=url->{
                    if(request!=generation)return;
                    try {String output=candidate.apply(url);feedback.setText("Browser-tested result\n"+output+"\n\nAnonymous lookup may still fail; the app will offer the browser when needed.");tested[0]=candidate;save.setEnabled(true);}
                    catch(Exception e){feedback.setText("The browser URL does not match this rule's final address pattern.");}
                };
                startActivityForResult(new Intent(this,BrowserActivity.class).putExtra("url",source),103);
            } catch(Exception e){feedback.setText(e.getMessage());}
        });
        save.setOnClickListener(v->{try {if(tested[0]==null)return;List<SiteRule> items=RuleStore.load(this);items.removeIf(r->r.id().equals(rule.id()));items.add(0,tested[0]);RuleStore.save(this,items);list();}catch(Exception e){error(e);}});
        button("Cancel",this::list);
    }
    private String text(Map<String,EditText> fields,String key){return fields.get(key).getText().toString().trim();}
    private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setPadding(0,dp(10),0,dp(8));page.addView(t);return t;}
    private EditText field(String name,String value){TextView label=label(name,16);EditText e=new EditText(this);e.setId(android.view.View.generateViewId());label.setLabelFor(e.getId());e.setText(value);e.setTextSize(16);e.setSaveEnabled(false);e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);page.addView(e);return e;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());page.addView(b);return b;}
    private void error(Exception e){Toast.makeText(this,e.getMessage()==null?"Could not complete this step.":e.getMessage(),Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();super.onDestroy();}
}
