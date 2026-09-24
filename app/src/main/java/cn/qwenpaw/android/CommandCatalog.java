package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Mirrors Console command precedence: system, loop/plugin, enabled console skills. */
public final class CommandCatalog {
    public record Entry(String command,String title,String description,String category) {
        public boolean matches(String query,String filter){return (filter.equals("全部")||category.equals(filter))&&(title+" "+command+" "+description).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
    }
    public static List<Entry> build(JSONArray loops,JSONArray skills){
        LinkedHashMap<String,Entry> result=new LinkedHashMap<>();
        add(result,"/compact","压缩上下文","整理当前上下文，保留继续对话所需的信息","常用");
        add(result,"/new","开始新上下文","按服务端配置保存记忆，并重置当前上下文","常用");
        add(result,"/clear","清空上下文","重置当前上下文、压缩摘要和计划","常用");
        add(result,"/skills","查看技能","让智能体列出当前可用技能","常用");
        for(int i=0;i<loops.length();i++){
            JSONObject mode=loops.optJSONObject(i);if(mode==null)continue;String command=normalize(mode.optString("slash_command"));if(command.isEmpty())continue;
            String id=mode.optString("id"),name=localized(mode,"name"),description=localized(mode,"description");
            if(mode.optString("source").equals("builtin"))switch(id){case "goal"->{name="目标模式";description="围绕目标持续推进，直到完成或需要你处理";}case "mission"->{name="任务模式";description="组织并推进多步骤任务";}default->{}}
            add(result,command,name.isBlank()?command:name,firstLine(description),"Loop / 插件");
        }
        ArrayList<JSONObject> enabled=new ArrayList<>();
        for(int i=0;i<skills.length();i++){
            JSONObject skill=skills.optJSONObject(i);if(skill==null||!skill.optBoolean("enabled",false))continue;
            JSONArray channels=skill.optJSONArray("channels");boolean available=channels==null||channels.length()==0;
            if(channels!=null)for(int j=0;j<channels.length();j++)if(List.of("all","console").contains(channels.optString(j)))available=true;
            if(available&&!skill.optString("name").isBlank())enabled.add(skill);
        }
        enabled.sort(Comparator.comparing(s->s.optString("name"),String.CASE_INSENSITIVE_ORDER));
        for(JSONObject skill:enabled)add(result,normalize(skill.optString("name")),skill.optString("name"),firstLine(skill.optString("description","调用此技能处理接下来的消息")),"技能");
        return new ArrayList<>(result.values());
    }
    private static void add(Map<String,Entry> map,String command,String title,String description,String category){map.putIfAbsent(command,new Entry(command,title,description,category));}
    private static String normalize(String value){value=value.trim();return value.isEmpty()?"":value.startsWith("/")?value:"/"+value;}
    private static String localized(JSONObject item,String key){JSONObject labels=item.optJSONObject(key+"_i18n");return labels==null?item.optString(key):labels.optString("zh-CN",labels.optString("zh",item.optString(key)));}
    private static String firstLine(String text){for(String line:text.split("\\R"))if(!line.isBlank())return line.trim().replaceAll("^[#* ]+","").replace("**","");return "";}
}
