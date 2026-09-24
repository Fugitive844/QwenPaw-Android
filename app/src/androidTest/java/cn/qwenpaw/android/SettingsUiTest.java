package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsUiTest {
    private MockWebServer server;
    private ActivityScenario<SettingsActivity> scenario;
    private ChatController chat;
    private String oldBase,oldToken,oldAgent,oldUser;
    private boolean oldLogin;
    private volatile JSONObject config;
    private final AtomicReference<JSONObject> saved=new AtomicReference<>(),envPatch=new AtomicReference<>();
    private final AtomicReference<String> header=new AtomicReference<>();
    private final AtomicInteger configGets=new AtomicInteger(),languageWrites=new AtomicInteger();
    private volatile boolean failSave=false,failTrend=false,failEnv=false;
    private static MockResponse json(Object body){return new MockResponse().setHeader("Content-Type","application/json").setBody(body.toString());}
    @Before public void setup()throws Exception{
        config=Json.obj("shell_command_timeout",60,"shell_command_executable","","auto_title_config",Json.obj("enabled",true,"timeout_seconds",30),"memory_manager_backend","remelight","approval_level","AUTO","llm_retry_enabled",true,"llm_max_retries",3,"llm_backoff_base",1,"llm_backoff_cap",10,"server_owned",Json.obj("untouched",42),"light_context_config",Json.obj("strategy","scroll","dialog_path","dialog","token_count_estimate_divisor",4,"context_compact_config",Json.obj("enabled",true,"compact_threshold_ratio",0.8,"reserve_threshold_ratio",0.1),"scroll_config",Json.obj("history_retention_days",30),"tool_result_pruning_config",Json.obj("enabled",true,"pruning_recent_n",2,"pruning_old_msg_max_bytes",3000,"pruning_recent_msg_max_bytes",50000,"offload_retention_days",30,"exempt_file_extensions",Json.arr(".md"),"exempt_tool_names",Json.arr("chat_with_agent")),"visual_compact_config",Json.obj("enabled",false,"effort","low")));
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest r){String path=r.getPath(),method=r.getMethod();header.set(r.getHeader("X-Agent-Id"));
            if(path.equals("/api/workspace/running-config")){if(method.equals("PUT")){saved.set(Json.object(Json.parse(r.getBody().readUtf8())));if(failSave)return json(Json.obj("detail","save offline")).setResponseCode(503);config=saved.get();}else configGets.incrementAndGet();return json(config);}
            if(path.equals("/api/workspace/language")){if(method.equals("PUT"))languageWrites.incrementAndGet();return json(Json.obj("language","zh"));}
            if(path.equals("/api/config/user-timezone"))return json(Json.obj("timezone","Asia/Shanghai"));
            if(path.equals("/api/agents/memory/backends"))return json(Json.arr(Json.obj("id","remelight","label","ReMe Light","available",true),Json.obj("id","missing","label","Unavailable","available",false)));
            if(path.equals("/api/workspace/project-directory/dirs"))return json(Json.obj("project_dirs",new JSONArray(),"workspace_dir","/workspace/alpha"));
            if(path.equals("/api/coding-mode"))return json(Json.obj("enabled",false));
            if(path.startsWith("/api/models/active"))return json(Json.obj("effective_max_input_length",100000));
            if(path.equals("/api/models")||path.equals("/api/skills")||path.equals("/api/loops"))return json(new JSONArray());
            if(path.contains("thinking"))return json(new JSONObject());
            if(path.equals("/api/envs")){if(method.equals("PATCH")){envPatch.set(Json.object(Json.parse(r.getBody().readUtf8())));if(failEnv)return json(Json.obj("detail","try again")).setResponseCode(503);}return json(Json.arr(Json.obj("key","SECRET_KEY","value","secret-value"),Json.obj("key","UNCHANGED","value","keep")));}
            if(path.equals("/api/envs/catalog"))return json(Json.arr(Json.obj("key","HOT_VALUE","default","3","effective_value","4","source","user","editable",true,"configured",true,"description_key","missing","mutability","hot_runtime"),Json.obj("key","READONLY_PORT","default","8088","effective_value","8088","source","default","editable",false,"readonly_reason_code","startup","mutability","startup_only")));
            if(path.startsWith("/api/token-usage/details"))return json(Json.arr(Json.obj("date",LocalDate.now().toString(),"provider_id","p","model","m","agent_id","alpha","prompt_tokens",1000,"completion_tokens",200,"cache_read_tokens",800,"cache_eligible_input_tokens",1000,"cache_observed_calls",1,"call_count",2)));
            if(path.startsWith("/api/agent-stats/llm-tool-trend"))return failTrend?json(Json.obj("detail","unavailable")).setResponseCode(503):json(Json.arr(Json.obj("date",LocalDate.now().toString(),"agent_llm_calls",1,"tool_calls",3)));
            return json(new JSONObject());
        }});server.start();
        chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        main(()->{oldBase=chat.base;oldToken=chat.token;oldAgent=chat.agent;oldUser=chat.username;oldLogin=chat.loggedIn;chat.base="http://127.0.0.1:"+server.getPort();chat.token="fixture-token";chat.agent="alpha";chat.username="fixture";chat.loggedIn=true;});
    }
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void launch(int page)throws Exception{Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SettingsActivity.class).putExtra("page",page);scenario=ActivityScenario.launch(intent);Thread.sleep(850);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<deadline){if(condition.getAsBoolean()){Thread.sleep(180);return;}Thread.sleep(40);}throw new AssertionError("Timed out");}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"settings-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @After public void tearDown()throws Exception{if(scenario!=null)scenario.close();Thread.sleep(300);main(()->{chat.base=oldBase;chat.token=oldToken;chat.agent=oldAgent;chat.username=oldUser;chat.loggedIn=oldLogin;});server.shutdown();}
    @Test public void retryRoundTripRetainsUnknownAndCollapsedConfig()throws Exception{
        launch(0);onView(allOf(withText("LLM 自动重试"),isAssignableFrom(android.widget.Button.class))).perform(click());onView(withHint("最大重试次数")).perform(scrollTo(),replaceText("5"),closeSoftKeyboard());onView(withText("保存配置")).perform(click());waitFor(()->saved.get()!=null);assertEquals(5,saved.get().optInt("llm_max_retries"));assertEquals(42,saved.get().optJSONObject("server_owned").optInt("untouched"));assertEquals("low",RuntimeConfig.get(saved.get(),RuntimeConfig.VISUAL+"effort"));assertEquals("alpha",header.get());assertTrue(configGets.get()>=2);capture("retry");
    }
    @Test public void retryDisabledRetainsValuesAndSaveFailureKeepsDraft()throws Exception{
        launch(0);onView(allOf(withText("LLM 自动重试"),isAssignableFrom(android.widget.Button.class))).perform(click());onView(withHint("最大重试次数")).perform(scrollTo(),replaceText("7"),closeSoftKeyboard());onView(withText("启用自动重试")).perform(scrollTo(),click());onView(withHint("最大重试次数")).check(matches(not(isEnabled())));failSave=true;onView(withText("保存配置")).perform(click());waitFor(()->saved.get()!=null);Thread.sleep(400);onView(withHint("最大重试次数")).check(matches(withText("7")));failSave=false;saved.set(null);onView(withText("保存配置")).perform(click());waitFor(()->saved.get()!=null);assertFalse(saved.get().optBoolean("llm_retry_enabled"));assertEquals(7,saved.get().optInt("llm_max_retries"));
    }
    @Test public void contextVisualSettingsAndApprovalArePersisted()throws Exception{
        launch(0);onView(allOf(withText("上下文管理"),isAssignableFrom(android.widget.Button.class))).perform(click());onView(withText("上下文压缩 ▾")).perform(scrollTo(),click());onView(withHint("上下文压缩阈值比例")).perform(scrollTo(),replaceText("0.7"),closeSoftKeyboard());onView(withText(containsString("70000"))).perform(scrollTo()).check(matches(isDisplayed()));capture("context");onView(withText("视觉压缩 ▾")).perform(scrollTo(),click());onView(withText("启用 Visual Compact")).perform(scrollTo(),click());onView(withText("高")).perform(scrollTo(),click());onView(allOf(withText("工具审批模式"),isAssignableFrom(android.widget.Button.class))).perform(scrollTo(),click());onView(withText(startsWith("智能模式\n"))).perform(scrollTo(),click());onView(withText("保存配置")).perform(click());waitFor(()->saved.get()!=null);assertEquals("SMART",saved.get().optString("approval_level"));assertEquals("high",RuntimeConfig.get(saved.get(),RuntimeConfig.VISUAL+"effort"));assertEquals(0.7,(Double)RuntimeConfig.get(saved.get(),RuntimeConfig.COMPACT+"compact_threshold_ratio"),0);capture("approval");
    }
    @Test public void environmentSecretsSearchDuplicateValidationAndPatch()throws Exception{
        launch(1);onView(withText("secret-value")).check(doesNotExist());capture("environment");onView(withText("添加变量")).perform(scrollTo(),click());onView(withHint("键")).perform(replaceText("secret_key"));onView(withHint("值")).perform(replaceText("new"),closeSoftKeyboard());onView(withText("立即应用")).perform(click());assertNull(envPatch.get());onView(withHint("键")).perform(replaceText("NEW_SETTING"),closeSoftKeyboard());onView(withText("立即应用")).perform(click());waitFor(()->envPatch.get()!=null);assertEquals(1,envPatch.get().length());assertEquals("new",envPatch.get().optString("NEW_SETTING"));
    }
    @Test public void tokensRemainVisibleWhenIndependentTrendFails()throws Exception{
        failTrend=true;launch(2);onView(allOf(withText("1,200"),isDisplayed())).check(matches(isDisplayed()));onView(allOf(withText("80.0%"),isDisplayed())).check(matches(isDisplayed()));capture("token");onView(withText("重试调用趋势")).perform(scrollTo(),click());onView(withText("按模型 ▾")).perform(scrollTo(),click());onView(allOf(withText("p:m"),not(isAssignableFrom(android.widget.CheckBox.class)))).perform(scrollTo()).check(matches(isDisplayed()));
    }
    @Test public void languageChangeRequiresItsOwnConfirmation()throws Exception{
        launch(0);onView(withText(startsWith("智能体语言 ·"))).perform(scrollTo(),click());onView(withText("English")).perform(click());onView(withText("取消")).perform(click());assertEquals(0,languageWrites.get());capture("react");
    }
}




