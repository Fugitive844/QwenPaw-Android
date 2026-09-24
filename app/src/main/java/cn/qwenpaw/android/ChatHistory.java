package cn.qwenpaw.android;

import org.json.*;
import java.time.*;
import java.util.*;

final class ChatHistory {
    static JSONObject newConsoleChat(String sessionId) {
        // The server upgrades this explicit placeholder on the first textual turn, then generates a title.
        return Json.obj("name","New Chat","session_id",sessionId,"user_id","default","channel","console",
                "meta",Json.obj("console_placeholder_name","New Chat"));
    }
    static boolean syncName(JSONObject current,JSONArray latest) {
        if(current==null || current.optString("id").isEmpty())return false;
        for(int i=0;i<latest.length();i++) {
            JSONObject item=latest.optJSONObject(i);
            if(item==null || !current.optString("id").equals(item.optString("id")))continue;
            String name=item.optString("name","");
            if(name.isBlank() || name.equals(current.optString("name")))return false;
            Json.put(current,"name",name);return true;
        }
        return false;
    }
    static List<JSONObject> sorted(JSONArray source) {
        List<JSONObject> result=new ArrayList<>();
        for(int i=0;i<source.length();i++)if(source.optJSONObject(i)!=null)result.add(source.optJSONObject(i));
        result.sort(Comparator.<JSONObject,Boolean>comparing(x->x.optBoolean("pinned")).reversed()
                .thenComparing(Comparator.comparingLong(ChatHistory::time).reversed()));
        return result;
    }
    private static long time(JSONObject chat) {
        Long updated=parse(chat.optString("updated_at"));
        Long created=parse(chat.optString("created_at"));
        return updated!=null?updated:created!=null?created:Long.MIN_VALUE;
    }
    private static Long parse(String value) {
        try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception ignored){}
        try{return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli();}catch(Exception ignored){}
        try{return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();}catch(Exception ignored){}
        return null;
    }
}
