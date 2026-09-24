package cn.qwenpaw.android;

import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Optional opt-in live test. Credentials are instrumentation arguments, never packaged in either APK. */
@RunWith(AndroidJUnit4.class)
public class LiveServerTest {
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Test public void realHubLoginHistoryAndFileDownload()throws Exception{
        var args=InstrumentationRegistry.getArguments();Assume.assumeTrue(args.containsKey("liveBase"));
        ChatController c=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        main(c::logout);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            android.net.Uri endpoint=android.net.Uri.parse(args.getString("liveBase"));
            onView(withHint("IP 或域名")).perform(replaceText(endpoint.getHost()));
            onView(withText("高级连接选项")).perform(scrollTo(),click());
        onView(withHint("端口（留空使用默认值）")).perform(scrollTo(),replaceText(endpoint.getPort()<0?"":Integer.toString(endpoint.getPort())),closeSoftKeyboard());
            onView(isAssignableFrom(android.widget.Spinner.class)).perform(scrollTo(),click());onData(allOf(is(instanceOf(String.class)),is(endpoint.getScheme()))).perform(click());
            onView(withHint("用户名")).perform(scrollTo(),replaceText(args.getString("liveUser")));
            onView(withHint("密码")).perform(scrollTo(),replaceText(args.getString("livePassword")),closeSoftKeyboard());
            onView(withText("连接服务器")).perform(scrollTo(),click());
            await(()->c.loggedIn&&c.chats.length()>0&&c.providers.length()>0,c);
            onView(withContentDescription("发送")).check(matches(isDisplayed()));
            AtomicBoolean found=new AtomicBoolean(false);main(()->{for(int i=0;i<c.chats.length();i++){var chat=c.chats.optJSONObject(i);if(chat.optString("id").equals(args.getString("liveChat"))){c.open(chat);found.set(true);break;}}});assertTrue("Dedicated smoke chat must exist",found.get());
            await(()->!c.busy&&c.transcript.rows().stream().anyMatch(r->r.kind.equals("file")),c);
            final String[] url={""};main(()->{for(Transcript.Row row:c.transcript.rows())if(row.kind.equals("file"))url[0]=row.url;});
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();c.api().download(url[0],bytes);assertEquals("android smoke ok",bytes.toString("UTF-8").replaceFirst("^\uFEFF",""));
            assertTrue(c.transcript.rows().stream().anyMatch(r->r.text.contains("```mermaid")));
            if(args.getString("liveSend","false").equals("true")){
                onView(withHint("发送消息…")).perform(replaceText("只回复：安卓联调OK。不要调用任何工具。"),closeSoftKeyboard());
                onView(withContentDescription("发送")).perform(click());
                await(()->!c.running&&!c.busy&&c.transcript.rows().stream().anyMatch(r->r.role.equals("assistant")&&r.text.contains("安卓联调OK")),c);
            }
            await(()->c.commandsLoading==0&&c.transcript.metrics!=null&&c.activeContextLimit>0,c);
            assertTrue("Live history must restore context usage",c.transcript.metrics.hasContext());
            onView(withContentDescription("快捷命令")).perform(click());await(()->c.commandsLoading==0,c);assertTrue(CommandCatalog.build(c.loops,c.skills).size()>6);capture("live-commands");onView(withContentDescription("关闭面板")).perform(click());
            onView(withContentDescription("上下文用量")).perform(click());capture("live-context-usage");onView(withContentDescription("关闭面板")).perform(click());
            if(args.getString("liveCompact","false").equals("true")){
                long before=c.transcript.metrics.tokens();main(()->{c.draft="压缩测试：保留的未发送草稿";c.emit();});
                onView(withContentDescription("上下文用量")).perform(click());onView(withText("压缩上下文")).perform(scrollTo(),click());await(()->!c.running&&!c.busy,c);
                assertEquals("压缩测试：保留的未发送草稿",c.draft);assertEquals("",c.transcript.error);assertNotNull(c.transcript.metrics);
                var report=Json.obj("before_context_tokens",before,"after_context_tokens",c.transcript.metrics.tokens(),"draft_preserved",true,"status",c.status);
                var rows=c.transcript.rows();for(int i=rows.size()-1;i>=0;i--){var row=rows.get(i);if(row.role.equals("assistant")&&row.kind.equals("text")){Json.put(report,"server_reply",row.text);break;}}
                String commandReply=report.optString("server_reply");assertTrue("Actual compact feedback must survive history refresh: "+commandReply,commandReply.toLowerCase(java.util.Locale.ROOT).contains("compact")||commandReply.contains("压缩"));
                File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(dir,"live-compact.json")),java.nio.charset.StandardCharsets.UTF_8)){out.write(report.toString(2));}
                onView(withContentDescription("上下文用量")).perform(click());capture("live-context-after-compact");onView(withContentDescription("关闭面板")).perform(click());
            }
            assertEquals("",c.transcript.error);
            Thread.sleep(1800);
            Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"live-hub-chat.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
        }finally{main(c::logout);}
    }
    private void capture(String name)throws Exception{Thread.sleep(650);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void await(java.util.function.BooleanSupplier condition,ChatController c)throws Exception{long end=System.currentTimeMillis()+120000;while(System.currentTimeMillis()<end){AtomicBoolean result=new AtomicBoolean();main(()->result.set(condition.getAsBoolean()));if(result.get())return;Thread.sleep(60);}throw new AssertionError("Live timeout: "+c.status);}
}
