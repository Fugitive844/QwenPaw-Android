package cn.qwenpaw.android;

import org.json.*;
import java.util.Locale;

/** Server-reported context is distinct from the tokens billed for a turn. */
public final class UsageSnapshot {
    public final JSONObject usage, context;
    private UsageSnapshot(JSONObject usage,JSONObject context){this.usage=usage;this.context=context;}
    public static UsageSnapshot parse(JSONObject payload){
        if(payload==null)return null;
        JSONObject u=payload.optJSONObject("usage"),c=payload.optJSONObject("context_usage");
        if(u==null&&c==null)return null;
        return new UsageSnapshot(u==null?new JSONObject():Json.copy(u),c==null?new JSONObject():Json.copy(c));
    }
    public static UsageSnapshot metadata(JSONObject message){
        JSONObject meta=message.optJSONObject("metadata");if(meta==null)return null;
        UsageSnapshot result=parse(meta.optJSONObject("qwenpaw_turn_usage"));
        if(result!=null)return result;
        JSONObject nested=meta.optJSONObject("metadata");
        return nested==null?null:parse(nested.optJSONObject("qwenpaw_turn_usage"));
    }
    public long limit(long activeLimit){return activeLimit>0?activeLimit:number(context,"max_input_length");}
    public boolean hasContext(){return context.has("estimated_tokens")&&!context.isNull("estimated_tokens")&&context.optDouble("estimated_tokens",-1)>=0;}
    public long tokens(){return number(context,"estimated_tokens");}
    public double percent(long activeLimit){long max=limit(activeLimit);return max>0?Math.min(100,100.0*tokens()/max):0;}
    public boolean hasCache(){return usage.optBoolean("session_cache_observed",false);}
    public double cachePercent(){double value=usage.optDouble("session_cache_hit_rate",Double.NaN);if(!Double.isFinite(value)){long total=number(usage,"session_cache_eligible_input_tokens");value=total>0?100.0*number(usage,"session_cache_read_tokens")/total:0;}return Math.max(0,Math.min(100,value));}
    public static long number(JSONObject o,String key){return Math.max(0,o.optLong(key,0));}
    public static String count(long value){return String.format(Locale.CHINA,"%,d",value);}
    public static String ratio(double value){return String.format(Locale.CHINA,value>0&&value<1?"%.2f%%":"%.1f%%",value);}
}
