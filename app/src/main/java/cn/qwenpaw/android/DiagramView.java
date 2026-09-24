package cn.qwenpaw.android;

import android.content.Context;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;

/** Only local diagram assets run here; the application and chat UI are native Android views. */
public final class DiagramView extends WebView {
    public DiagramView(Context context,String source) {
        super(context);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowFileAccess(false);getSettings().setAllowContentAccess(false);
        getSettings().setDomStorageEnabled(false);getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        getSettings().setSupportZoom(true);getSettings().setBuiltInZoomControls(true);getSettings().setDisplayZoomControls(false);
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(context)).build();
        setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                WebResourceResponse local=loader.shouldInterceptRequest(request.getUrl());
                return local!=null?local:new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public void onPageFinished(WebView view,String url){
                if(url.equals("https://appassets.androidplatform.net/assets/diagram.html"))
                    evaluateJavascript("renderDiagram("+JSONObject.quote(source).replace("\u2028","\\u2028").replace("\u2029","\\u2029")+")",null);
            }
        });
        loadUrl("https://appassets.androidplatform.net/assets/diagram.html");
    }
}
