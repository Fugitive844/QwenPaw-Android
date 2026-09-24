package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Allowlisted account fields; never render arbitrary metadata or credentials. */
final class AccountProfile {
    final JSONObject user;
    final boolean hub,passwordAllowed;
    AccountProfile(JSONObject status,JSONObject identity){
        hub="hub".equals(status.optString("mode"));user=Json.copy(identity);
        passwordAllowed=status.optBoolean("enabled",false)&&(hub||status.optBoolean("has_users",false));
    }
    Map<String,String> fields(){
        Map<String,String> fields=new LinkedHashMap<>();
        add(fields,"账号",user,"username");add(fields,"用户 ID",user,"user_id");
        String role=Json.text(user.opt("role"));if(!role.isBlank())fields.put("角色",switch(role){case "admin"->"管理员";case "user"->"普通用户";default->role;});
        if(user.opt("disabled") instanceof Boolean)fields.put("账号状态",user.optBoolean("disabled")?"已停用":"正常");
        add(fields,"创建时间",user,"created_at");add(fields,"更新时间",user,"updated_at");add(fields,"最近登录",user,"last_login_at");
        add(fields,"个人工作目录",Json.object(user.opt("profile")),"workspace_dir");return fields;
    }
    private static void add(Map<String,String> fields,String label,JSONObject data,String key){String value=Json.text(data.opt(key));if(!value.isBlank())fields.put(label,value);}
    String passwordError(String current,String password,String confirmation){
        if(!passwordAllowed)return "当前连接不支持账号密码修改";
        if(!hub&&current.isEmpty())return "请输入当前密码";
        int length=password.codePointCount(0,password.length());
        if(password.isBlank()||length<8||length>1024)return "新密码长度应为 8–1024 个字符，且不能全为空白";
        if(!password.equals(confirmation))return "两次输入的新密码不一致";
        if(!hub&&password.equals(current))return "新密码不能与当前密码相同";
        return "";
    }
}
