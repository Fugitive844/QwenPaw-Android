package cn.qwenpaw.android;

import java.util.Locale;
import java.util.Set;
import org.json.*;

/** File types supported by the chat attachment preview. Never infer HTML from file contents. */
public final class AttachmentPreview {
    public static final long TEXT_LIMIT = 3L * 1024 * 1024;
    public static final long EXTERNAL_LIMIT = 512L * 1024 * 1024;
    public enum Kind { TEXT, IMAGE, EXTERNAL, UNSUPPORTED }
    private static final Set<String> TEXT = Set.of("txt","md","markdown","json","jsonl","xml","csv","tsv","log","yaml","yml","ini","conf","config","properties","toml","sh","bat","ps1","py","js","ts","css","java","kt","c","cpp","h","sql","svg","gitignore");
    private static final Set<String> IMAGE = Set.of("png","jpg","jpeg","gif","webp","bmp","heic","heif");
    private static final Set<String> VIDEO = Set.of("mp4","m4v","mov","webm","mkv","avi","3gp","mpg","mpeg");
    private static final Set<String> AUDIO = Set.of("mp3","m4a","aac","wav","ogg","flac");
    private AttachmentPreview() {}
    public static boolean markdown(String name) {
        return Set.of("md","markdown").contains(extension(name));
    }
    public static String formatText(String name,String source) {
        if(!extension(name).equals("json"))return source;
        try {
            JSONTokener tokens=new JSONTokener(source.startsWith("\uFEFF")?source.substring(1):source);
            Object value=tokens.nextValue();
            if(tokens.nextClean()!=0)return source;
            if(value instanceof JSONObject object)return object.toString(2);
            if(value instanceof JSONArray array)return array.toString(2);
        } catch(JSONException ignored) { /* Invalid JSON remains readable as supplied. */ }
        return source;
    }
    public static String extension(String name) {
        if(name==null)return "";
        String clean=name.split("[?#]",2)[0];int slash=Math.max(clean.lastIndexOf('/'),clean.lastIndexOf('\\'));int dot=clean.lastIndexOf('.');
        return dot>slash ? clean.substring(dot+1).toLowerCase(Locale.ROOT) : "";
    }
    public static Kind kind(String name) {
        String ext=extension(name);
        if(TEXT.contains(ext))return Kind.TEXT;
        if(IMAGE.contains(ext))return Kind.IMAGE;
        if(VIDEO.contains(ext)||AUDIO.contains(ext)||ext.equals("html")||ext.equals("htm"))return Kind.EXTERNAL;
        return Kind.UNSUPPORTED;
    }
    public static String mime(String name) {
        return switch(extension(name)) {
            case "html","htm" -> "text/html";
            case "mp4","m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            case "avi" -> "video/x-msvideo";
            case "3gp" -> "video/3gpp";
            case "mpg","mpeg" -> "video/mpeg";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "aac" -> "audio/aac";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "jpg","jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            default -> "text/plain";
        };
    }
}
