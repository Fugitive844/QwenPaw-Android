package cn.qwenpaw.android;

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Opt-in: reads account info and UI only, never submits a real password change. */
@RunWith(AndroidJUnit4.class)
public class LiveAccountTest {
    @Test public void accountAndFullHeightNavigation()throws Exception{
        Bundle args=InstrumentationRegistry.getArguments();String base=args.getString("liveBase","");Assume.assumeFalse(base.isEmpty());
        ChatController chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->chat.login(base,args.getString("liveUser"),args.getString("livePassword"),""));
        long end=System.currentTimeMillis()+60000;AtomicBoolean ready=new AtomicBoolean();
        while(System.currentTimeMillis()<end){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ready.set(chat.loggedIn&&!chat.busy&&!chat.thinkingLoading&&chat.agents.length()>0&&!chat.thinking.optString("model").isEmpty()));if(ready.get())break;Thread.sleep(200);}assertTrue("Real account failed to load",ready.get());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            capture("chat");onView(withContentDescription("历史对话")).perform(click());await("你的对话");assertFullHeight();capture("history");onView(withContentDescription("关闭面板")).perform(click());
            onView(withContentDescription("更多选项")).perform(click());assertFullHeight();capture("options");onView(withText("个人中心")).perform(click());await("保存新密码");onView(withText("账号资料")).perform(scrollTo());capture("account");onView(withText("保存新密码")).perform(scrollTo());capture("password");onView(withContentDescription("返回对话")).perform(click());
            onView(withContentDescription("模型设置")).perform(click());
            await("模型设置");assertFullHeight();if(!chat.sessionModels)onView(withContentDescription("当前模型思考强度")).check(matches(withEffectiveVisibility(Visibility.GONE)));capture("models");onView(withContentDescription("关闭面板")).perform(click());
        }
    }
    private void await(String text)throws Exception{long end=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<end){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(AssertionError|RuntimeException e){try{onView(withText(text)).perform(scrollTo());return;}catch(AssertionError|RuntimeException ignored){}Thread.sleep(150);}}throw new AssertionError("Page failed to load: "+text);}
    private void assertFullHeight(){onView(withId(com.google.android.material.R.id.design_bottom_sheet)).check((view,error)->{assertNull(error);assertTrue(view.getHeight()>=view.getRootView().getHeight()*0.85);});}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(450);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"v05-live-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
