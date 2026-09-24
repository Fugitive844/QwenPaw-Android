package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AccountAndThinkingTest {
    private JSONObject state(JSONObject control){return Json.obj("model_key","provider:model","control",control);}
    @Test public void legacyUnknownAndUnsupportedModelsHaveNoThinkingOptions(){
        JSONObject effort=state(Json.obj("kind","effort","efforts",Json.arr("low","high")));
        assertTrue(ThinkingOptions.levels(false,effort).isEmpty());
        for(String kind:List.of("unknown","unsupported","future-kind"))assertTrue(ThinkingOptions.levels(true,state(Json.obj("kind",kind,"supports_off",true,"efforts",Json.arr("high")))).isEmpty());
        assertTrue(ThinkingOptions.levels(true,new JSONObject()).isEmpty());
    }
    @Test public void onlyDeclaredEffortsAndOffAreSelectable(){
        JSONObject state=state(Json.obj("kind","effort","efforts",Json.arr("low","high","high","invalid")));
        assertEquals(List.of("inherit","low","high"),ThinkingOptions.levels(true,state));
        assertFalse(ThinkingOptions.accepts(true,state,Json.obj("level","off")));
        assertFalse(ThinkingOptions.accepts(true,state,Json.obj("level","medium")));
        Json.put(state.optJSONObject("control"),"supports_off",true);
        assertTrue(ThinkingOptions.accepts(true,state,Json.obj("level","off")));
        assertFalse(ThinkingOptions.accepts(true,state,Json.obj("level","low","budget_tokens",1024)));
    }
    @Test public void budgetsRequireValidModelRangeAndIntegerWithinBounds(){
        JSONObject state=state(Json.obj("kind","budget","budget_min",1024,"budget_max",8192));
        for(int tokens:new int[]{1024,8192})assertTrue(ThinkingOptions.accepts(true,state,Json.obj("level","budget","budget_tokens",tokens)));
        for(Object tokens:new Object[]{1023,8193,1024.5,"2048",JSONObject.NULL})assertFalse(ThinkingOptions.accepts(true,state,Json.obj("level","budget","budget_tokens",tokens)));
        assertTrue(ThinkingOptions.levels(true,state(Json.obj("kind","budget","budget_min",8192,"budget_max",1024))).isEmpty());
    }
    @Test public void profileShowsAllowlistedFieldsWithoutLeakingMetadata(){
        AccountProfile p=new AccountProfile(Json.obj("enabled",true,"mode","hub"),Json.obj("username","alice","user_id","u1","role","user","disabled",false,"last_login_at",JSONObject.NULL,"profile",Json.obj("workspace_dir","/workspace","token","secret"),"metadata",Json.obj("secret","hidden")));
        assertEquals(Map.of("账号","alice","用户 ID","u1","角色","普通用户","账号状态","正常","个人工作目录","/workspace"),p.fields());
        assertTrue(p.passwordAllowed);assertTrue(p.hub);
    }
    @Test public void passwordRulesDifferForHubStandaloneAndAnonymous(){
        AccountProfile hub=new AccountProfile(Json.obj("enabled",true,"mode","hub"),new JSONObject());
        AccountProfile standalone=new AccountProfile(Json.obj("enabled",true,"has_users",true),new JSONObject());
        assertEquals("",hub.passwordError("","new-secret","new-secret"));
        assertFalse(standalone.passwordError("","new-secret","new-secret").isEmpty());
        assertEquals("",standalone.passwordError("old-secret","new-secret","new-secret"));
        assertFalse(standalone.passwordError("new-secret","new-secret","new-secret").isEmpty());
        assertFalse(hub.passwordError("","short","short").isEmpty());
        assertFalse(hub.passwordError("","new-secret","mismatch").isEmpty());
        assertFalse(hub.passwordError(""," ".repeat(8)," ".repeat(8)).isEmpty());
        assertFalse(hub.passwordError("","a".repeat(1025),"a".repeat(1025)).isEmpty());
        assertFalse(new AccountProfile(Json.obj("enabled",false,"has_users",true),new JSONObject()).passwordAllowed);
        assertFalse(new AccountProfile(Json.obj("enabled",true,"has_users",false),new JSONObject()).passwordAllowed);
    }
}
