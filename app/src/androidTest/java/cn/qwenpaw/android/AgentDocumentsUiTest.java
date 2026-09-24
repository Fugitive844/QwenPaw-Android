package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.text.Spanned;
import io.noties.markwon.core.spans.StrongEmphasisSpan;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AgentDocumentsUiTest {
    private MockWebServer server;
    private ActivityScenario<AgentDocumentsActivity> scenario;
    private ChatController chat;
    private String oldBase,oldToken,oldAgent;
    private boolean oldLogin;
    private JSONArray oldAgents;
    private volatile boolean delayed;
    private volatile int soulStatus=404;
    private final AtomicInteger writes=new AtomicInteger();
    private final CountDownLatch alphaRequested=new CountDownLatch(1);
    private final AtomicReference<String> lastFileAgent=new AtomicReference<>();
    private final String sample="---\nsummary: hidden metadata\n---\n## 协作约定\n\n先核对 **设备状态**。\n\n- [x] 已确认\n- [ ] 待处理\n\n| 项目 | 说明 |\n| --- | --- |\n| 设备 | 在线 |\n\n```text\n示例配置\n```";
    private static MockResponse json(Object value){return new MockResponse().setHeader("Content-Type","application/json").setBody(value.toString());}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Before public void setup()throws Exception{
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){
            if(!request.getMethod().equals("GET"))writes.incrementAndGet();String path=request.getPath(),agent=request.getHeader("X-Agent-Id");
            if(path.equals("/api/workspace/files"))return json(Json.arr(Json.obj("filename","AGENTS.md","modified_time","2026-09-23T08:00:00Z"),Json.obj("filename","PROFILE.md"),Json.obj("filename","HEARTBEAT.md")));
            if(path.equals("/api/workspace/system-prompt-files"))return json(Json.arr("AGENTS.md","PROFILE.md"));
            if(path.equals("/api/config/heartbeat"))return json(Json.obj("enabled",false));
            if(path.startsWith("/api/workspace/files/")){
                lastFileAgent.set(agent);
                if(path.endsWith("AGENTS.md")){alphaRequested.countDown();return json(Json.obj("content",agent.equals("beta")?"# Beta 专属规则\n\n仅属于 Beta。":sample)).setBodyDelay(delayed&&agent.equals("alpha")?900:0,TimeUnit.MILLISECONDS);}
                if(path.endsWith("PROFILE.md"))return json(Json.obj("content","# 身份\n\n**名字**：测试助手\n\n## 用户资料\n\n称呼：同事"));
                if(path.endsWith("SOUL.md"))return json(Json.obj("detail","unavailable")).setResponseCode(soulStatus);
                if(path.endsWith("HEARTBEAT.md"))return json(Json.obj("content",""));
            }return json(new JSONObject()).setResponseCode(404);
        }});server.start();String base=server.url("/").toString().replaceAll("/$","");
        chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        main(()->{oldBase=chat.base;oldToken=chat.token;oldAgent=chat.agent;oldLogin=chat.loggedIn;oldAgents=chat.agents;chat.base=base;chat.token="document-test-token";chat.agent="alpha";chat.loggedIn=true;chat.agents=Json.arr(Json.obj("id","alpha","name","Alpha"),Json.obj("id","beta","name","Beta"));});
        scenario=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),AgentDocumentsActivity.class));awaitText("了解你的智能体");
    }
    private void awaitText(String text)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){try{onView(withText(containsString(text))).perform(scrollTo()).check(matches(isDisplayed()));return;}catch(AssertionError|RuntimeException e){Thread.sleep(100);}}throw new AssertionError("Not displayed: "+text);}
    private void open(String label){onView(withContentDescription("查看"+label)).perform(scrollTo(),click());}
    private void back(){onView(withContentDescription("返回个人中心或档案列表")).perform(click());}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(300);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"documents-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void purposeCardsAndRenderedMarkdownUseConsoleReadOnlyEndpoint()throws Exception{
        capture("overview");open("做事规则");awaitText("协作约定");
        onView(withTagValue(is((Object)"agent-document-content"))).check((view,error)->{assertNull(error);CharSequence text=((TextView)view).getText();assertTrue(text instanceof Spanned);assertTrue(((Spanned)text).getSpans(0,text.length(),StrongEmphasisSpan.class).length>0);assertFalse(text.toString().contains("**设备状态**"));assertFalse(text.toString().contains("hidden metadata"));});capture("markdown");
        onView(withText("Markdown 源码")).perform(scrollTo(),click());awaitText("hidden metadata");onView(withText("阅读模式")).perform(scrollTo(),click());awaitText("协作约定");assertEquals("alpha",lastFileAgent.get());assertEquals(0,writes.get());
    }
    @Test public void missingEmptyAndReadFailureAreDifferent()throws Exception{
        open("性格与沟通方式");awaitText("尚未创建这份档案");capture("missing");back();open("主动检查清单");awaitText("这份档案目前还没有正文");onView(withText(containsString("心跳未开启"))).perform(scrollTo()).check(matches(isDisplayed()));capture("heartbeat-empty");back();soulStatus=500;open("性格与沟通方式");awaitText("暂时无法读取正文");onView(withText("重新读取")).check(matches(isDisplayed()));assertEquals(0,writes.get());
    }
    @Test public void switchingAgentRejectsSlowOldDocument()throws Exception{
        delayed=true;open("做事规则");assertTrue(alphaRequested.await(5,TimeUnit.SECONDS));onView(withContentDescription("选择档案所属智能体")).perform(click());onData(allOf(is(instanceOf(String.class)),is("Beta · beta"))).perform(click());awaitText("Beta 专属规则");Thread.sleep(1200);onView(withTagValue(is((Object)"agent-document-content"))).check(matches(withText(containsString("Beta 专属规则"))));assertEquals("beta",lastFileAgent.get());assertEquals("alpha",chat.agent);assertEquals(0,writes.get());
    }
    @After public void teardown()throws Exception{if(scenario!=null)scenario.close();main(()->{chat.base=oldBase;chat.token=oldToken;chat.agent=oldAgent;chat.loggedIn=oldLogin;chat.agents=oldAgents;});server.shutdown();}
}
