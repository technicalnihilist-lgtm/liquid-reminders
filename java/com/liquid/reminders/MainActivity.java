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
    /* a permission request is outstanding between requestPermissions() and its answer, and the
       activity is resumed in between; the hint is held back until the answer is in */
    private boolean askingPermission = false;
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
        /* the page keeps its reminders in localStorage and opens nothing else: the WebSQL
           database is never used, so it is not given a file of its own in app_webview/databases */
        s.setDatabaseEnabled(false);
        /* the page is a single local asset and nothing in it is fetched over a network, so the
           WebView's HTTP cache has never had anything worth keeping: LOAD_NO_CACHE stops it
           being written to at all, and whatever an earlier version left behind is dropped once
           per launch (localStorage, which the reminders live in, is not touched by this) */
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.clearCache(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        /* the page loads nothing out of a file:// URL and opens no window of its own */
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        /* the two sheets are the only forms, both onsubmit="return false": nothing is ever
           submitted, so nothing is worth remembering for autofill */
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(textZoom());
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

    /* The user's own text-size setting is not ours to override: the page is rendered at the
       system font scale (Configuration.fontScale, the Android "Font size" setting) instead of
       being pinned to 100%. The scale is held inside the range the layout was checked against,
       so an unusual OEM value cannot shrink the text to nothing or blow the layout apart. */
    private int textZoom() {
        float fs = getResources().getConfiguration().fontScale;
        if (!(fs > 0f)) fs = 1f;
        int zoom = Math.round(fs * 100f);
        return Math.max(50, Math.min(200, zoom));
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
            askingPermission = true;
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    /* The dialog has been answered. Granted or refused, the answer is recorded, so that a later
       "not allowed" can be told apart from "not asked yet", and the page is told at once rather
       than on the next launch. */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != 1) return;
        askingPermission = false;
        ReminderScheduler.markAsked(this);
        hintIfNotificationsRefused();
    }

    /* A reminder that cannot reach the tray fails in silence: the alarm fires, notify() throws
       or is dropped, and the app looks like it simply forgot. The page's own toast is the one
       place it can say so without adding anything to the design, and it shows the line once
       per install. Below Android 13 there is no permission to ask for — notifications are
       switched off in system Settings, and coming back to the app is when that is noticed. */
    private void hintIfNotificationsRefused() {
        if (askingPermission) return;
        if (web == null) return;
        if (!ReminderScheduler.notificationsRefused(this)) return;
        web.post(new Runnable() {
            @Override
            public void run() {
                if (web != null) web.evaluateJavascript(
                        "(function(){try{if(window.LiquidNotifyHint)window.LiquidNotifyHint();}catch(e){}})()",
                        null);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* exact alarms are allowed or refused in system Settings, so coming back to the app is
           the first moment the answer can have changed: pending alarms armed while it was
           refused are rebuilt here, exact */
        ReminderScheduler.rearmIfExactAlarmsGranted(this);
        /* the notification channel's importance is the user's to change, in system Settings, and
           a channel that was turned down there stays down for good: it is put back to High here */
        ReminderScheduler.ensureChannel(this);
        /* and if notifications are refused, say so once: the tray would otherwise stay empty
           with nothing on screen to explain it */
        hintIfNotificationsRefused();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLightBars();
        /* a font-size change arrives here instead of recreating the activity, so the new scale
           has to be pushed into the already-loaded page */
        if (web != null) web.getSettings().setTextZoom(textZoom());
    }

    @Override
    public void onBackPressed() {
        /* Back closes whichever sheet is up before it leaves the app. Both sheets share one
           scrim, so asking the page only "is the scrim open" said nothing about which one was
           up and Back was a no-op while Settings was open; the page now closes the open sheet
           itself (window.LiquidBack) and answers true only when it closed something. */
        web.evaluateJavascript(
                "(function(){try{return !!(window.LiquidBack&&window.LiquidBack());}catch(e){return false;}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!"true".equals(value)) finish();
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
