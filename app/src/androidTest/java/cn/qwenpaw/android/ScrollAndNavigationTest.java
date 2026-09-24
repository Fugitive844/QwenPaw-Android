package cn.qwenpaw.android;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/** Actual RecyclerView gestures and delayed HTTP responses; never uses a live account. */
@RunWith(AndroidJUnit4.class)
public class ScrollAndNavigationTest {
    private MockWebServer server;
    private ActivityScenario<MainActivity> scenario;
    private PawApplication app;
    private ChatController chat;
    private volatile CountDownLatch historyGate,profileGate,poolGate;
    private volatile boolean historyFails,poolFails;
    private final AtomicInteger historyCalls=new AtomicInteger(),profileCalls=new AtomicInteger(),poolCalls=new AtomicInteger();
    private final AtomicInteger accountCreated=new AtomicInteger(),poolCreated=new AtomicInteger();
    private final AtomicReference<Activity> child=new AtomicReference<>();
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle b){
            if(a instanceof AccountActivity){accountCreated.incrementAndGet();child.set(a);}
            if(a instanceof SkillsActivity){poolCreated.incrementAndGet();child.set(a);}
        }
        public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){}
        public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){}
        public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
    };
    private static MockResponse json(String value){return new MockResponse().setHeader("Content-Type","application/json").setBody(value);}
    private static void hold(CountDownLatch gate)throws InterruptedException{if(gate!=null)gate.await(15,TimeUnit.SECONDS);}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private void until(BooleanSupplier condition)throws Exception{
        long deadline=System.currentTimeMillis()+12000;
        while(System.currentTimeMillis()<deadline){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(40);}
        fail("Timed out waiting for test state");
    }
    private static boolean flag(Object target,String name){
        try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.getBoolean(target);}catch(Exception e){throw new AssertionError(e);}
    }
    private static Object field(Object target,String name){
        try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}
    }
    private void startBackgroundHistory(){main(()->call(chat,"refreshChats",new Class<?>[]{boolean.class,boolean.class,Runnable.class,Runnable.class},false,true,null,null));}
    private static void call(Object target,String name,Class<?>[] types,Object...args){
        try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}catch(Exception e){throw new AssertionError(e);}
    }
    @Before public void setup()throws Exception{
        app=(PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertEquals("Use the isolated verification variant", "cn.qwenpaw.android.scrollverify",app.getPackageName());
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){
            @Override public MockResponse dispatch(RecordedRequest request)throws InterruptedException{
                String path=request.getPath();
                if(path.equals("/api/console/chat")){
                    StringBuilder events=new StringBuilder();int chunk=0;
                    for(int i=0;i<40;i++){
                        String event="data: "+Json.obj("object","content","msg_id","long-reply","index",0,"type","text","delta",true,"text",String.format(java.util.Locale.ROOT,"\n\nSSE-%02d：网络持续输出的新段落，用于验证底部跟随。\n",i))+"\n\n";
                        events.append(event);chunk=event.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    }
                    return new MockResponse().setHeader("Content-Type","text/event-stream").setBody(events.toString()).throttleBody(chunk,500,TimeUnit.MILLISECONDS);
                }
                if(path.equals("/api/chats/stream-test"))return json("{\"status\":\"idle\",\"messages\":[]}");
                if(path.startsWith("/api/chats?")){historyCalls.incrementAndGet();hold(historyGate);return historyFails?json("{}").setResponseCode(503):json("[]");}
                if(path.equals("/api/auth/status")){profileCalls.incrementAndGet();hold(profileGate);return json("{\"enabled\":false}");}
                if(path.equals("/api/skills/pool")||path.equals("/api/skills/pool/refresh")){poolCalls.incrementAndGet();hold(poolGate);return poolFails?json("{}").setResponseCode(503):json("[]");}
                return json("{}");
            }
        });server.start();String base="http://127.0.0.1:"+server.getPort();
        main(()->{
            app.clearCrash();app.startupUpdateCheckStarted.set(true);chat=app.ensureChat();chat.logout();
            chat.base=base;chat.loggedIn=true;chat.status="已连接";
            chat.agents=Json.arr(Json.obj("id","default","name","Default"));
        });
        app.registerActivityLifecycleCallbacks(lifecycle);
        scenario=ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(a->chat.detach(a));
        until(()->!flag(chat,"chatsLoading"));historyCalls.set(0);
    }
    @After public void teardown()throws Exception{
        for(CountDownLatch gate:new CountDownLatch[]{historyGate,profileGate,poolGate})if(gate!=null)gate.countDown();
        main(()->{Activity a=child.get();if(a!=null&&!a.isFinishing())a.finish();});
        if(scenario!=null)scenario.close();
        app.unregisterActivityLifecycleCallbacks(lifecycle);
        main(()->chat.logout());server.shutdown();
    }
    private RecyclerView list(View view){
        if(view instanceof RecyclerView r)return r;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){RecyclerView found=list(group.getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private void assertBottom()throws Exception{
        AtomicBoolean bottom=new AtomicBoolean();
        try{until(()->{scenario.onActivity(a->bottom.set(!list(a.findViewById(android.R.id.content)).canScrollVertically(1)));return bottom.get();});}
        catch(AssertionError e){scrollState("bottom-timeout");throw e;}
    }
    private void scrollState(String label){
        scenario.onActivity(a->{RecyclerView v=list(a.findViewById(android.R.id.content));StringBuilder s=new StringBuilder(label+" follow="+chat.autoFollow+" userScrolling="+flag(a,"userScrolling")+" state="+v.getScrollState()+" down="+v.canScrollVertically(1)+" h="+v.getHeight());
            for(int i=0;i<v.getChildCount();i++){View child=v.getChildAt(i);s.append(" child=").append(v.getChildAdapterPosition(child)).append(":").append(child.getTop()).append("..").append(child.getBottom());}
            android.util.Log.i("ScrollVerify",s.toString());});
    }
    private void renderLongReply(boolean streaming)throws Exception{
        StringBuilder text=new StringBuilder();for(int i=0;i<100;i++)text.append("第 ").append(i).append(" 行：用于验证第一次滑离底部后立即显示返回按钮。\n\n");
        scenario.onActivity(a->{
            chat.transcript.clear();chat.running=streaming;chat.autoFollow=true;chat.expanded.clear();
            chat.transcript.accept(Json.obj("object","content","msg_id","long-reply","index",0,"type","text","delta",true,"text",text.toString()));
            a.changed();
        });assertBottom();
        onView(withText("回到最新")).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }
    private void appendReply()throws Exception{
        scenario.onActivity(a->{chat.transcript.accept(Json.obj("object","content","msg_id","long-reply","index",0,"type","text","delta",true,"text","\n\n最新追加内容\n\n继续追加的最新内容"));a.changed();});
    }
    private void awaitHistoryPanel()throws Exception{
        AtomicBoolean ready=new AtomicBoolean();until(()->{scenario.onActivity(a->ready.set(!flag(a,"historyLoading")));return ready.get();});
        onView(withText("你的对话")).check(matches(isDisplayed()));
    }
    private androidx.test.espresso.ViewAction swipeTowardBottom(){
        // Start beside the floating button so the gesture reaches the list.
        return new androidx.test.espresso.action.GeneralSwipeAction(androidx.test.espresso.action.Swipe.FAST,
            v->{int[] p=new int[2];v.getLocationOnScreen(p);return new float[]{p[0]+v.getWidth()*0.2f,p[1]+v.getHeight()*0.85f};},
            v->{int[] p=new int[2];v.getLocationOnScreen(p);return new float[]{p[0]+v.getWidth()*0.2f,p[1]+v.getHeight()*0.15f};},androidx.test.espresso.action.Press.FINGER);
    }
    @Test public void firstDragShowsButtonWithoutAnyDataRefresh()throws Exception{
        renderLongReply(false);
        onView(isAssignableFrom(RecyclerView.class)).perform(swipeDown());
        assertFalse(chat.autoFollow);
        // No second gesture, emit(), polling or layout-triggering action before this assertion.
        onView(withText("回到最新")).check(matches(isDisplayed()));
        scrollState("before-click");onView(withText("回到最新")).perform(click());scrollState("after-click");assertBottom();assertTrue(chat.autoFollow);
        onView(withText("回到最新")).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }
    @Test public void manualReturnAndButtonBothResumeStreamingFollow()throws Exception{
        renderLongReply(true);
        onView(isAssignableFrom(RecyclerView.class)).perform(swipeDown());
        onView(withText("回到最新")).check(matches(isDisplayed()));
        appendReply();assertFalse(chat.autoFollow);
        onView(withText("回到最新")).check(matches(isDisplayed()));
        for(int i=0;i<8&&!chat.autoFollow;i++){onView(isAssignableFrom(RecyclerView.class)).perform(swipeTowardBottom());scrollState("manual-bottom-"+i);}
        until(()->chat.autoFollow);assertBottom();
        onView(withText("回到最新")).check(matches(withEffectiveVisibility(Visibility.GONE)));
        appendReply();assertBottom();
        onView(isAssignableFrom(RecyclerView.class)).perform(swipeDown());
        onView(withText("回到最新")).perform(click());appendReply();assertBottom();
        onView(withText("回到最新")).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }
    @Test public void actualSseFollowsBottomAndReturnButtonButPreservesReadingPosition()throws Exception{
        renderLongReply(false);
        scenario.onActivity(a->{chat.attach(a);chat.chat=Json.obj("id","stream-test");call(chat,"startStream",new Class<?>[]{org.json.JSONObject.class,boolean.class},Json.obj("input","test"),false);});
        until(()->chat.transcript.rows().stream().anyMatch(r->r.text.contains("SSE-05")));assertBottom();assertTrue(chat.running);
        onView(isAssignableFrom(RecyclerView.class)).perform(swipeDown());
        onView(withText("回到最新")).check(matches(isDisplayed()));
        until(()->chat.transcript.rows().stream().anyMatch(r->r.text.contains("SSE-12")));assertFalse(chat.autoFollow);
        scenario.onActivity(a->assertTrue("Incoming SSE must not pull the reader to bottom",list(a.findViewById(android.R.id.content)).canScrollVertically(1)));
        onView(withText("回到最新")).perform(click());
        until(()->chat.transcript.rows().stream().anyMatch(r->r.text.contains("SSE-22")));assertBottom();assertTrue(chat.autoFollow);
        onView(withText("回到最新")).check(matches(withEffectiveVisibility(Visibility.GONE)));
        until(()->!chat.running);assertBottom();
    }
    @Test public void slowHistoryStaysSingleFlightAfterThrottleWindowAndRetriesFailure()throws Exception{
        historyGate=new CountDownLatch(1);historyFails=true;
        onView(withContentDescription("历史对话")).perform(click(),click(),click());until(()->historyCalls.get()==1);
        Thread.sleep(2800);
        onView(withContentDescription("历史对话")).perform(click(),click());assertEquals(1,historyCalls.get());
        historyGate.countDown();
        AtomicBoolean released=new AtomicBoolean();until(()->{scenario.onActivity(a->released.set(!flag(a,"historyLoading")));return released.get();});
        historyFails=false;historyGate=null;
        onView(withContentDescription("历史对话")).perform(click());until(()->historyCalls.get()==2);
        awaitHistoryPanel();
    }
    @Test public void historyClickDuringBackgroundRefreshDoesNotDuplicateHttp()throws Exception{
        historyGate=new CountDownLatch(1);
        startBackgroundHistory();until(()->historyCalls.get()==1);
        onView(withContentDescription("历史对话")).perform(click(),click());
        Thread.sleep(500);assertEquals("History opening must reuse the in-flight list request",1,historyCalls.get());
        historyGate.countDown();
        awaitHistoryPanel();
    }
    @Test public void completedHistoryRequestStillHonorsClickCooldown()throws Exception{
        long started=android.os.SystemClock.elapsedRealtime();
        scenario.onActivity(a->call(a,"history",new Class<?>[]{}));
        until(()->historyCalls.get()==1);
        AtomicBoolean ready=new AtomicBoolean();until(()->{scenario.onActivity(a->ready.set(!flag(a,"historyLoading")));return ready.get();});
        scenario.onActivity(a->{
            ((android.app.Dialog)field(a,"activePanel")).dismiss();
            assertTrue("Repeat must be inside the 2.5 second cooldown",android.os.SystemClock.elapsedRealtime()-started<2500);
            call(a,"history",new Class<?>[]{});
            assertFalse("Cooldown must reject the completed request's repeat",flag(a,"historyLoading"));
        });
        Thread.sleep(200);assertEquals(1,historyCalls.get());
        Thread.sleep(2700);
        onView(withContentDescription("历史对话")).perform(click());until(()->historyCalls.get()==2);
        awaitHistoryPanel();
    }
    @Test public void sharedBackgroundFailureReleasesHistoryForRetry()throws Exception{
        historyGate=new CountDownLatch(1);historyFails=true;
        startBackgroundHistory();until(()->historyCalls.get()==1);
        scenario.onActivity(a->call(a,"history",new Class<?>[]{}));
        Thread.sleep(2800);assertEquals(1,historyCalls.get());historyGate.countDown();
        AtomicBoolean ready=new AtomicBoolean();until(()->{scenario.onActivity(a->ready.set(!flag(a,"historyLoading")));return ready.get();});
        historyFails=false;historyGate=null;
        onView(withContentDescription("历史对话")).perform(click());until(()->historyCalls.get()==2);awaitHistoryPanel();
    }
    @Test public void differentHistoryFilterDoesNotReuseWrongList()throws Exception{
        historyGate=new CountDownLatch(1);
        startBackgroundHistory();until(()->historyCalls.get()==1);
        AtomicBoolean complete=new AtomicBoolean();
        main(()->{chat.archived=true;chat.refreshChats(()->complete.set(true));});
        until(()->historyCalls.get()==2);historyGate.countDown();until(complete::get);
        assertEquals(2,historyCalls.get());
    }
    @Test public void accountAndPoolNavigationDoNotLaunchDuplicateActivities()throws Exception{
        profileGate=new CountDownLatch(1);
        scenario.onActivity(a->{for(int i=0;i<6;i++)call(a,"openPage",new Class<?>[]{String.class,Intent.class},"account",new Intent(a,AccountActivity.class));});
        until(()->profileCalls.get()==1);assertEquals(1,accountCreated.get());
        profileGate.countDown();onView(withContentDescription("返回对话")).perform(click());
        // A different entry must remain usable immediately after returning.
        poolGate=new CountDownLatch(1);
        scenario.onActivity(a->{chat.detach(a);for(int i=0;i<6;i++)call(a,"openPage",new Class<?>[]{String.class,Intent.class},"skill-pool",new Intent(a,SkillsActivity.class).putExtra("pool",true));});
        until(()->poolCalls.get()==1);assertEquals(1,poolCreated.get());
        Thread.sleep(2800);
        onView(withContentDescription("刷新列表")).perform(click(),click(),click());assertEquals(1,poolCalls.get());
        poolGate.countDown();
        until(()->child.get() instanceof SkillsActivity s&&!s.busy);
        onView(withContentDescription("刷新列表")).perform(click());until(()->poolCalls.get()==2);
    }
    @Test public void failedPoolLoadAllowsRetry()throws Exception{
        poolFails=true;
        scenario.onActivity(a->call(a,"openPage",new Class<?>[]{String.class,Intent.class},"skill-pool",new Intent(a,SkillsActivity.class).putExtra("pool",true)));
        until(()->poolCalls.get()==1&&child.get() instanceof SkillsActivity s&&!s.busy);
        onView(withText("知道了")).perform(click());poolFails=false;
        onView(withContentDescription("刷新列表")).perform(click());until(()->poolCalls.get()==2&&child.get() instanceof SkillsActivity s&&!s.busy);
        onView(withText("技能池管理")).check(matches(isDisplayed()));
    }
}
