package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Only the active model's server-declared controls may be offered. */
final class ThinkingOptions {
    private static final List<String> EFFORTS=List.of("minimal","low","medium","high","xhigh","max");
    static List<String> levels(boolean sessionModels,JSONObject state){
        if(!sessionModels||state.optString("model_key").isBlank())return List.of();
        JSONObject control=Json.object(state.opt("control"));String kind=control.optString("kind");
        ArrayList<String> values=new ArrayList<>();
        if(kind.equals("effort")){
            JSONArray efforts=Json.array(control.opt("efforts"));
            for(int i=0;i<efforts.length();i++){String value=efforts.optString(i);if(EFFORTS.contains(value)&&!values.contains(value))values.add(value);}
        }else if(kind.equals("budget")&&control.optInt("budget_min",0)>0&&control.optInt("budget_max",0)>=control.optInt("budget_min"))values.add("budget");
        if(values.isEmpty())return List.of();
        if(control.optBoolean("supports_off"))values.add(0,"off");values.add(0,"inherit");return values;
    }
    static String label(String level){return switch(level){case "inherit"->"跟随默认";case "off"->"关闭思考";case "minimal"->"最少";case "low"->"低";case "medium"->"中";case "high"->"高";case "xhigh"->"更高";case "max"->"最高";case "budget"->"自定义思考预算";default->"跟随默认";};}
    static boolean accepts(boolean sessionModels,JSONObject state,JSONObject value){
        String level=value.optString("level");if(!levels(sessionModels,state).contains(level))return false;
        if(!level.equals("budget"))return !value.has("budget_tokens");
        JSONObject control=Json.object(state.opt("control"));Object tokens=value.opt("budget_tokens");
        return tokens instanceof Integer&&((Integer)tokens)>=control.optInt("budget_min")&&((Integer)tokens)<=control.optInt("budget_max");
    }
}
