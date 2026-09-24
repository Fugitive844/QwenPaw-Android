package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Editable fields mirror Console's four runtime cards; untouched server fields survive saves. */
final class RuntimeConfig {
    record Field(String path, String label, String type, double min, double max) {}
    static Field f(String path,String label,String type,double min,double max){return new Field(path,label,type,min,max);}
    static final String LIGHT="light_context_config.", COMPACT=LIGHT+"context_compact_config.",
        PRUNING=LIGHT+"tool_result_pruning_config.", VISUAL=LIGHT+"visual_compact_config.";
    static final List<Field> FIELDS=List.of(
        f("shell_command_timeout","shellCommandTimeout","number",1,Double.MAX_VALUE),
        f("shell_command_executable","shellCommandExecutable","text",0,0),
        f("auto_title_config.enabled","autoGenerateSessionTitle","boolean",0,0),
        f("memory_manager_backend","memoryManagerBackend","memory",0,0),
        f("llm_retry_enabled","llmRetryEnabled","boolean",0,0),
        f("llm_max_retries","llmMaxRetries","integer",1,Integer.MAX_VALUE),
        f("llm_backoff_base","llmBackoffBase","number",0.1,Double.MAX_VALUE),
        f("llm_backoff_cap","llmBackoffCap","number",0.5,Double.MAX_VALUE),
        f(LIGHT+"dialog_path","dialogPath","text",0,0),
        f(LIGHT+"token_count_estimate_divisor","tokenCountEstimateDivisor","number",2,5),
        f(COMPACT+"enabled","contextCompactEnabled","boolean",0,0),
        f(COMPACT+"compact_threshold_ratio","contextCompactRatio","number",0.1,0.9),
        f(COMPACT+"reserve_threshold_ratio","contextCompactReserveRatio","number",0.01,0.3),
        f(LIGHT+"scroll_config.history_retention_days","historyRetentionDays","integer",0,Integer.MAX_VALUE),
        f(PRUNING+"enabled","toolResultCompactEnabled","boolean",0,0),
        f(PRUNING+"pruning_recent_n","toolResultCompactRecentN","integer",1,10),
        f(PRUNING+"pruning_old_msg_max_bytes","toolResultCompactOldThreshold","integer",100,Integer.MAX_VALUE),
        f(PRUNING+"pruning_recent_msg_max_bytes","toolResultCompactRecentThreshold","integer",1000,Integer.MAX_VALUE),
        f(PRUNING+"offload_retention_days","toolResultCompactRetentionDays","integer",1,365),
        f(PRUNING+"exempt_file_extensions","exemptFileExtensions","tags",0,0),
        f(PRUNING+"exempt_tool_names","exemptToolNames","tags",0,0),
        f(VISUAL+"enabled","visualCompactEnabled","boolean",0,0),
        f(VISUAL+"effort","visualCompactEffort","effort",0,0),
        f("approval_level","toolExecutionLevelTitle","approval",0,0)
    );
    static Object get(JSONObject root,String path){Object value=root;for(String key:path.split("\\.")){if(!(value instanceof JSONObject))return null;value=((JSONObject)value).opt(key);}return value==JSONObject.NULL?null:value;}
    static void set(JSONObject root,String path,Object value){String[] parts=path.split("\\.");JSONObject node=root;for(int i=0;i<parts.length-1;i++){JSONObject child=node.optJSONObject(parts[i]);if(child==null){child=new JSONObject();Json.put(node,parts[i],child);}node=child;}Json.put(node,parts[parts.length-1],value);}
    static Field field(String path){for(Field f:FIELDS)if(f.path.equals(path))return f;throw new IllegalArgumentException("未知配置字段");}
    static Object parse(Field field,String value){
        if(field.type.equals("boolean"))return Boolean.parseBoolean(value);
        if(field.type.equals("tags")){JSONArray a=new JSONArray();Set<String> unique=new LinkedHashSet<>();for(String s:value.split("[,，\\s]+"))if(!s.isEmpty())unique.add(s);unique.forEach(a::put);return a;}
        if(field.type.equals("integer")||field.type.equals("number")){
            double n;try{n=Double.parseDouble(value.trim());}catch(NumberFormatException e){throw new IllegalArgumentException("请输入有效数字");}
            if(!Double.isFinite(n)||n<field.min||n>field.max)throw new IllegalArgumentException("取值范围："+field.min+" ～ "+(field.max>=Integer.MAX_VALUE?"上限由服务器决定":field.max));
            if(field.type.equals("integer")){if(n!=Math.floor(n))throw new IllegalArgumentException("请输入整数");return (long)n;}return n;
        }
        if(field.type.equals("effort")&&!List.of("low","medium","high").contains(value))throw new IllegalArgumentException("请选择压缩强度");
        if(field.type.equals("approval")&&!List.of("STRICT","SMART","AUTO","OFF").contains(value))throw new IllegalArgumentException("请选择审批模式");
        return value;
    }
    static JSONObject merge(JSONObject latest,Map<String,String> edits){
        JSONObject result=Json.copy(latest);
        for(Map.Entry<String,String> entry:edits.entrySet())set(result,entry.getKey(),parse(field(entry.getKey()),entry.getValue()));
        double base=number(result,"llm_backoff_base",1),cap=number(result,"llm_backoff_cap",10);
        if((edits.containsKey("llm_backoff_base")||edits.containsKey("llm_backoff_cap"))&&cap<base)throw new IllegalArgumentException("退避最大延迟必须大于等于基础延迟");
        return result;
    }
    static double number(JSONObject root,String path,double fallback){Object v=get(root,path);return v instanceof Number?((Number)v).doubleValue():fallback;}
    static String display(Object value){if(value instanceof JSONArray a){List<String> items=new ArrayList<>();for(int i=0;i<a.length();i++)items.add(a.optString(i));return String.join(", ",items);}return value==null?"":value.toString();}
    static long reserve(long limit,double ratio,String strategy){return (long)("native".equals(strategy)?limit*ratio:Math.min(40000,Math.max(limit*ratio,Math.min(10000,limit*0.1))));}
    static void validateEnvKey(String key,boolean isNew,JSONArray variables,JSONArray catalog){
        if(!key.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("变量名只能包含字母、数字和下划线，且不能以数字开头");
        if(isNew)for(JSONArray rows:List.of(variables,catalog))for(int i=0;i<rows.length();i++)if(Json.object(rows.opt(i)).optString("key").equalsIgnoreCase(key))throw new IllegalArgumentException("已存在同名环境变量："+key);
    }
}
