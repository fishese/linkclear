package app.linkstripper;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status, result, updateStatus;
    private EditText input;
    private LinearLayout editor;
    private Button share, copy, retry, browserFallback;
    private SiteRule pendingRule;
    private String cleaned, pending, originalText, originalUrl;
    private ScrollView screen;
    private boolean relay;
    private void showScreen() { if (relay) { setContentView(screen); relay=false; } }
    private boolean working, automatic, forwarded, passthrough;
    private int generation;
    interface Resolver { String resolve(String url) throws Exception; }
    Resolver resolver = LinkResolver::resolve;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newFixedThreadPool(2);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        relay = this instanceof ShareActivity && !getSharedPreferences("MainActivity",0).getBoolean("preview",false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(24));
        content.setBackgroundColor(Color.rgb(245,247,243));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.addView(content); screen=scroll; if (!relay) setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom()); return insets;
        });
        content.addView(label("LinkClear", 30));
        content.addView(label("Share the post. Leave the tracking behind.", 16));
        divider(content);
        content.addView(sectionTitle("Clean a link"));
        status = label("Share a post to LinkClear, or paste a link below.", 16); content.addView(status);
        editor = new LinearLayout(this); editor.setOrientation(LinearLayout.VERTICAL); content.addView(editor);
        LinearLayout inputRow = new LinearLayout(this); inputRow.setGravity(android.view.Gravity.CENTER_VERTICAL); editor.addView(inputRow);
        input = new EditText(this); input.setHint("Paste a link to clean"); input.setMinLines(2);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSaveEnabled(false); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); inputRow.addView(input,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        ImageButton paste = new ImageButton(this);
        paste.setImageResource(R.drawable.ic_paste); paste.setContentDescription("Paste"); paste.setTooltipText("Paste");
        paste.setBackgroundResource(android.R.drawable.list_selector_background);
        paste.setPadding(dp(12),dp(12),dp(12),dp(12));
        inputRow.addView(paste,new LinearLayout.LayoutParams(dp(48),dp(48)));
        paste.setOnClickListener(v -> {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            ClipData clip=clipboard.getPrimaryClip();
            CharSequence text=clip!=null && clip.getItemCount()>0 ? clip.getItemAt(0).getText() : null;
            if(text!=null) { input.setText(text); input.setSelection(input.length()); }
            else Toast.makeText(this,"No text on clipboard",Toast.LENGTH_SHORT).show();
        });
        Button clean = button("Clean and share", editor);
        clean.setOnClickListener(v -> {
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
            process(input.getText().toString(), !getSharedPreferences("MainActivity",0).getBoolean("preview", false));
        });
        share = button("Share clean link", content); share.setEnabled(false); share.setOnClickListener(v -> forward());
        result = label("", 16); result.setTextIsSelectable(true); result.setSaveEnabled(false); content.addView(result);
        copy = button("Copy clean link", content); copy.setVisibility(View.GONE); copy.setOnClickListener(v -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(passthrough ? "Original content" : "Clean post link", cleaned));
            status.setText(passthrough ? "Original content copied" : "Clean link copied");
        });
        retry = button("Retry lookup", content); retry.setVisibility(View.GONE); retry.setOnClickListener(v -> resolvePending());
        browserFallback = button("Open in login browser", content); browserFallback.setVisibility(View.GONE);
        browserFallback.setOnClickListener(v -> { if(pending!=null) startActivityForResult(new Intent(this,BrowserActivity.class).putExtra("url",pending),102); });
        Switch preview = new Switch(this); preview.setText("Preview before opening share sheet");
        preview.setChecked(getSharedPreferences("MainActivity",0).getBoolean("preview", false));
        preview.setPadding(0,dp(16),0,dp(16)); content.addView(preview);
        preview.setOnCheckedChangeListener((v, checked) -> {
            getSharedPreferences("MainActivity",0).edit().putBoolean("preview", checked).apply();
            automatic = !checked;
        });
        content.addView(label("Short links are looked up automatically on the original site without signing in. That site receives the link token and your IP address. No link history or analytics is stored.", 14));
        divider(content);
        content.addView(sectionTitle("Site rules"));
        content.addView(label("Add support for another site or adjust how an existing link is cleaned.",14));
        Button rules=button("Site rules & browser",content);rules.setOnClickListener(v->startActivity(new Intent(this,RulesActivity.class)));
        divider(content);
        content.addView(sectionTitle("Updates"));
        Button updates=button("Check for updates",content);
        updateStatus=label("Installed version v"+installedVersion()+" · Checks only when tapped.",14); content.addView(updateStatus);
        updates.setOnClickListener(v->checkForUpdates(updates));
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { reset(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        if (state != null) forwarded = state.getBoolean("forwarded");
        handleIntent(getIntent(), forwarded);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        forwarded = false;
        handleIntent(intent, false);
    }

    private void handleIntent(Intent intent, boolean alreadyForwarded) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
        editor.setVisibility(View.GONE);
        String text = sharedText(intent);
        if (text == null) {
            reset(); showScreen(); editor.setVisibility(View.VISIBLE);
            status.setText("This share contains no web link. You can paste the post address below.");
            return;
        }
        process(text, !alreadyForwarded && !getSharedPreferences("MainActivity",0).getBoolean("preview", false));
    }

    private String sharedText(Intent intent) {
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null && !text.toString().trim().isEmpty()) return text.toString();
        ClipData clip = intent.getClipData();
        if (clip != null) {
            StringBuilder parts = new StringBuilder();
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = clip.getItemAt(i);
                // Never dereference content URIs or read attachments.
                if (i > 0) parts.append('\n');
                if (item.getText() != null) parts.append(item.getText());
                else if (item.getUri() != null && ("https".equals(item.getUri().getScheme()) || "http".equals(item.getUri().getScheme())))
                    parts.append(item.getUri());
            }
            if (parts.length() > 0) return parts.toString();
        }
        String html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT);
        if (html != null) return html.replace("&amp;", "&");
        return null;
    }

    private void reset() {
        generation++; cleaned = null; pending = null; pendingRule=null; working = false; passthrough = false;
        browserFallback.setVisibility(View.GONE);
        share.setText("Share clean link"); copy.setText("Copy clean link");
        share.setEnabled(false); copy.setVisibility(View.GONE); retry.setVisibility(View.GONE); result.setText("");
    }
    private void process(String text, boolean auto) {
        reset(); originalText=text; originalUrl=null; automatic = auto; forwarded = false;
        try {
            String url = LinkCleaner.extract(text); originalUrl=url;
            SiteRule custom;
            try { custom=RuleStore.find(this,url); } catch(IllegalStateException e) {showScreen();status.setText(e.getMessage());return;}
            if(custom!=null) {
                if(custom.resolveFirst()) {pending=url;pendingRule=custom;resolvePending();}
                else complete(custom.apply(url));
                return;
            }
            if (LinkCleaner.needsResolution(url)) {
                pending = url;
                resolvePending();
            } else complete(LinkCleaner.clean(url));
        } catch (IllegalArgumentException e) {
            passthrough = true;
            Toast.makeText(this, "Unrecognized link — sharing original content unchanged", Toast.LENGTH_LONG).show();
            complete(text);
        }
    }
    private void complete(String url) {
        cleaned = passthrough || originalUrl==null ? url : LinkCleaner.replaceUrl(originalText,originalUrl,url); pending = null; result.setText(cleaned);
        status.setText(passthrough ? "Unrecognized link • original content unchanged" : "Your clean post link is ready");
        share.setText(passthrough ? "Share original content" : "Share clean link");
        copy.setText(passthrough ? "Copy original content" : "Copy clean link");
        share.setEnabled(true); copy.setVisibility(View.VISIBLE); retry.setVisibility(View.GONE);
        browserFallback.setVisibility(View.GONE);
        if (automatic && !isFinishing()) forward();
    }
    private void resolvePending() {
        if (working || pending == null) return;
        working = true; retry.setVisibility(View.GONE); status.setText("Finding the original post…");
        String source = pending; SiteRule rule=pendingRule; int request = generation;
        worker.execute(() -> {
            String answer = null;
            try { answer = rule==null?resolver.resolve(source):UrlInspector.apply(rule,source); } catch (Exception ignored) {}
            String finalAnswer = answer;
            runOnUiThread(() -> {
                if (isDestroyed() || request != generation) return;
                working = false;
                if (finalAnswer != null) complete(finalAnswer);
                else {
                    passthrough=true;
                    Toast.makeText(this,"Could not resolve link — sharing original content unchanged",Toast.LENGTH_LONG).show();
                    complete(originalText);
                }
            });
        });
    }
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(request==102 && code==RESULT_OK && data!=null) {
            String finalUrl=data.getStringExtra("url");
            try {if(pendingRule!=null)complete(pendingRule.apply(finalUrl));else if(LinkCleaner.googleShare(originalUrl)) complete(LinkResolver.googleDestination(finalUrl)); else complete(LinkCleaner.clean(finalUrl));}
            catch(Exception e){status.setText("The selected browser URL does not match the saved rule. Edit it in Site rules.");}
        }
    }
    private void forward() {
        if (cleaned == null) return;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cleaned);
        Intent chooser = Intent.createChooser(send, passthrough ? "Share original content" : "Share clean post link");
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new android.content.ComponentName[]{new android.content.ComponentName(this, MainActivity.class),new android.content.ComponentName(this, ShareActivity.class)});
        try { startActivity(chooser); forwarded = true;
            if(!passthrough) Toast.makeText(this,"Link cleaned",Toast.LENGTH_SHORT).show();
            if(this instanceof ShareActivity) finish(); } catch (android.content.ActivityNotFoundException e) { showScreen(); status.setText("No sharing app is available. You can copy the clean link."); }
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("forwarded",forwarded); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
    private void checkForUpdates(Button button) {
        button.setEnabled(false); updateStatus.setText("Checking for updates…");
        String installed=installedVersion();
        worker.execute(() -> {
            String available=null;
            try { available=UpdateChecker.latestVersion(); } catch(Exception ignored) {}
            String latest=available;
            runOnUiThread(() -> {
                if(isDestroyed()) return;
                button.setEnabled(true);
                if(latest==null) { updateStatus.setText("Could not check for updates. Try again later."); return; }
                if(!UpdateChecker.isNewer(latest,installed)) {
                    updateStatus.setText("LinkClear is up to date (v"+installed+").");
                    return;
                }
                updateStatus.setText("LinkClear v"+latest+" is available.");
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Update available")
                    .setMessage("LinkClear v"+latest+" is available. Open the permanent download link?")
                    .setNegativeButton("Not now",null)
                    .setPositiveButton("Download",(dialog,which)->openDownload())
                    .show();
            });
        });
    }
    @SuppressWarnings("deprecation")
    private String installedVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException impossible) { return "0.0.0"; }
    }
    private void openDownload() {
        try { startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://linkclear.fishese.cc/download/"))); }
        catch(android.content.ActivityNotFoundException e) { updateStatus.setText("No browser is available to download the update."); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void divider(LinearLayout parent) {
        View line=new View(this); line.setBackgroundColor(Color.rgb(210,220,213));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1));
        params.setMargins(0,dp(20),0,dp(10)); parent.addView(line,params);
    }
    private TextView sectionTitle(String text) { TextView v=label(text,20); v.setTypeface(null,android.graphics.Typeface.BOLD); return v; }
    private TextView label(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(27,49,40)); v.setPadding(0,dp(8),0,dp(8)); return v; }
    private Button button(String text, LinearLayout parent) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); parent.addView(b); return b; }
}
