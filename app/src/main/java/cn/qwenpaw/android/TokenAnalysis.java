package cn.qwenpaw.android;

import org.json.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Aggregation never adds cache tokens to prompt + completion, nor averages percentages. */
final class TokenAnalysis {
    static final class Stats {
        long prompt,completion,read,write,eligible,observed,calls;
        void add(JSONObject r){prompt+=r.optLong("prompt_tokens");completion+=r.optLong("completion_tokens");read+=r.optLong("cache_read_tokens");write+=r.optLong("cache_write_tokens");eligible+=r.optLong("cache_eligible_input_tokens");observed+=r.optLong("cache_observed_calls");calls+=r.optLong("call_count");}
        long total(){return prompt+completion;}
        String hit(){return eligible>0?String.format(Locale.CHINA,"%.1f%%",Math.max(0,Math.min(100,100.0*read/eligible))):"—";}
    }
    final Stats total=new Stats();
    final Map<String,Stats> models=new TreeMap<>(),dates=new TreeMap<>(),agents=new TreeMap<>();
    final Map<String,Map<String,Stats>> modelDates=new TreeMap<>();
    TokenAnalysis(JSONArray records){for(int i=0;i<records.length();i++){
        JSONObject r=records.optJSONObject(i);if(r==null)continue;
        String model=r.optString("provider_id")+":"+r.optString("model"),date=r.optString("date"),agent=r.isNull("agent_id")?"":r.optString("agent_id");
        total.add(r);models.computeIfAbsent(model,k->new Stats()).add(r);dates.computeIfAbsent(date,k->new Stats()).add(r);agents.computeIfAbsent(agent,k->new Stats()).add(r);
        modelDates.computeIfAbsent(date,k->new TreeMap<>()).computeIfAbsent(model,k->new Stats()).add(r);
    }}
    static String query(LocalDate start,LocalDate end){if(start.isAfter(end)||end.isAfter(LocalDate.now())||ChronoUnit.DAYS.between(start,end)>=365)throw new IllegalArgumentException("请选择不超过 365 天且不晚于今天的日期范围");return "?start_date="+start+"&end_date="+end;}
}
