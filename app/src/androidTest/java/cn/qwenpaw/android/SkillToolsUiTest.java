package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SkillToolsUiTest {
    private MockWebServer server;
    private ActivityScenario<?> scenario;
    private ChatController chat;
    private String oldBase,oldToken,oldAgent;
    private boolean oldLogin;
    private JSONArray oldAgents;
    private volatile boolean enabled=true,failSave,failConfig;
    private volatile JSONObject saved,configSaved;
    private final List<RecordedRequest> writes=new CopyOnWriteArrayList<>();
    private final String source="---\nname: demo\ndescription: Test skill\n---\n# Demo\nRead carefully.";
    private MockResponse json(Object value){return new MockResponse().setHeader("Content-Type","application/json").setBody(value.toString());}
    private JSONObject skill(){return Json.obj("name","demo","description","测试技能说明","source","customized","content",source,"enabled",enabled,"tags",Json.arr("test"),"channels",Json.arr("all"),"config",Json.obj("future",Json.obj("keep",true)));}
    private JSONArray tools(){return Json.arr(Json.obj("name","web_search","enabled",true,"description","搜索互联网","config_values",Json.obj("provider","tavily")),Json.obj("name","execute_shell_command","enabled",true,"description","运行命令","async_execution",false),Json.obj("name","plugin_demo","enabled",false,"requires_config",true,"description","插件配置测试","config_fields",Json.arr(Json.obj("name","timeout","label","Timeout","type","number","required",true,"min",1,"max",60),Json.obj("name","secret","label","Secret","type","password"),Json.obj("name","flag","label","Flag","type","boolean"))));}
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    @Before public void setup()throws Exception{
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest r){String p=r.getPath(),method=r.getMethod();if(!method.equals("GET"))writes.add(r);
            if(p.equals("/api/skills")||p.equals("/api/skills/refresh")||p.equals("/api/skills/pool"))return json(Json.arr(skill()));
            if(p.equals("/api/skills/demo")||p.equals("/api/skills/pool/demo"))return json(skill());
            if(p.equals("/api/skills/demo/disable")){enabled=false;return json(Json.obj("disabled",true));}
            if(p.equals("/api/skills/demo/enable")){enabled=true;return json(Json.obj("enabled",true));}
            if(p.equals("/api/skills/demo/config")||p.equals("/api/skills/pool/demo/config")){saved=Json.object(Json.parse(r.getBody().clone().readUtf8()));return failSave?json(Json.obj("detail","temporary unavailable")).setResponseCode(500):json(Json.obj("success",true,"name","demo"));}
            if(p.equals("/api/skills/batch-enable"))return json(Json.obj("results",Json.obj("demo",Json.obj("success",false,"reason","security_scan_failed"))));
            if(p.equals("/api/skills/workspaces"))return json(Json.arr(Json.obj("agent_id","alpha","agent_name","Alpha","skill_names",Json.arr("demo")),Json.obj("agent_id","beta","agent_name","Beta","skill_names",new JSONArray())));
            if(p.equals("/api/skills/pool/download")){JSONObject b=Json.object(Json.parse(r.getBody().clone().readUtf8()));if(b.optBoolean("preview_only"))return json(Json.obj("detail",Json.obj("conflicts",Json.arr(Json.obj("workspace_id","alpha","skill_name","demo","reason","conflict"))))).setResponseCode(409);return json(Json.obj("downloaded",Json.arr(Json.obj("workspace_id","alpha","name","demo"))));}
            if(p.equals("/api/tools"))return json(tools());
            if(p.startsWith("/api/tools/web_search/config")){if(method.equals("GET"))return json(p.contains("anysearch")?Json.obj("provider","anysearch","api_key","masked-anysearch"):Json.obj("provider","tavily"));configSaved=Json.object(Json.parse(r.getBody().clone().readUtf8()));return failConfig?json(Json.obj("detail","SECRET MUST NOT LEAK")).setResponseCode(422):json(Json.obj("status","success"));}
            if(p.equals("/api/tools/plugin_demo/config"))return json(Json.obj("timeout",12,"secret","***","flag",true,"future","keep"));
            if(p.endsWith("/async-execution"))return json(Json.obj("name","execute_shell_command","enabled",true,"async_execution",true));
            return json(Json.obj("detail","unhandled "+p)).setResponseCode(404);
        }});server.start();String mockBase=server.url("/").toString().replaceAll("/$","");chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        main(()->{oldBase=chat.base;oldToken=chat.token;oldAgent=chat.agent;oldLogin=chat.loggedIn;oldAgents=chat.agents;chat.base=mockBase;chat.token="skills-test-token";chat.agent="alpha";chat.loggedIn=true;chat.agents=Json.arr(Json.obj("id","alpha","name","Alpha"),Json.obj("id","beta","name","Beta"));});
    }
    private void launch(Class<?> type,boolean pool){scenario=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),type).putExtra("pool",pool));}
    private void awaitText(String value)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){try{try{onView(withText(value)).check(matches(isDisplayed()));}catch(AssertionError|RuntimeException e){onView(withText(value)).perform(scrollTo()).check(matches(isDisplayed()));}return;}catch(AssertionError|RuntimeException e){Thread.sleep(100);}}throw new AssertionError("Not visible: "+value);}
    private void tap(String value){onView(withText(value)).perform(scrollTo(),click());}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(200);Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"skills-tools-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Test public void agentToggleAndExcludedEntries()throws Exception{launch(SkillsActivity.class,false);awaitText("查看 / 配置");capture("skills");for(String label:new String[]{"通过 ZIP 上传","通过 URL 上传","浏览市场","创建技能"})onView(withText(label)).check(doesNotExist());tap("禁用");awaitText("启用");assertEquals("alpha",writes.get(0).getHeader("X-Agent-Id"));assertEquals("POST",writes.get(0).getMethod());assertEquals("/api/skills/demo/disable",writes.get(0).getPath());assertEquals("alpha",chat.agent);}
    @Test public void agentMarkdownIsReadOnlyAndFailedConfigSaveRetainsDraft()throws Exception{
        verifyReadOnlyConfig(false,true);
    }
    @Test public void poolMarkdownIsReadOnlyAndConfigSaveNeverSubmitsContent()throws Exception{
        verifyReadOnlyConfig(true,false);
    }
    private void verifyReadOnlyConfig(boolean pool,boolean fail)throws Exception{
        launch(SkillsActivity.class,pool);awaitText("查看 / 配置");tap("查看 / 配置");awaitText("保存配置");
        onView(withTagValue(is((Object)"skill-markdown-preview"))).check((view,error)->{
            assertNull(error);assertFalse(view instanceof EditText);
            android.widget.TextView text=(android.widget.TextView)view;assertNull(text.getKeyListener());assertFalse(text.onCheckIsTextEditor());
            assertTrue(text.getText() instanceof android.text.Spanned);assertTrue(text.getText().toString().contains("Read carefully."));
        });
        onView(withContentDescription("SKILL.md 内容")).check(doesNotExist());onView(withContentDescription("技能名称")).check(doesNotExist());
        onView(withText("保存技能")).check(doesNotExist());assertEquals(0,writes.size());
        String draft="{\"future\":{\"keep\":true},\"option\":\"draft\"}";failSave=fail;
        onView(withContentDescription("技能配置（JSON 对象）")).perform(scrollTo(),replaceText(draft),closeSoftKeyboard());tap("保存配置");
        if(fail){awaitText("配置未保存");onView(withText("知道了")).perform(click());onView(withContentDescription("技能配置（JSON 对象）")).check(matches(withText(draft)));}
        else{long end=System.currentTimeMillis()+10000;while(saved==null&&System.currentTimeMillis()<end)Thread.sleep(50);assertNotNull(saved);}
        assertEquals(1,writes.size());assertEquals("PUT",writes.get(0).getMethod());
        assertEquals(pool?"/api/skills/pool/demo/config":"/api/skills/demo/config",writes.get(0).getPath());
        assertEquals(1,saved.length());assertTrue(saved.optJSONObject("config").optJSONObject("future").optBoolean("keep"));
        assertFalse(saved.has("content"));assertFalse(saved.has("name"));assertFalse(saved.has("source_name"));assertFalse(saved.has("overwrite"));
    }
    @Test public void partialBatchFailureIsVisible()throws Exception{launch(SkillsActivity.class,false);awaitText("查看 / 配置");onView(withContentDescription("选择技能 demo")).perform(scrollTo(),click());tap("批量管理");onView(withText("启用已选技能")).perform(click());awaitText("操作结果");onView(withText(containsString("security_scan_failed"))).check(matches(isDisplayed()));assertEquals("[\"demo\"]",writes.get(0).getBody().clone().readUtf8());}
    @Test public void poolTransferRequiresConflictConfirmation()throws Exception{launch(SkillsActivity.class,true);awaitText("分发");capture("pool");tap("分发");onView(withText("Alpha · alpha")).perform(click());onView(withText("确认")).perform(click());long end=System.currentTimeMillis()+10000;while(writes.size()<1&&System.currentTimeMillis()<end)Thread.sleep(50);awaitText("确认覆盖冲突技能");assertEquals(1,writes.size());assertTrue(Json.object(Json.parse(writes.get(0).getBody().clone().readUtf8())).optBoolean("preview_only"));onView(withText("确认")).perform(click());awaitText("传输结果");assertEquals(2,writes.size());JSONObject data=Json.object(Json.parse(writes.get(1).getBody().clone().readUtf8()));assertTrue(data.optBoolean("overwrite"));assertFalse(data.optBoolean("preview_only"));assertEquals("alpha",data.optJSONArray("targets").optJSONObject(0).optString("workspace_id"));}
    @Test public void searchProviderLoadsOwnKeyAndFailedSaveNeverLeaksSecrets()throws Exception{launch(ToolsActivity.class,false);awaitText("搜索互联网");onView(allOf(withText("配置"),withParent(hasDescendant(withText(containsString("web_search")))))).perform(scrollTo(),click());awaitText("保存配置");onView(withContentDescription("搜索服务商")).perform(click());onData(is("anysearch")).perform(click());awaitText("API 密钥（可选）");onView(withContentDescription("API 密钥（可选）")).check(matches(withText("masked-anysearch")));onView(withContentDescription("API 密钥（可选）")).perform(replaceText("new-secret"),closeSoftKeyboard());failConfig=true;tap("保存配置");awaitText("配置未保存");onView(withText(containsString("SECRET MUST NOT LEAK"))).check(doesNotExist());onView(withText("知道了")).perform(click());onView(withContentDescription("API 密钥（可选）")).check(matches(withText("new-secret")));assertEquals("anysearch",configSaved.optJSONObject("config").optString("provider"));capture("tools-config");}
    @Test public void pluginNumbersValidateBeforeNetworkAndAsyncIsScoped()throws Exception{launch(ToolsActivity.class,false);awaitText("异步执行已禁用");capture("tools");tap("异步执行已禁用");awaitText("异步执行已禁用");assertEquals("alpha",writes.get(0).getHeader("X-Agent-Id"));assertTrue(Json.object(Json.parse(writes.get(0).getBody().clone().readUtf8())).optBoolean("async_execution"));tap("启用");awaitText("保存配置");onView(withContentDescription("Timeout *")).perform(scrollTo(),replaceText("0"),closeSoftKeyboard());tap("保存配置");onView(withText("请检查配置")).check(matches(isDisplayed()));assertEquals(1,writes.size());}
    @After public void teardown()throws Exception{if(scenario!=null)scenario.close();main(()->{chat.base=oldBase;chat.token=oldToken;chat.agent=oldAgent;chat.loggedIn=oldLogin;chat.agents=oldAgents;});server.shutdown();}
}
