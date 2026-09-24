package cn.qwenpaw.android;

import java.io.*;

/** SSE framing independent of HTTP chunk boundaries, with comments and multiline data. */
public final class SseReader {
    public interface Sink { void event(String data) throws IOException; }
    public static void read(Reader input, Sink sink) throws IOException {
        BufferedReader reader=new BufferedReader(input); String line; StringBuilder data=new StringBuilder();
        boolean first=true;
        while((line=reader.readLine())!=null) {
            if(first) { line=line.replaceFirst("^\uFEFF", ""); first=false; }
            if(line.isEmpty()) { if(dispatch(data,sink)) return; continue; }
            if(line.startsWith(":")) continue;
            if(line.equals("data") || line.startsWith("data:")) {
                String value=line.length()>4 ? line.substring(5) : "";
                if(value.startsWith(" ")) value=value.substring(1);
                if(data.length()+value.length()>8*1024*1024) throw new IOException("单个流事件超过 8 MiB");
                data.append(value).append('\n');
            }
        }
        dispatch(data,sink);
    }
    private static boolean dispatch(StringBuilder data, Sink sink) throws IOException {
        if(data.length()==0) return false;
        String value=data.substring(0,data.length()-1); data.setLength(0);
        if(value.equals("[DONE]")) return true;
        sink.event(value); return false;
    }
}
