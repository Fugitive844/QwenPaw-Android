package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/** Explicit live credentials; only reads documents and optional status configuration. */
@RunWith(AndroidJUnit4.class)
public class LiveDocumentsTest {
    @Test public void realConsoleDocumentsRenderFromPersonalCenter()throws Exception{
        Bundle args=InstrumentationRegistry.getArguments();String base=args.getString("liveBase","");Assume.assumeFalse(base.isEmpty());
        ChatController chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->chat.login(base,args.getString("liveUser"),args.getString("livePassword"),""));
        long end=System.currentTimeMillis()+60000;AtomicBoolean ready=new AtomicBoolean();while(System.currentTimeMillis()<end){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ready.set(chat.loggedIn&&!chat.busy&&!chat.thinkingLoading&&chat.agents.length()>0));if(ready.get())break;Thread.sleep(200);}assertTrue("Account did not load",ready.get());
        try(ActivityScenario<AccountActivity> scenario=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),AccountActivity.class))){
            onView(withText("查看智能体档案")).perform(scrollTo(),click());waitReady();capture("overview");
            for(AgentDocument.Kind kind:AgentDocument.ALL){
                onView(withContentDescription("查看"+kind.title())).perform(scrollTo(),click());waitReady();
                onView(withText("Markdown 源码")).perform(scrollTo()).check(matches(isDisplayed()));capture(kind.file().replace(".md",""));
                onView(withContentDescription("返回个人中心或档案列表")).perform(click());waitReady();
            }
            onView(withContentDescription("返回个人中心或档案列表")).perform(click());
        }
    }
    private void waitReady()throws Exception{long end=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<end){try{onView(withContentDescription("刷新智能体档案")).check(matches(isEnabled()));Thread.sleep(600);return;}catch(AssertionError|RuntimeException e){Thread.sleep(150);}}throw new AssertionError("Document did not finish loading");}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"documents-live-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
