package cn.qwenpaw.android;

import okhttp3.*;
import okio.BufferedSink;
import org.json.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Immutable account + workspace binding. No request can change identity mid-flight. */
public final class ApiClient {
    public static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    public final String base, token, agent;
    private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS)
        .readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private static final OkHttpClient STREAM=HTTP.newBuilder().readTimeout(70,TimeUnit.SECONDS).build();
    public ApiClient(String base,String token,String agent) { this.base=base;this.token=token;this.agent=agent; }
    public static String enc(String s) { try {return java.net.URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);} }
    public String url(String path) { return base+"/api"+path; }
    private Request.Builder request(String url) {
        Request.Builder b=new Request.Builder().url(url).header("Accept","application/json");
        if(sameOrigin(HttpUrl.get(base),HttpUrl.get(url))) {
            if(!token.isEmpty()) b.header("Authorization","Bearer "+token);
            b.header("X-Agent-Id",agent);
        }
        return b;
    }
    public static boolean sameOrigin(HttpUrl a,HttpUrl b) { return a.scheme().equals(b.scheme()) && a.host().equals(b.host()) && a.port()==b.port(); }
    public Object call(String method,String path,Object body) throws IOException {
        return callWithClient(HTTP,method,path,body);
    }
    /** Optional status reads must never hold a completed management operation for 90 seconds. */
    public Object optionalStatus(String path) throws IOException {
        return callWithClient(HTTP.newBuilder().callTimeout(5,TimeUnit.SECONDS).build(),"GET",path,null);
    }
    public JSONObject saveRunningConfig(JSONObject body) throws IOException {
        // A backend change can reload memory. Console allows ten minutes for the same operation.
        Object result=callWithClient(HTTP.newBuilder().readTimeout(10,TimeUnit.MINUTES).build(),"PUT","/workspace/running-config",body);
        if(!(result instanceof JSONObject))throw new IOException("服务器返回了无效的运行配置");
        return (JSONObject)result;
    }
    private Object callWithClient(OkHttpClient client,String method,String path,Object body) throws IOException {
        RequestBody data=method.equals("GET") || method.equals("HEAD") ? null : RequestBody.create(body==null?"":body.toString(),JSON);
        try(Response response=client.newCall(request(url(path)).method(method,data).build()).execute()) {
            check(response); String raw=response.body()==null?"":response.body().string();
            return raw.isEmpty()?new JSONObject():Json.parse(raw);
        }
    }
    public Call stream(JSONObject body) { return STREAM.newCall(request(url("/console/chat")).header("Accept","text/event-stream").post(RequestBody.create(body.toString(),JSON)).build()); }
    public JSONObject stopChat(String chatId) throws IOException {
        Object result=callWithClient(HTTP.newBuilder().callTimeout(20,TimeUnit.SECONDS).build(),"POST","/console/chat/stop?chat_id="+enc(chatId),null);
        if(!(result instanceof JSONObject value) || !(value.opt("stopped") instanceof Boolean))throw new IOException("停止结果未确认");
        return value;
    }
    public void consume(Call call,SseReader.Sink sink) throws IOException {
        try(Response response=call.execute()) {
            check(response);
            if(response.body()==null || !response.header("Content-Type","").contains("text/event-stream")) throw new IOException("服务器未返回 SSE 流，请检查地址和反向代理");
            SseReader.read(response.body().charStream(),sink);
        }
    }
    public JSONObject upload(String path,File file,String name,String mime) throws IOException {
        MultipartBody body=new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file",name,RequestBody.create(file,MediaType.parse(mime==null?"application/octet-stream":mime))).build();
        try(Response r=HTTP.newCall(request(url(path)).post(body).build()).execute()) {
            check(r); return Json.object(Json.parse(r.body().string()));
        }
    }
    public String fileUrl(String raw) {
        if(raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        if(raw.startsWith("/api/")) return base+raw;
        if(raw.startsWith("/files/")) return url(raw);
        if(raw.startsWith("file://")) raw=raw.substring(7);
        return url("/files/preview/"+enc(raw.replaceFirst("^/+", "")));
    }
    public void download(String raw,OutputStream out) throws IOException {
        String target=fileUrl(raw);
        try(Response r=HTTP.newCall(request(target).build()).execute()) {
            check(r); if(r.body()==null) throw new IOException("文件内容为空");
            try(InputStream in=r.body().byteStream()) { byte[] b=new byte[32768]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); }
        }
    }
    public void downloadBounded(String raw,OutputStream out,long limit) throws IOException {
        try(Response r=HTTP.newCall(request(fileUrl(raw)).build()).execute()) {
            check(r);if(r.body()==null)throw new IOException("文件内容为空");
            long length=r.body().contentLength();if(length>limit)throw new FileTooLargeException();
            try(InputStream in=r.body().byteStream()) {
                byte[] b=new byte[32768];int n;long total=0;
                while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new FileTooLargeException();out.write(b,0,n);}
            }
        }
    }
    public static final class FileTooLargeException extends IOException {
        FileTooLargeException(){super("文件超过预览大小限制");}
    }
    public static void check(Response r) throws IOException {
        if(r.isSuccessful()) return;
        String detail=r.body()==null?"":r.body().string();
        Object parsed=Json.parse(detail); Object structured=parsed instanceof JSONObject o&&o.has("detail")?o.opt("detail"):parsed;
        if(parsed instanceof JSONObject) detail=Json.text(structured);
        if(detail.length()>700) detail=detail.substring(0,700);
        String label=switch(r.code()) {
            case 401 -> "登录已过期，请重新登录";
            case 403 -> "没有执行此操作的权限";
            case 409 -> "会话状态冲突，请刷新后重试";
            case 423 -> "工作区正在准备，请稍后重试";
            case 413 -> "文件超过服务器大小限制";
            case 429 -> "请求过于频繁，请稍后重试";
            case 502,503,504 -> "服务暂不可用，请稍后重试";
            default -> "请求失败";
        };
        throw new ApiException(r.code(),label+" ("+r.code()+")"+(detail.isEmpty()?"":"\n"+detail),structured);
    }
    public static final class ApiException extends IOException {
        public final int status;
        public final Object detail;
        ApiException(int status,String message) { this(status,message,null); }
        ApiException(int status,String message,Object detail) { super(message);this.status=status;this.detail=detail; }
    }
}
