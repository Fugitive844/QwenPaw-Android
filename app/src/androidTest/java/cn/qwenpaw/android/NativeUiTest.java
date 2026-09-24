package cn.qwenpaw.android;

import android.graphics.Bitmap;
import android.view.View;
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
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NativeUiTest {
    private MockWebServer server;
    private ActivityScenario<MainActivity> scenario;
    private ChatController c;
    private volatile JSONObject chat;
    private final AtomicReference<JSONObject> sentBody=new AtomicReference<>();
    private volatile JSONArray skillItems=Json.arr(Json.obj("name","demo-skill","enabled",true,"channels",Json.arr("all")));
    private final AtomicInteger skillCalls=new AtomicInteger();
    private volatile boolean failSkills=false;
    private final AtomicReference<JSONObject> approvalBody=new AtomicReference<>();
    private final AtomicBoolean pending=new AtomicBoolean(false);
    private volatile JSONArray historyItems;
    private static MockResponse json(Object o){return new MockResponse().setHeader("Content-Type","application/json").setBody(o.toString());}
    private static JSONObject message(String id,String type,String text){return Json.obj("id",id,"object","message","role","assistant","type",type,"status","completed","content",Json.arr(Json.obj("type","text","text",text)));}
    @Before public void setup()throws Exception{
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){
            String path=request.getPath();
            if(path.equals("/api/auth/status"))return json(Json.obj("enabled",false));
            if(path.equals("/api/agents"))return json(Json.obj("agents",Json.arr(Json.obj("id","default","name","Default","enabled",true,"workspace_dir","/test"))));
            if(path.startsWith("/api/chats/thinking-default") || path.endsWith("/thinking"))return json(Json.obj("model","test-model","model_name","测试模型","control",Json.obj("efforts",Json.arr("low","high")),"value",Json.obj("level","inherit")));
            if(path.equals("/api/workspace/running-config"))return json(Json.obj("approval_level","AUTO"));
            if(path.startsWith("/api/models/active"))return json(Json.obj("active_llm",Json.obj("model","test-model"),"effective_max_input_length",10000));
            if(path.equals("/api/models"))return json(Json.arr(Json.obj("id","hub-managed","name","Hub","models",Json.arr(Json.obj("id","test-model","name","测试模型")))));
            if(path.equals("/api/loops"))return json(Json.arr(Json.obj("id","default","source","builtin","slash_command",""),Json.obj("id","goal","source","builtin","slash_command","goal")));
            if(path.startsWith("/api/loops/status"))return json(Json.obj("state","idle"));
            if(path.equals("/api/skills")){skillCalls.incrementAndGet();return failSkills?json(Json.obj("detail","offline")).setResponseCode(503):json(skillItems);}
            if(path.equals("/api/chats") && request.getMethod().equals("POST")){chat=Json.object(Json.parse(request.getBody().readUtf8()));Json.put(chat,"id","test-chat");return json(chat);}
            if(path.startsWith("/api/chats?"))return json(historyItems!=null?historyItems:chat==null?new JSONArray():Json.arr(chat));
            if(path.equals("/api/chats/test-chat"))return json(Json.obj("messages",Json.arr(message("reply","message","测试回复 **成功**")),"status","idle"));
            if(path.startsWith("/api/approval/list"))return json(Json.obj("pending_approvals",pending.get()&&chat!=null?Json.arr(Json.obj("request_id","approval-1","root_session_id",chat.optString("session_id"),"owner_agent_id","default","agent_id","default","tool_name","write_file","tool_params",Json.obj("path","/tmp/test.txt"))):new JSONArray()));
            if(path.startsWith("/api/approval/approve")){approvalBody.set(Json.object(Json.parse(request.getBody().readUtf8())));pending.set(false);return json(Json.obj("success",true));}
            if(path.equals("/api/console/chat")){
                JSONObject body=Json.object(Json.parse(request.getBody().readUtf8()));sentBody.set(body);
                String query=Json.object(Json.array(Json.object(Json.array(body.opt("input")).opt(0)).opt("content")).opt(0)).optString("text");
                JSONObject usage=Json.obj("type","turn_usage","usage",Json.obj("total_tokens",1200,"prompt_tokens",1000,"completion_tokens",200,"session_cache_observed",true,"session_cache_read_tokens",800,"session_cache_eligible_input_tokens",1000,"session_cache_hit_rate",80),"context_usage",Json.obj("estimated_tokens",query.equals("/compact")?400:8200,"max_input_length",10000));
                return new MockResponse().setHeader("Content-Type","text/event-stream").setBody("data: "+message("reason","reasoning","正在思考")+"\n\ndata: "+message(query.equals("/compact")?"compact-reply":"reply","message",query.equals("/compact")?"压缩完成：上下文已整理":"测试回复 **成功**")+"\n\ndata: {\"object\":\"response\",\"status\":\"completed\"}\n\ndata: "+usage+"\n\n").throttleBody(80,30,TimeUnit.MILLISECONDS);
            }
            if(path.equals("/api/slow"))return json(Json.obj("old",true)).setBodyDelay(700,TimeUnit.MILLISECONDS);
            if(path.equals("/api/unauthorized"))return json(Json.obj("detail","expired")).setResponseCode(401);
            return json(new JSONObject());
        }});server.start();
        c=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        main(()->{c.logout();c.base="";c.username="";});scenario=ActivityScenario.launch(MainActivity.class);
    }
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void waitFor(java.util.function.BooleanSupplier ready)throws Exception{
        long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){AtomicBoolean result=new AtomicBoolean();main(()->result.set(ready.getAsBoolean()));if(result.get())return;Thread.sleep(40);}throw new AssertionError("Timed out waiting for application state: "+c.status);
    }
    private void login()throws Exception{
        onView(withHint("IP 或域名")).perform(replaceText("127.0.0.1"));
        onView(withText("高级连接选项")).perform(scrollTo(),click());
        onView(withHint("端口（留空使用默认值）")).perform(scrollTo(),replaceText(Integer.toString(server.getPort())),closeSoftKeyboard());
        onView(isAssignableFrom(android.widget.Spinner.class)).perform(scrollTo(),click());onData(allOf(is(instanceOf(String.class)),is("http"))).perform(click());
        onView(withText("连接服务器")).perform(scrollTo(),click());waitFor(()->c.loggedIn && c.agents.length()>0 && c.thinking.optString("model").equals("test-model"));
    }
    private void screenshot(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(450);
        name+=InstrumentationRegistry.getArguments().getString("screenshotSuffix","");
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void commandButtonPreservesDraftAndDoesNotOpenKeyboard()throws Exception{
        login();onView(withHint("发送消息…")).perform(click(),replaceText("已有草稿"));
        onView(withContentDescription("快捷命令")).perform(click());onView(withText("快捷命令")).check(matches(isDisplayed()));
        waitFor(()->c.commandsLoading==0);assertKeyboardHidden();screenshot("commands");
        onView(withText("技能")).perform(click());onView(withText("demo-skill")).perform(click());
        assertEquals("/demo-skill 已有草稿",c.draft);scenario.recreate();onView(withHint("发送消息…")).check(matches(withText("/demo-skill 已有草稿")));
    }
    private void assertKeyboardHidden()throws Exception{
        Thread.sleep(500);AtomicBoolean hidden=new AtomicBoolean();scenario.onActivity(a->{View decor=a.getWindow().getDecorView();android.graphics.Rect frame=new android.graphics.Rect();decor.getWindowVisibleDisplayFrame(frame);hidden.set(decor.getRootView().getHeight()-frame.bottom<150*a.getResources().getDisplayMetrics().density);});assertTrue("Command menu must not bring up IME",hidden.get());
    }
    @Test public void commandCatalogScrollsSearchesAndRefreshesFailures()throws Exception{
        JSONArray many=new JSONArray();for(int i=0;i<80;i++)many.put(Json.obj("name",String.format("skill-%02d",i),"enabled",true,"description","用于测试完整技能列表与纵向滚动"));skillItems=many;
        login();waitFor(()->c.commandsLoading==0);onView(withContentDescription("快捷命令")).perform(click());waitFor(()->c.commandsLoading==0);
        onView(withText("技能")).perform(click());onData(anything()).inAdapterView(withContentDescription("命令列表")).atPosition(79).check(matches(isDisplayed()));screenshot("commands-skills");assertKeyboardHidden();
        onView(withHint("搜索命令、技能或说明")).perform(click(),replaceText("skill-79"));onView(allOf(withText("skill-79"),not(isAssignableFrom(android.widget.EditText.class)))).check(matches(isDisplayed()));screenshot("commands-search-keyboard");onView(withHint("搜索命令、技能或说明")).perform(closeSoftKeyboard());onView(allOf(withText("skill-79"),not(isAssignableFrom(android.widget.EditText.class)))).perform(click());assertEquals("/skill-79 ",c.draft);
        failSkills=true;onView(withContentDescription("快捷命令")).perform(click());waitFor(()->c.commandsLoading==0&&!c.commandsError.isEmpty());onView(withText("技能加载失败 · 点击此处重试")).check(matches(isDisplayed()));
        failSkills=false;onView(withText("技能加载失败 · 点击此处重试")).perform(click());waitFor(()->c.commandsLoading==0&&c.commandsError.isEmpty());assertTrue(skillCalls.get()>=4);
    }
    @Test public void contextUsageAndCompactUseCurrentIdentityKeepDraftAndFiles()throws Exception{
        login();main(()->{c.draft="hello";c.send();});waitFor(()->c.chat!=null&&!c.running&&!c.busy&&c.transcript.metrics!=null);
        assertEquals(8200,c.transcript.metrics.tokens());main(()->{c.draft="保留这份草稿";c.attachments.add(Json.obj("type","file","file_name","待发资料.md","file_url","file:///test/pending.md"));c.emit();});
        onView(withContentDescription("上下文用量")).perform(click());onView(withText("82.0%")).check(matches(isDisplayed()));screenshot("context-usage");
        onView(withText("压缩上下文")).perform(scrollTo(),click());waitFor(()->c.running);main(()->c.contextCommand("/compact"));waitFor(()->!c.running&&!c.busy&&c.transcript.metrics.tokens()==400);
        assertTrue("Command reply absent from persisted history must remain visible",c.transcript.rows().stream().anyMatch(r->r.text.contains("压缩完成")));assertEquals("保留这份草稿",c.draft);assertEquals(1,c.attachments.size());assertEquals(c.chat.optString("session_id"),sentBody.get().optString("session_id"));assertEquals(c.chat.optString("user_id"),sentBody.get().optString("user_id"));assertEquals("console",sentBody.get().optString("channel"));
        JSONArray blocks=Json.array(Json.object(Json.array(sentBody.get().opt("input")).opt(0)).opt("content"));assertEquals(1,blocks.length());assertEquals("/compact",blocks.getJSONObject(0).getString("text"));assertEquals("AUTO",sentBody.get().getJSONObject("request_context").getString("approval_level"));
        onView(withContentDescription("上下文用量")).perform(click());onView(withText("4.0%")).check(matches(isDisplayed()));screenshot("context-after-compact");onView(withContentDescription("关闭面板")).perform(click());main(c::newChat);assertNull(c.transcript.metrics);
    }
    @Test public void approvalModesHaveDistinctColorsAndPersist()throws Exception{
        login();onView(withText("自动模式")).perform(click());screenshot("approval-colors");onView(withText("严格模式")).perform(click());assertEquals("STRICT",c.approvalLevel);scenario.recreate();onView(withText("严格模式")).check(matches(isDisplayed()));screenshot("strict-mode");
        onView(withText("严格模式")).perform(click());onView(withText("智能模式")).perform(click());assertEquals("SMART",c.approvalLevel);
        onView(withText("智能模式")).perform(click());onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(3).perform(click());assertEquals("OFF",c.approvalLevel);
    }
    @Test public void sendsAndCompletesWithoutWebChat()throws Exception{
        login();onView(withHint("发送消息…")).perform(replaceText("你好"),closeSoftKeyboard());onView(withContentDescription("发送")).perform(click());waitFor(()->c.chat!=null&&!c.running&&!c.busy&&c.transcript.rows().size()>0);
        assertTrue(c.transcript.rows().stream().anyMatch(r->r.text.contains("测试回复")));assertEquals("",c.draft);screenshot("native-response");
    }
    @Test public void approvalsRoundTripWithExactRootSession()throws Exception{
        login();main(()->{c.draft="hello";c.send();});waitFor(()->c.chat!=null&&!c.running&&!c.busy);pending.set(true);main(c::pollApprovals);waitFor(()->c.approvals.length()==1);
        screenshot("native-approval");onView(withText("批准")).perform(click());waitFor(()->approvalBody.get()!=null);
        assertEquals(c.chat.optString("session_id"),approvalBody.get().optString("session_id"));assertEquals("exact",approvalBody.get().optString("scope"));
    }
    @Test public void staleRequestCannotMutateNewWorkspace()throws Exception{
        login();AtomicBoolean staleApplied=new AtomicBoolean(false);main(()->{c.task(a->a.call("GET","/slow",null),r->staleApplied.set(true));c.newChat();});Thread.sleep(1000);assertFalse(staleApplied.get());
    }
    @Test public void manualPauseFreezesThinkingExpansion()throws Exception{
        login();main(()->{c.transcript.clear();c.transcript.accept(Json.obj("object","message","id","r","type","reasoning","role","assistant","content",Json.arr(Json.obj("type","text","text","思考中"))));c.pauseFollow();c.transcript.accept(Json.obj("object","message","id","r","status","completed"));});
        assertFalse(c.autoFollow);assertEquals(Boolean.TRUE,c.expanded.get("r:0"));
    }
    @Test public void completedTurnCollapsesTogetherAndFilesStayVisible()throws Exception{
        login();main(()->{
            c.transcript.history(Json.arr(
                Json.obj("id","user","role","user","content","帮我整理一下今天的工作"),
                message("think","reasoning","先检查记录，再整理重点。"),
                Json.obj("id","tool","type","plugin_call","role","assistant","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","one","name","send_file_to_user","arguments","{}")))),
                Json.obj("id","output","type","plugin_call_output","role","assistant","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","one","output",Json.arr(Json.obj("type","data","name","今日工作.md","source",Json.obj("url","file:///test/today.md"))))))),
                message("answer","message","已经为你整理好了。\n\n**今天的三个重点**\n\n- 完成方案评审\n- 梳理待办事项\n- 准备明天的讨论\n\n工作清单已保存，可随时下载。")
            ));c.running=false;c.autoFollow=true;c.expanded.clear();c.emit();
        });
        onView(isAssignableFrom(androidx.recyclerview.widget.RecyclerView.class)).perform(swipeDown());
        onView(withContentDescription("展开本轮过程")).check(matches(isDisplayed()));onView(withText("今日工作.md")).check(matches(isDisplayed()));
        assertEquals(0,ProcessGroups.project(c.transcript.rows(),false,c.expanded).stream().filter(ProcessGroups::process).count());screenshot("redesign-chat");
        onView(withContentDescription("展开本轮过程")).perform(click());assertFalse(c.autoFollow);assertEquals(Boolean.TRUE,c.expanded.get("process:0"));
        onView(withText("回到最新")).perform(click());assertTrue(c.autoFollow);assertEquals(0,ProcessGroups.project(c.transcript.rows(),false,c.expanded).stream().filter(ProcessGroups::process).count());
    }
    @Test public void modernPanelsAndKeyboardKeepActionsAccessible()throws Exception{
        onView(withText("连接你的智能体")).check(matches(isDisplayed()));screenshot("redesign-login");login();waitFor(()->c.providers.length()>0&&c.status.equals("已连接"));screenshot("redesign-welcome");
        onView(withText("测试模型")).perform(click());onView(withText("模型设置")).check(matches(isDisplayed()));screenshot("redesign-models");
        onView(withHint("搜索模型")).perform(click(),replaceText("测试"));onView(withContentDescription("关闭面板")).check(matches(isDisplayed()));screenshot("redesign-model-keyboard");onView(withHint("搜索模型")).perform(closeSoftKeyboard());onView(withContentDescription("关闭面板")).perform(click());
        onView(withText("自动模式")).perform(click());onView(withText("严格模式")).check(matches(isDisplayed()));screenshot("redesign-modes");onView(withContentDescription("关闭面板")).perform(click());
        onView(withHint("发送消息…")).perform(click(),replaceText("这是一段准备发送的消息"));onView(withContentDescription("发送")).check(matches(isDisplayed()));screenshot("redesign-keyboard");onView(withHint("发送消息…")).perform(closeSoftKeyboard());
        chat=Json.obj("id","test-chat","name","今天的灵感与计划","updated_at","2026-09-22","session_id","test-session");onView(withContentDescription("历史对话")).perform(click());waitFor(()->c.chats.length()>0);onView(withText("你的对话")).check(matches(isDisplayed()));screenshot("redesign-history");onView(withContentDescription("关闭面板")).perform(click());
    }
    @Test public void unauthorizedReturnsToLogin()throws Exception{
        login();main(()->c.task(a->a.call("GET","/unauthorized",null),r->{}));waitFor(()->!c.loggedIn);onView(withText("连接服务器")).check(matches(isDisplayed()));
    }
    private androidx.recyclerview.widget.RecyclerView messageList(android.app.Activity activity){
        return findList((android.view.ViewGroup)activity.findViewById(android.R.id.content));
    }
    private androidx.recyclerview.widget.RecyclerView findList(android.view.ViewGroup parent){
        for(int i=0;i<parent.getChildCount();i++){View child=parent.getChildAt(i);if(child instanceof androidx.recyclerview.widget.RecyclerView list)return list;if(child instanceof android.view.ViewGroup group){var list=findList(group);if(list!=null)return list;}}return null;
    }
    private void assertLatestBottomVisible()throws Exception{
        AtomicBoolean visible=new AtomicBoolean();long deadline=System.currentTimeMillis()+5000;
        do{scenario.onActivity(a->{var list=messageList(a);var layout=(androidx.recyclerview.widget.LinearLayoutManager)list.getLayoutManager();View last=layout.findViewByPosition(list.getAdapter().getItemCount()-1);visible.set(last!=null&&layout.getDecoratedBottom(last)<=list.getHeight()-list.getPaddingBottom()+2);});if(visible.get())return;Thread.sleep(40);}while(System.currentTimeMillis()<deadline);
        fail("Latest reply bottom must remain inside viewport while following");
    }
    @Test public void finalReplyKeepsFollowingAfterToolsAndReadingPause()throws Exception{
        login();main(()->{c.transcript.clear();c.transcript.addUser("long-user",Json.arr(Json.obj("type","text","text","整理完整的设备分析")));c.running=true;
            for(int i=0;i<3;i++){c.transcript.accept(message("reason-"+i,"reasoning","检查第 "+i+" 项设备数据"));c.transcript.accept(Json.obj("object","message","id","tool-"+i,"role","assistant","type","plugin_call","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","call-"+i,"name","查询设备","arguments","{}")))));}
            c.emit();});assertLatestBottomVisible();
        for(int i=0;i<25;i++){final int n=i;main(()->{c.transcript.accept(Json.obj("object","content","msg_id","final-long","index",0,"type","text","delta",true,"text","\n\n第 "+n+" 段：设备状态与趋势分析。本段用于验证最终回复持续增长时能跟到文本末尾，不能停在前面的思考或工具卡片。"));c.emit();});Thread.sleep(40);}assertLatestBottomVisible();
        onView(withText(org.hamcrest.Matchers.containsString("第 24 段"))).perform(new androidx.test.espresso.ViewAction(){
            public org.hamcrest.Matcher<View> getConstraints(){return isDisplayed();}public String getDescription(){return "Tap the visible portion of a long reply";}
            public void perform(androidx.test.espresso.UiController controller,View view){new androidx.test.espresso.action.GeneralClickAction(androidx.test.espresso.action.Tap.SINGLE,v->{android.graphics.Rect rect=new android.graphics.Rect();v.getGlobalVisibleRect(rect);return new float[]{rect.centerX(),rect.centerY()};},androidx.test.espresso.action.Press.FINGER).perform(controller,view);}
        });assertTrue("Simple text tap must not disable follow",c.autoFollow);
        onView(isAssignableFrom(androidx.recyclerview.widget.RecyclerView.class)).perform(swipeDown());assertFalse(c.autoFollow);
        AtomicReference<Integer> top=new AtomicReference<>();scenario.onActivity(a->{var list=messageList(a);top.set(((androidx.recyclerview.widget.LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition());});
        main(()->{c.transcript.accept(Json.obj("object","content","msg_id","final-long","index",0,"type","text","delta",true,"text","\n\n暂停阅读时新增的一段。"));c.emit();});Thread.sleep(150);
        scenario.onActivity(a->assertEquals(top.get().intValue(),((androidx.recyclerview.widget.LinearLayoutManager)messageList(a).getLayoutManager()).findFirstVisibleItemPosition()));
        onView(withText("回到最新")).perform(click());assertTrue(c.autoFollow);assertLatestBottomVisible();
        onView(withHint("发送消息…")).perform(click(),replaceText("保留的草稿"));assertLatestBottomVisible();onView(withHint("发送消息…")).perform(closeSoftKeyboard());
        scenario.recreate();assertLatestBottomVisible();
        main(()->{c.running=false;c.transcript.terminal=true;c.emit();});assertLatestBottomVisible();screenshot("v04-long-final-follow");
        main(()->{c.transcript.addUser("next-user",Json.arr(Json.obj("type","text","text","再看下一轮")));c.running=true;c.transcript.accept(message("next-answer","message","下一轮的最新回复"));c.emit();});assertLatestBottomVisible();
    }
    @Test public void welcomeShowsThreeRefreshesAndPreservesDraft()throws Exception{
        login();assertEquals(50,c.promptCatalog.size());assertEquals(3,c.welcomePrompts.size());var previous=new java.util.ArrayList<>(c.welcomePrompts);
        onView(withContentDescription("语音输入")).check(doesNotExist());onView(withContentDescription("换一组提示词")).perform(scrollTo(),click());assertTrue(java.util.Collections.disjoint(previous,c.welcomePrompts));
        var choices=new java.util.ArrayList<>(c.welcomePrompts);scenario.recreate();assertEquals(choices,c.welcomePrompts);
        main(()->{c.draft="已有草稿";c.attachments.add(Json.obj("file_name","保留.txt","file_url","/files/test"));c.emit();});
        onView(withContentDescription("推荐提问："+choices.get(0).title())).perform(scrollTo(),click());assertEquals("已有草稿\n"+choices.get(0).text(),c.draft);assertEquals(1,c.attachments.size());assertNull(sentBody.get());
        main(c::newChat);assertTrue(java.util.Collections.disjoint(choices,c.welcomePrompts));screenshot("v04-welcome");
    }
    @Test public void loopDescriptionsAndHistoryOrderingAreVisible()throws Exception{
        login();waitFor(()->c.commandsLoading==0);main(()->{c.loops=Json.arr(Json.obj("id","goal","source","builtin"),Json.obj("id","plugin","name","插件模式","source","plugin","description_i18n",Json.obj("zh","插件中文说明")));c.emit();});
        onView(withText("Loop · 默认")).perform(click());onView(withText("标准的受控智能体 Loop。")).check(matches(isDisplayed()));onView(withText("持续推进一个具体且可验证的目标。")).check(matches(isDisplayed()));
        onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(2).check(matches(hasDescendant(withText("插件中文说明"))));screenshot("v04-loop");
        onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(1).perform(click());assertEquals("goal",c.selectedLoop.optString("id"));
        historyItems=Json.arr(Json.obj("id","old","name","历史旧","updated_at","2026-09-20"),Json.obj("id","new","name","历史新","updated_at","2026-09-23"),Json.obj("id","pin","name","历史置顶","pinned",true,"updated_at","2026-09-19"));
        onView(withContentDescription("历史对话")).perform(click());onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(0).onChildView(withText("★ 历史置顶")).check(matches(isDisplayed()));onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(1).onChildView(withText("历史新")).check(matches(isDisplayed()));
        onView(withHint("搜索历史对话")).perform(replaceText("历史"),closeSoftKeyboard());onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(1).onChildView(withText("历史新")).check(matches(isDisplayed()));screenshot("v04-history");onView(withText("查看已归档")).perform(click());onData(anything()).inAdapterView(isAssignableFrom(android.widget.ListView.class)).atPosition(1).onChildView(withText("历史新")).check(matches(isDisplayed()));
    }
    @Test public void navigationPanelsFillHeightAndThinkingFollowsModelCapabilities()throws Exception{
        login();
        AtomicInteger optionsX=new AtomicInteger();
        onView(withContentDescription("更多选项")).check((view,error)->{assertNull(error);int[] p=new int[2];view.getLocationOnScreen(p);optionsX.set(p[0]);});
        onView(withContentDescription("历史对话")).check((view,error)->{assertNull(error);int[] p=new int[2];view.getLocationOnScreen(p);assertTrue(p[0]>optionsX.get());}).perform(click());
        assertFullHeightPanel();onView(withContentDescription("关闭面板")).perform(click());
        onView(withContentDescription("更多选项")).perform(click());assertFullHeightPanel();onView(withText("个人中心")).check(matches(isDisplayed()));onView(withText("思考强度")).check(doesNotExist());onView(withContentDescription("关闭面板")).perform(click());
        main(()->{c.sessionModels=true;c.thinkingLoading=false;Json.put(c.thinking,"model_key","test:test-model");Json.put(c.thinking,"control",Json.obj("kind","effort","efforts",Json.arr("low","high")));c.emit();});
        onView(withText("测试模型")).perform(click());assertFullHeightPanel();onView(withContentDescription("当前模型思考强度")).check(matches(isDisplayed())).perform(click());
        onView(withText("低")).check(matches(isDisplayed()));onView(withText("高")).check(matches(isDisplayed()));onView(withText("中")).check(doesNotExist());onView(withText("关闭思考")).check(doesNotExist());onView(withContentDescription("关闭面板")).perform(click());
        main(()->{Json.put(c.thinking,"control",Json.obj("kind","unsupported"));c.emit();});
        onView(withText("测试模型")).perform(click());onView(withContentDescription("当前模型思考强度")).check(matches(withEffectiveVisibility(Visibility.GONE)));onView(withContentDescription("关闭面板")).perform(click());
    }
    private void assertFullHeightPanel(){
        onView(withId(com.google.android.material.R.id.design_bottom_sheet)).check((view,error)->{assertNull(error);assertTrue("Panel must use the available screen height",view.getHeight()>=view.getRootView().getHeight()*0.85);});
    }
    @Test public void offlineMermaidRendersSvg()throws Exception{
        login();AtomicReference<DiagramView> view=new AtomicReference<>();scenario.onActivity(a->{DiagramView d=new DiagramView(a,"graph LR\n A[Android] --> B[OK]");view.set(d);new android.app.AlertDialog.Builder(a).setView(d).show();});
        AtomicBoolean rendered=new AtomicBoolean(false);long end=System.currentTimeMillis()+15000;
        while(!rendered.get()&&System.currentTimeMillis()<end){main(()->view.get().evaluateJavascript("document.querySelectorAll('#diagram svg').length",value->rendered.set(value.equals("1"))));Thread.sleep(150);}
        AtomicReference<String> diagnostics=new AtomicReference<>("");main(()->view.get().evaluateJavascript("JSON.stringify({errors:window.diagramErrors,text:document.body.innerText,engine:typeof mermaid,ua:navigator.userAgent})",diagnostics::set));Thread.sleep(100);
        assertTrue("Bundled Mermaid must render without network: "+diagnostics.get(),rendered.get());screenshot("native-mermaid");
    }
    @After public void teardown()throws Exception{if(scenario!=null)scenario.close();main(c::logout);if(server!=null)server.shutdown();}
}
