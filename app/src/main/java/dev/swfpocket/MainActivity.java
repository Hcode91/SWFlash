package dev.swfpocket;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.*;
import android.widget.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.*;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://player.local/";
    /**
     * The request another app (BacklogQuest's home-screen tiles) sends to play a movie straight away:
     * the .swf file as the intent's data, with read access granted.
     */
    public static final String ACTION_LAUNCH_GAME = "com.hcode91.pocket.LAUNCH_GAME";
    private WebView web;
    private Uri movie;
    private String movieName;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff101821);
        getWindow().setNavigationBarColor(0xff101821);
        web = new WebView(this);
        // Android 15 draws every app behind the status and navigation bars (targetSdk 35), which put
        // the Open SWF row under the status bar. A frame padded by the bars keeps the page clear of
        // them; fullscreen playback is added over the whole window, so it still uses every pixel.
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xff101821);
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(frame);
        web.setBackgroundColor(0xff101821);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void openFile() { runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);
            }); }
        }, "Android");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customCallback = callback;
                addContentView(view, new android.view.ViewGroup.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(5894);
            }
            @Override public void onHideCustomView() { leaveFullscreen(); }
        });
        if (state != null) { String saved = state.getString("movie"); movieName = state.getString("name"); if (saved != null) movie = Uri.parse(saved); }
        else launchFrom(getIntent());
        web.setWebViewClient(new PlayerClient());
        web.loadUrl(ORIGIN);
        web.setDownloadListener((url, ua, disposition, mime, length) -> Toast.makeText(this, "Downloads are unavailable", Toast.LENGTH_SHORT).show());
    }
    private class PlayerClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
        @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return intercept(r.getUrl()); }
        @Override public void onPageFinished(WebView v, String url) { if (movie != null) loadMovie(); }
    }
    private WebResourceResponse intercept(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !"player.local".equals(uri.getHost())) return blocked();
        try {
            String path = uri.getPath();
            if ("/movie.swf".equals(path) && movie != null) return new WebResourceResponse("application/x-shockwave-flash", null, getContentResolver().openInputStream(movie));
            if (path == null || path.contains("..")) return blocked();
            String type = path.endsWith(".wasm") ? "application/wasm" : path.endsWith(".js") ? "application/javascript" : "text/html";
            return new WebResourceResponse(type, "UTF-8", getAssets().open("player/" + ("/".equals(path) ? "index.html" : path.substring(1))));
        } catch (Exception e) { return blocked(); }
    }
    private WebResourceResponse blocked() { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0])); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (launchFrom(intent)) loadMovie();
    }

    /** Takes the movie another app sent. The page plays it once loaded (onPageFinished). */
    private boolean launchFrom(Intent intent) {
        if (intent == null || !ACTION_LAUNCH_GAME.equals(intent.getAction()) || intent.getData() == null) return false;
        Uri sent = intent.getData();
        if (!isSwf(sent)) { Toast.makeText(this, "That file isn't a readable SWF", Toast.LENGTH_LONG).show(); return false; }
        movie = sent;
        movieName = displayName(sent);
        return true;
    }

    /** True when the file starts with an SWF signature (FWS, CWS or ZWS). */
    private boolean isSwf(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) return false;
            byte[] sig = new byte[3];
            // read() may return fewer bytes than asked for, so keep reading until there are three.
            int got = 0;
            while (got < 3) { int n = stream.read(sig, got, 3 - got); if (n < 0) return false; got += n; }
            return (sig[0] == 'F' || sig[0] == 'C' || sig[0] == 'Z') && sig[1] == 'W' && sig[2] == 'S';
        } catch (Exception e) { return false; }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) return cursor.getString(0);
        } catch (Exception ignored) {}
        return "Movie.swf";
    }

    private void loadMovie() { web.evaluateJavascript("window.openMovie(" + JSONObject.quote(movieName == null ? "Movie.swf" : movieName) + ")", null); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null) return;
        Uri selected = data.getData();
        if (selected == null || !isSwf(selected)) { Toast.makeText(this, "Choose a valid, readable SWF file", Toast.LENGTH_LONG).show(); return; }
        // Android keeps a limited number of persisted grants per app (128 on older versions), and
        // only the current movie needs one, so the previous movie's grant is given back.
        Uri previous = movie;
        movie = selected;
        try { getContentResolver().takePersistableUriPermission(movie, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
        if (previous != null && !previous.equals(selected)) {
            try { getContentResolver().releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
        }
        movieName = displayName(movie);
        loadMovie();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (movie != null) { out.putString("movie", movie.toString()); out.putString("name", movieName); } }
    private void leaveFullscreen() { if (customView == null) return; ((android.view.ViewGroup)customView.getParent()).removeView(customView); customView = null; web.setVisibility(View.VISIBLE); getWindow().getDecorView().setSystemUiVisibility(0); customCallback.onCustomViewHidden(); }
    @Override public void onBackPressed() { if (customView != null) leaveFullscreen(); else super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); web.evaluateJavascript("window.pauseMovie && window.pauseMovie()", null); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { web.removeJavascriptInterface("Android"); web.destroy(); super.onDestroy(); }
}
