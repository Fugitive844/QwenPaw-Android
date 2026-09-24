package cn.qwenpaw.android;

import org.json.*;
import java.util.*;
import java.util.regex.Pattern;

/** Descriptions follow the server's prompt builder and heartbeat runner, not filename guesses. */
final class AgentDocument {
    record Kind(String file,String title,String summary,String examples,String usage,String icon) {}
    static final List<Kind> ALL=List.of(
        new Kind("AGENTS.md","做事规则","了解它怎样处理任务、使用工具，以及哪些操作需要先与你确认。","常见内容：任务流程、工具使用约定、安全边界和协作习惯。","被选为对话指引后参与系统提示词；服务端会按配置处理其中的记忆与心跳章节。","shield"),
        new Kind("PROFILE.md","身份与用户画像","看看它是谁，以及它记录了怎样的你：称呼、偏好、背景与长期合作信息。","常见内容：智能体名称与定位、用户称呼、偏好和项目背景。","这是一份智能体维护的工作区资料，不等同于个人中心里的登录账号资料。","paw"),
        new Kind("SOUL.md","性格与沟通方式","了解它的行为原则、表达风格，以及与你相处时遵守的边界。","常见内容：帮助用户的原则、语气与表达方式、隐私和对外行动边界。","被选为对话指引后影响它如何回应与行动；内容可由你和智能体逐步完善。","spark"),
        new Kind("HEARTBEAT.md","主动检查清单","看看没有新消息时，你希望它定期关注什么、检查什么。","常见内容：待办巡查、状态检查和需要主动留意的事项。","启用心跳后，服务端按间隔和活跃时段读取这份清单；文件存在不代表定时检查已经开启。","clock")
    );
    static Kind find(String file){for(Kind kind:ALL)if(kind.file().equals(file))return kind;throw new IllegalArgumentException("不支持的智能体档案");}
    private static final Pattern FRONTMATTER=Pattern.compile("\\A\\uFEFF?---[ \\t]*\\r?\\n[\\s\\S]*?\\r?\\n(?:---|\\.\\.\\.)[ \\t]*(?:\\r?\\n|$)");
    static String markdown(String source){return FRONTMATTER.matcher(source).replaceFirst("").strip();}
    static String content(JSONObject response)throws java.io.IOException{
        Object value=response.opt("content");if(!(value instanceof String))throw new java.io.IOException("未收到有效的文本内容");return (String)value;
    }
    static String status(Kind kind,JSONArray promptFiles,JSONObject heartbeat){
        String prompt="对话指引状态未获取";
        if(promptFiles!=null){boolean selected=false;for(int i=0;i<promptFiles.length();i++)if(kind.file().equals(promptFiles.optString(i)))selected=true;prompt=selected?"已选为对话指引":"未选为对话指引";}
        if(!kind.file().equals("HEARTBEAT.md"))return prompt;
        String result="心跳状态未获取";
        if(heartbeat!=null&&heartbeat.opt("enabled") instanceof Boolean){
            result=heartbeat.optBoolean("enabled")?"心跳已开启":"心跳未开启";
            if(heartbeat.optBoolean("enabled")){
                String every=Json.text(heartbeat.opt("every"));if(!every.isBlank())result+=" · 间隔 "+every;
                JSONObject hours=heartbeat.optJSONObject("activeHours");if(hours!=null&&!hours.optString("start").isBlank()&&!hours.optString("end").isBlank())result+=" · "+hours.optString("start")+"–"+hours.optString("end");
            }
        }
        // HEARTBEAT may also be explicitly included in the prompt file list.
        return result+(prompt.equals("已选为对话指引")?" · 同时作为对话指引":"");
    }
    static JSONObject metadata(JSONArray files,String name){if(files!=null)for(int i=0;i<files.length();i++){JSONObject file=files.optJSONObject(i);if(file!=null&&name.equals(file.optString("filename")))return file;}return null;}
    static String modified(JSONObject file){
        if(file==null)return "";String raw=Json.text(file.opt("modified_time"));if(raw.isBlank())return "";
        try{return java.time.OffsetDateTime.parse(raw).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}catch(java.time.DateTimeException e){return raw;}
    }
}
