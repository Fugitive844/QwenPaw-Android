package cn.qwenpaw.android;

import org.json.*;

final class LoopDescriptions {
    static String description(JSONObject mode) {
        if(mode.optString("source","builtin").equals("builtin")) {
            String text=switch(mode.optString("id")){case "default"->"标准的受控智能体 Loop。";case "goal"->"持续推进一个具体且可验证的目标。";case "mission"->"运行结构化、可持续的多步骤任务。";default->"";};
            if(!text.isEmpty())return text;
        }
        JSONObject labels=Json.object(mode.opt("description_i18n"));
        for(String text:new String[]{labels.optString("zh-CN"),labels.optString("zh"),mode.optString("description")})
            for(String line:text.split("\\r?\\n"))if(!line.isBlank())return line.trim().replaceAll("[*`#]","");
        return "暂无模式说明";
    }
}
