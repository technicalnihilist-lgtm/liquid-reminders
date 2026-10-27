package com.liquid.reminders;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView web;
    private int lastTop = 0;
    private int lastBottom = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        setLightBars();

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applyInsets();
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        if (Build.VERSION.SDK_INT >= 30) {
            web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    lastTop = bars.top;
                    lastBottom = Math.max(bars.bottom, ime.bottom);
                    applyInsets();
                    return insets;
                }
            });
        }

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        ensureNotifPermission();
    }

    private void applyInsets() {
        if (web == null) return;
        float d = getResources().getDisplayMetrics().density;
        if (d <= 0f) d = 1f;
        final int top = Math.round(lastTop / d);
        final int bottom = Math.round(lastBottom / d);
        final String js = "document.documentElement.style.setProperty('--sat','" + top
                + "px');document.documentElement.style.setProperty('--sab','" + bottom + "px');";
        web.post(new Runnable() {
            @Override
            public void run() {
                if (web != null) web.evaluateJavascript(js, null);
            }
        });
    }

    private void setLightBars() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private void ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLightBars();
    }

    @Override
    public void onBackPressed() {
        // If the add-reminder sheet is open, close it first; otherwise leave the app.
        web.evaluateJavascript(
                "(function(){var s=document.getElementById('scrim');return !!(s&&s.classList.contains('open'));})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if ("true".equals(value)) {
                            web.evaluateJavascript(
                                    "document.getElementById('cancel').click()", null);
                        } else {
                            finish();
                        }
                    }
                });
    }

    private class Bridge {
        @JavascriptInterface
        public void schedule(long id, String title, String notes, long due) {
            ReminderScheduler.schedule(MainActivity.this, id,
                    title == null ? "" : title,
                    notes == null ? "" : notes, due);
        }

        @JavascriptInterface
        public void cancel(long id) {
            ReminderScheduler.cancel(MainActivity.this, id);
        }

        @JavascriptInterface
        public void resync(String json) {
            ReminderScheduler.resync(MainActivity.this, json);
        }

        @JavascriptInterface
        public void askNotifications() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ensureNotifPermission();
                }
            });
        }

        @JavascriptInterface
        public long now() {
            return System.currentTimeMillis();
        }
    }
}
