package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

public class SettingsLogicTest {
    @Test public void savingPreservesUnknownFieldsCollapsedConfigAndConcurrentServerChanges(){
        JSONObject latest=Json.obj("shell_command_timeout",60,"approval_level","SMART","future",Json.obj("x",42),"loop",Json.obj("custom_modes",Json.arr(Json.obj("id","x"))),"light_context_config",Json.obj("strategy","scroll","visual_compact_config",Json.obj("enabled",true,"effort","high"),"context_compact_config",Json.obj("enabled",true,"reserve_threshold_ratio",0.2,"compact_threshold_ratio",0.8)));
        JSONObject saved=RuntimeConfig.merge(latest,Map.of("light_context_config.context_compact_config.compact_threshold_ratio","0.7","shell_command_timeout","120"));
        assertEquals("SMART",saved.optString("approval_level"));assertEquals(42,saved.optJSONObject("future").optInt("x"));assertEquals(latest.optJSONObject("loop").toString(),saved.optJSONObject("loop").toString());assertEquals("high",RuntimeConfig.get(saved,RuntimeConfig.VISUAL+"effort"));assertEquals(0.2,((Number)RuntimeConfig.get(saved,RuntimeConfig.COMPACT+"reserve_threshold_ratio")).doubleValue(),0);assertEquals(60,latest.optInt("shell_command_timeout"));
    }
    @Test public void rejectsInvalidRetryLimitsIncludingDisabledRetryAndNonFiniteNumbers(){
        assertThrows(IllegalArgumentException.class,()->RuntimeConfig.merge(Json.obj("llm_backoff_base",5),Map.of("llm_backoff_cap","2")));
        assertThrows(IllegalArgumentException.class,()->RuntimeConfig.parse(RuntimeConfig.field("llm_backoff_base"),"NaN"));
        assertThrows(IllegalArgumentException.class,()->RuntimeConfig.parse(RuntimeConfig.field("llm_max_retries"),"2.5"));
        assertThrows(IllegalArgumentException.class,()->RuntimeConfig.parse(RuntimeConfig.field("llm_max_retries"),"0"));
        assertThrows(IllegalArgumentException.class,()->RuntimeConfig.parse(RuntimeConfig.field(RuntimeConfig.COMPACT+"reserve_threshold_ratio"),"0"));
    }
    @Test public void arraysAreReplacedIncludingEmptyAndWhitespaceValuesRemainExact(){
        JSONObject saved=RuntimeConfig.merge(Json.obj("light_context_config",Json.obj("tool_result_pruning_config",Json.obj("exempt_tool_names",Json.arr("old")))),Map.of(RuntimeConfig.PRUNING+"exempt_tool_names","","shell_command_executable","  bash -l  "));
        assertEquals(0,((JSONArray)RuntimeConfig.get(saved,RuntimeConfig.PRUNING+"exempt_tool_names")).length());assertEquals("  bash -l  ",saved.optString("shell_command_executable"));
        assertEquals(2,((JSONArray)RuntimeConfig.parse(RuntimeConfig.field(RuntimeConfig.PRUNING+"exempt_file_extensions"),".md, .txt .md")).length());
    }
    @Test public void reserveMirrorsNativeAndScrollBounds(){assertEquals(40000,RuntimeConfig.reserve(1000000,0.3,"scroll"));assertEquals(1000,RuntimeConfig.reserve(10000,0.01,"scroll"));assertEquals(100,RuntimeConfig.reserve(10000,0.01,"native"));}
    @Test public void environmentKeysRejectCaseInsensitiveDuplicatesAndCatalogOverrides(){
        JSONArray existing=Json.arr(Json.obj("key","API_KEY")),catalog=Json.arr(Json.obj("key","QWENPAW_PORT"));
        assertThrows(IllegalArgumentException.class,()->RuntimeConfig.validateEnvKey("api_key",true,existing,catalog));assertThrows(IllegalArgumentException.class,()->RuntimeConfig.validateEnvKey("qwenpaw_port",true,existing,catalog));assertThrows(IllegalArgumentException.class,()->RuntimeConfig.validateEnvKey("1BAD",true,existing,catalog));RuntimeConfig.validateEnvKey("API_KEY",false,existing,catalog);
    }
    @Test public void aggregationKeepsProvidersAgentsAndWeightedCacheSeparate(){
        TokenAnalysis a=new TokenAnalysis(Json.arr(row("p1","same","alpha",100,20,80,100),row("p2","same","beta",900,30,90,900),row("p1","same",null,10,5,0,0)));
        assertEquals(2,a.models.size());assertEquals(3,a.agents.size());assertTrue(a.agents.containsKey(""));assertEquals(1065,a.total.total());assertEquals("17.0%",a.total.hit());assertEquals(170,a.total.read);assertEquals(3,a.total.calls);
    }
    @Test public void absentCacheMetricsAreUnknownAndLargeCountersUseLong(){TokenAnalysis a=new TokenAnalysis(Json.arr(Json.obj("date","2026-01-01","provider_id","p","model","m","prompt_tokens",3000000000L,"completion_tokens",1)));assertEquals(3000000001L,a.total.total());assertEquals("—",a.total.hit());}
    @Test public void dateRangeIsInclusiveAtMost365DaysAndCannotIncludeFuture(){LocalDate today=LocalDate.now();assertTrue(TokenAnalysis.query(today.minusDays(364),today).contains("end_date="));assertThrows(IllegalArgumentException.class,()->TokenAnalysis.query(today.minusDays(365),today));assertThrows(IllegalArgumentException.class,()->TokenAnalysis.query(today,today.plusDays(1)));assertThrows(IllegalArgumentException.class,()->TokenAnalysis.query(today,today.minusDays(1)));}
    private JSONObject row(String provider,String model,String agent,long prompt,long completion,long read,long eligible){return Json.obj("date","2026-09-23","provider_id",provider,"model",model,"agent_id",agent,"prompt_tokens",prompt,"completion_tokens",completion,"cache_read_tokens",read,"cache_eligible_input_tokens",eligible,"cache_observed_calls",eligible>0?1:0,"call_count",1);}
}

