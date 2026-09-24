package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Shared skill validation and partial-result interpretation, independent of Android UI. */
final class SkillLogic {
    private SkillLogic(){}
    static JSONArray tags(String raw){
        LinkedHashSet<String> values=new LinkedHashSet<>();for(String item:raw.split("[,，\\n]")){String v=item.trim();if(v.isEmpty())continue;if(v.length()>16)throw new IllegalArgumentException("每个标签最多 16 个字符");values.add(v);}
        if(values.size()>8)throw new IllegalArgumentException("最多添加 8 个标签");return new JSONArray(values);
    }
    static JSONObject config(String raw){Object value=Json.parse(raw.isBlank()?"{}":raw);if(!(value instanceof JSONObject o))throw new IllegalArgumentException("技能配置必须是 JSON 对象");return o;}
    static void validate(String name,String content){
        if(name.trim().isEmpty()||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals(".."))throw new IllegalArgumentException("请填写有效的技能名称，不可包含路径分隔符");
        String trimmed=content.strip();if(!trimmed.startsWith("---\n")&&!trimmed.startsWith("---\r\n"))throw new IllegalArgumentException("技能内容必须以 YAML frontmatter 开头，包含 name 和 description");
        int end=trimmed.indexOf("\n---",4);if(end<0)throw new IllegalArgumentException("技能 frontmatter 缺少结束分隔符");
        String meta=trimmed.substring(3,end);for(String key:new String[]{"name","description"})if(!java.util.regex.Pattern.compile("(?m)^"+key+":[ \\t]*\\S.*$").matcher(meta).find())throw new IllegalArgumentException("技能 frontmatter 缺少 "+key);
    }
    static String join(JSONArray values){ArrayList<String> list=new ArrayList<>();for(int i=0;i<values.length();i++)list.add(values.optString(i));return String.join(", ",list);}
    static String result(JSONObject result){
        ArrayList<String> lines=new ArrayList<>();JSONObject results=result.optJSONObject("results");
        if(results!=null){Iterator<String> it=results.keys();while(it.hasNext()){String k=it.next();JSONObject r=Json.object(results.opt(k));lines.add(k+"："+(r.optBoolean("success")?"成功":Json.text(r.opt("reason"))+" "+Json.text(r.opt("detail"))));}}
        for(String k:new String[]{"imported","updated","unchanged","downloaded","conflicts"})if(result.optJSONArray(k)!=null&&result.optJSONArray(k).length()>0)lines.add(switch(k){case "imported"->"已导入";case "updated"->"已更新";case "unchanged"->"无变化";case "downloaded"->"已分发";default->"冲突（未覆盖）";}+"：\n"+Json.text(result.opt(k)));
        JSONObject automation=result.optJSONObject("automation");if(automation!=null){for(String key:new String[]{"pool_failed","sync_failed"})if(Json.array(automation.opt(key)).length()>0)lines.add("自动处理存在失败：\n"+Json.text(automation.opt(key)));}
        if(!result.optString("_scan_warnings").isBlank())lines.add("安全扫描警告（服务端允许继续）\n"+result.optString("_scan_warnings"));
        if(result.optBoolean("deleted",true)==false||result.optBoolean("success",true)==false||result.optBoolean("installed",true)==false)lines.add(Json.text(result));
        if(result.has("count")&&result.optInt("count")==0)lines.add("没有导入新技能");
        return lines.isEmpty()?"操作完成":String.join("\n\n",lines);
    }
    static boolean builtin(JSONObject skill){return "system".equals(skill.optString("source"))||"builtin".equals(skill.optString("source"))||skill.optString("source").startsWith("builtin:");}
    static JSONObject automation(JSONObject original,boolean update,boolean sync,JSONArray targets){
        JSONObject payload=new JSONObject();Set<String> old=new HashSet<>(),next=new HashSet<>();JSONArray previous=Json.array(original.opt("auto_sync_targets"));for(int i=0;i<previous.length();i++)old.add(previous.optString(i));for(int i=0;i<targets.length();i++)next.add(targets.optString(i));boolean changed=!old.equals(next);
        if(builtin(original)&&update!=original.optBoolean("auto_update"))Json.put(payload,"auto_update",update);
        if(sync!=original.optBoolean("auto_sync")||changed){JSONObject setting=Json.obj("enabled",sync);if(sync||changed)Json.put(setting,"targets",targets.length()==0?JSONObject.NULL:targets);Json.put(payload,"auto_sync",setting);}
        return payload;
    }
    static JSONObject withWarnings(ApiClient api,JSONObject result,Collection<String> names){
        try{
            JSONArray alerts=Json.array(api.optionalStatus("/config/security/skill-scanner/blocked-history"));if(alerts.length()==0)return result;
            JSONObject cfg=Json.object(api.optionalStatus("/config/security/skill-scanner"));Set<String> wanted=new HashSet<>(names);JSONArray whitelist=Json.array(cfg.opt("whitelist"));for(int i=0;i<whitelist.length();i++)wanted.remove(Json.object(whitelist.opt(i)).optString("skill_name"));
            Map<String,JSONObject> latest=new LinkedHashMap<>();for(int i=0;i<alerts.length();i++){JSONObject record=Json.object(alerts.opt(i));if(wanted.contains(record.optString("skill_name"))&&record.optString("action").equals("warned"))latest.put(record.optString("skill_name"),record);}
            StringBuilder warnings=new StringBuilder();for(Map.Entry<String,JSONObject> entry:latest.entrySet()){warnings.append(entry.getKey()).append('\n');JSONArray findings=Json.array(entry.getValue().opt("findings"));for(int i=0;i<Math.min(5,findings.length());i++){JSONObject f=Json.object(findings.opt(i));warnings.append(f.optString("title")).append(" · ").append(f.optString("file_path")).append('\n').append(f.optString("description")).append('\n');}if(findings.length()>5)warnings.append("另有 ").append(findings.length()-5).append(" 条记录\n");}
            if(warnings.length()>0)Json.put(result,"_scan_warnings",warnings.toString());
        }catch(Exception ignored){/* Optional warning history cannot turn a successful write into failure. */}
        return result;
    }
}
