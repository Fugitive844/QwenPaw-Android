package cn.qwenpaw.android;

import okhttp3.HttpUrl;

public final class ServerAddress {
    private ServerAddress() {}
    public static String create(String scheme, String host, String port, String path) {
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("请选择 HTTP 或 HTTPS");
        if (host.trim().isEmpty() || host.contains("/") || host.contains("@") || host.contains("?") || host.contains("#"))
            throw new IllegalArgumentException("主机只填写 IP 或域名");
        HttpUrl.Builder b = new HttpUrl.Builder().scheme(scheme).host(host.trim());
        if (!port.trim().isEmpty()) {
            int p; try { p=Integer.parseInt(port.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("端口须为 1–65535"); }
            if(p<1 || p>65535) throw new IllegalArgumentException("端口须为 1–65535"); b.port(p);
        }
        String prefix = path.trim().replaceAll("^/+|/+$", "");
        if (prefix.contains("?") || prefix.contains("#") || prefix.contains("..")) throw new IllegalArgumentException("路径前缀不能包含查询参数或 ..");
        if (!prefix.isEmpty()) b.addPathSegments(prefix);
        return b.build().toString().replaceAll("/+$", "");
    }
}
