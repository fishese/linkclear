package app.linkstripper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;

/** WebView is created only in this explicit, optional activity. No JavaScript bridge or cookie export. */
public final class BrowserActivity extends Activity {
    private WebView browser;
    private EditText address;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,40,20,24); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)-> { v.setPadding(20,i.getSystemWindowInsetTop()+16,20,i.getSystemWindowInsetBottom()+16); return i; });
        TextView info=new TextView(this); info.setText("Browser login stays in this browser. Use this URL when the post is open."); root.addView(info);
        address=new EditText(this); address.setSingleLine(true); address.setSaveEnabled(false); address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI); root.addView(address);
        LinearLayout buttons=new LinearLayout(this); root.addView(buttons);
        Button go=button("Go",buttons); go.setOnClickListener(v->load(address.getText().toString()));
        Button use=button("Use this URL",buttons); use.setOnClickListener(v->{ try { String url=SiteRule.url(browser.getUrl()).toString(); setResult(RESULT_OK,new Intent().putExtra("url",url)); finish(); } catch(Exception e) { info.setText("Open an HTTP(S) post page first."); } });
        Button external=button("External",buttons); external.setOnClickListener(v->{try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SiteRule.url(address.getText().toString()).toString()))); } catch(Exception e) { info.setText("No external browser is available for this address."); }});
        Button clear=button("Clear browser login & data",root); clear.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setMessage("Sign out of all sites in LinkClear's browser and clear its cookies and storage?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{
            browser.stopLoading(); browser.loadUrl("about:blank"); CookieManager.getInstance().removeAllCookies(done->CookieManager.getInstance().flush()); WebStorage.getInstance().deleteAllData(); browser.clearCache(true); browser.clearHistory(); info.setText("Browser data cleared.");
        }).show());
        browser=new WebView(this); root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings settings=browser.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) { address.setText(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                try { return !"https".equalsIgnoreCase(SiteRule.url(request.getUrl().toString()).getScheme()); } catch(Exception e) { return true; }
            }
            @Override public void onPageFinished(WebView view,String url) { address.setText(url); }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error) { handler.cancel(); info.setText("Certificate error. This page was not opened."); }
        });
        browser.setWebChromeClient(new WebChromeClient() { @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); } });
        load(getIntent().getStringExtra("url"));
        if(android.os.Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(browser.canGoBack())browser.goBack();else finish();});
    }
    private void load(String url) { try { String safe=SiteRule.url(url).toString(); if(!safe.startsWith("https://")) throw new Exception(); address.setText(safe); browser.loadUrl(safe); } catch(Exception e) { Toast.makeText(this,"Enter a public HTTPS URL",Toast.LENGTH_LONG).show(); } }
    private Button button(String text,LinearLayout parent) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); if(parent.getOrientation()==LinearLayout.HORIZONTAL)parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));else parent.addView(b); return b; }
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ uses the native callback registered above; this handles older devices.
    @Override public void onBackPressed() { if(browser.canGoBack()) browser.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if(browser!=null) { ((android.view.ViewGroup)browser.getParent()).removeView(browser); browser.stopLoading(); browser.destroy(); } super.onDestroy(); }
}
