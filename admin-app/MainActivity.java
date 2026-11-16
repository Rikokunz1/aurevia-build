package app.aurevia.admin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Aurevia Admin — hardened WebView shell around the bundled control panel.
 *
 * IMPORTANT: the package name must stay {@code app.aurevia.admin}; it is the same string the
 * manifest declares and the one apksigner/aapt verify. A mismatch here makes Android throw
 * ActivityNotFoundException on launch (install fine, crash on open) — that is exactly the bug
 * that shipped in v1.0.0 (vc1) and why the launcher icon also needs to come from this build.
 */
public class MainActivity extends Activity {

    private static final String PANEL = "file:///android_asset/admin.html";
    private static final String API_HOST = "aurevia.rixxstore.my.id";
    private WebView web;
    private FrameLayout root;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        root = new FrameLayout(this);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.parseColor("#0b0b10"));

        web = new WebView(this);
        web.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.parseColor("#0b0b10"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        // The panel is one self-contained file; nothing else may be read from disk.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSaveFormData(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " AureviaAdmin/1.1");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return route(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // A blank/aborted first paint is the second most common "cannot open" symptom.
                if (view.getContentHeight() == 0) showProblem("Panel kosong setelah dimuat. Tutup lalu buka lagi.");
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showProblem("Gagal memuat panel (" + errorCode + "). " + description);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                // Keep the console visible in logcat so a blank screen is diagnosable.
                System.out.println("[AureviaAdmin] " + message.message() + " @" + message.lineNumber());
                return true;
            }
        });

        root.addView(web);
        setContentView(root);
        try {
            if (state == null) web.loadUrl(PANEL); else web.restoreState(state);
        } catch (Throwable error) {
            showProblem("WebView tidak bisa dibuka di perangkat ini: " + error);
        }
    }

    /** true = handled here (blocked); false = let the WebView load it. */
    private boolean route(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        if ("file".equals(scheme) && uri.toString().startsWith("file:///android_asset/")) return false;
        if ("https".equals(scheme) && API_HOST.equals(host)) return false;
        if ("about".equals(scheme)) return false;
        openOutside(uri);
        return true;
    }

    private void openOutside(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
            // No browser installed — stay in the panel, nothing else to do.
        }
    }

    /** Visible, non-crashing error surface instead of a black screen or a force close. */
    private void showProblem(String message) {
        if (root == null) return;
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(Color.parseColor("#f5f5fa"));
        view.setTextSize(15f);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(48, 48, 48, 48);
        view.setBackgroundColor(Color.parseColor("#141019"));
        if (web != null) root.removeView(web);
        root.addView(view);
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
