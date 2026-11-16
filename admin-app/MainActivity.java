package com.aurevia.admin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Aurevia Admin — a thin, hardened shell around the bundled control panel.
 *
 * The panel itself is local (assets/admin.html). Only the Aurevia API host is allowed inside the
 * WebView; every other link opens in the real browser, so this app can never become a generic
 * browser for a logged-in admin session.
 */
public class MainActivity extends Activity {

    private static final String PANEL = "file:///android_asset/admin.html";
    private static final String API_HOST = "aurevia.rixxstore.my.id";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.parseColor("#0b0b10"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSaveFormData(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " AureviaAdmin/1.0");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if ("file".equals(scheme) || ("https".equals(scheme) && API_HOST.equals(host))) {
                    return false;
                }
                openOutside(uri);
                return true;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String host = uri.getHost() == null ? "" : uri.getHost();
                if ("file".equals(scheme) || ("https".equals(scheme) && API_HOST.equals(host))) {
                    return false;
                }
                openOutside(uri);
                return true;
            }
        });

        setContentView(web);
        if (state == null) web.loadUrl(PANEL); else web.restoreState(state);
    }

    private void openOutside(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
            // No browser installed — stay in the panel, nothing else to do.
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
