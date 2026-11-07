package dev.swfpocket;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.*;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://player.local/";
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
        setContentView(web);
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
    private void loadMovie() { web.evaluateJavascript("window.openMovie(" + JSONObject.quote(movieName == null ? "Movie.swf" : movieName) + ")", null); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null) return;
        Uri selected = data.getData();
        try (InputStream stream = getContentResolver().openInputStream(selected)) {
            byte[] sig = new byte[3];
            if (stream == null || stream.read(sig) != 3 || (sig[0] != 'F' && sig[0] != 'C' && sig[0] != 'Z') || sig[1] != 'W' || sig[2] != 'S') throw new IOException("Invalid SWF");
            movie = selected; movieName = "Movie.swf";
            try { getContentResolver().takePersistableUriPermission(movie, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
            try (Cursor cursor = getContentResolver().query(movie, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (cursor != null && cursor.moveToFirst()) movieName = cursor.getString(0); }
            loadMovie();
        } catch (Exception e) { Toast.makeText(this, "Choose a valid, readable SWF file", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (movie != null) { out.putString("movie", movie.toString()); out.putString("name", movieName); } }
    private void leaveFullscreen() { if (customView == null) return; ((android.view.ViewGroup)customView.getParent()).removeView(customView); customView = null; web.setVisibility(View.VISIBLE); getWindow().getDecorView().setSystemUiVisibility(0); customCallback.onCustomViewHidden(); }
    @Override public void onBackPressed() { if (customView != null) leaveFullscreen(); else super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); web.evaluateJavascript("window.pauseMovie && window.pauseMovie()", null); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { web.removeJavascriptInterface("Android"); web.destroy(); super.onDestroy(); }
}
