package cn.qwenpaw.android;

import org.json.*;

final class ToolLogic {
    private ToolLogic(){}
    static Object value(JSONObject field,String raw){
        String label=field.optString("label",field.optString("name"));String type=field.optString("type");
        if(field.optBoolean("required")&&raw.isBlank())throw new IllegalArgumentException(label+" 为必填项");
        if(type.equals("number")){
            if(raw.isBlank())return JSONObject.NULL;
            double value;try{value=Double.parseDouble(raw);}catch(NumberFormatException e){throw new IllegalArgumentException(label+" 必须是数字");}
            if(!Double.isFinite(value)||!field.isNull("min")&&value<field.optDouble("min")||!field.isNull("max")&&value>field.optDouble("max"))throw new IllegalArgumentException(label+" 超出允许范围");
            return value;
        }
        return raw;
    }
    static JSONObject webSearch(String provider,String key){return provider.equals("tavily")?Json.obj("provider",provider):Json.obj("provider",provider,"api_key",key);}
    static boolean configured(JSONObject tool){return !tool.optBoolean("requires_config")||Json.object(tool.opt("config_values")).length()>0;}
}
