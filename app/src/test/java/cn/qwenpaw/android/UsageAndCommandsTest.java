package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class UsageAndCommandsTest {
    @Test public void contextIsNotBilledInputAndUsesCurrentModelLimit(){
        UsageSnapshot s=UsageSnapshot.parse(Json.obj("usage",Json.obj("prompt_tokens",66674,"total_tokens",67501),"context_usage",Json.obj("estimated_tokens",807,"max_input_length",1000000)));
        assertEquals(807,s.tokens());assertEquals(.0807,s.percent(0),.00001);assertEquals(80.7,s.percent(1000),.00001);assertEquals(100,s.percent(100),0);
    }
    @Test public void zeroContextIsReportedButMissingIsUnknown(){
        UsageSnapshot zero=UsageSnapshot.parse(Json.obj("context_usage",Json.obj("estimated_tokens",0,"max_input_length",1000)));assertTrue(zero.hasContext());assertEquals(0,zero.tokens());
        UsageSnapshot absent=UsageSnapshot.parse(Json.obj("usage",Json.obj("prompt_tokens",200)));assertFalse(absent.hasContext());assertFalse(absent.hasCache());assertNull(UsageSnapshot.parse(new JSONObject()));
    }
    @Test public void cacheUsesSessionAggregateNotLastCall(){
        UsageSnapshot s=UsageSnapshot.parse(Json.obj("usage",Json.obj("cache_observed",true,"cache_hit_rate",99,"session_cache_observed",true,"session_cache_read_tokens",200,"session_cache_eligible_input_tokens",1000)));
        assertEquals(20,s.cachePercent(),0);assertTrue(s.hasCache());
        s=UsageSnapshot.parse(Json.obj("usage",Json.obj("cache_observed",true,"cache_hit_rate",99)));assertFalse(s.hasCache());
    }
    @Test public void historyUnderstandsDirectAndNestedMetadataAndScopeReset(){
        JSONObject usage=Json.obj("usage",Json.obj("total_tokens",100),"context_usage",Json.obj("estimated_tokens",600,"max_input_length",1000));Transcript t=new Transcript();
        t.history(Json.arr(Json.obj("id","a","role","assistant","metadata",Json.obj("qwenpaw_turn_usage",usage))));assertEquals(600,t.metrics.tokens());
        Json.put(Json.object(usage.opt("context_usage")),"estimated_tokens",200);
        t.history(Json.arr(Json.obj("id","a","role","assistant","metadata",Json.obj("metadata",Json.obj("qwenpaw_turn_usage",usage)))));assertEquals(200,t.metrics.tokens());
        t.history(Json.arr(Json.obj("role","assistant","content","No usage")));assertNull(t.metrics);
    }
    @Test public void liveUsageAfterCompletedWinsOverOldSnapshotMetadata(){
        Transcript t=new Transcript();t.accept(Json.obj("object","response","status","completed"));t.accept(Json.obj("type","turn_usage","context_usage",Json.obj("estimated_tokens",40,"max_input_length",1000)));
        t.accept(Json.obj("object","message","id","a","metadata",Json.obj("qwenpaw_turn_usage",Json.obj("context_usage",Json.obj("estimated_tokens",900)))));assertEquals(40,t.metrics.tokens());assertTrue(t.liveMetrics);t.beginTurn();assertFalse(t.liveMetrics);t.clear();assertNull(t.metrics);
    }
    @Test public void commandsFilterUnavailableSkillsAndHonorCollisionPrecedence(){
        JSONArray skills=Json.arr(Json.obj("name","compact","enabled",true),Json.obj("name","goal","enabled",true),Json.obj("name","disabled","enabled",false),Json.obj("name","slack-only","enabled",true,"channels",Json.arr("slack")),Json.obj("name","console-skill","enabled",true,"channels",Json.arr("console")),Json.obj("name","all-skill","enabled",true),Json.obj("name","","enabled",true));
        List<CommandCatalog.Entry> entries=CommandCatalog.build(Json.arr(Json.obj("id","goal","source","builtin","slash_command","goal")),skills);
        assertEquals(7,entries.size());assertEquals("常用",entries.stream().filter(e->e.command().equals("/compact")).findFirst().get().category());assertEquals("Loop / 插件",entries.stream().filter(e->e.command().equals("/goal")).findFirst().get().category());assertFalse(entries.stream().anyMatch(e->e.command().contains("disabled")||e.command().contains("slack")));
    }
    @Test public void allSkillsArePresentAndPluginLocalizationIsSearchable(){
        JSONArray skills=new JSONArray();for(int i=0;i<100;i++)skills.put(Json.obj("name","skill-"+i,"enabled",true));
        List<CommandCatalog.Entry> entries=CommandCatalog.build(Json.arr(Json.obj("id","custom","slash_command","/custom","source","plugin","name_i18n",Json.obj("zh-CN","项目助手"),"description_i18n",Json.obj("zh-CN","管理项目\n额外内容"))),skills);
        assertEquals(105,entries.size());CommandCatalog.Entry plugin=entries.get(4);assertEquals("/custom",plugin.command());assertTrue(plugin.matches("项目","Loop / 插件"));assertFalse(plugin.matches("项目","技能"));assertEquals("管理项目",plugin.description());
    }
}
