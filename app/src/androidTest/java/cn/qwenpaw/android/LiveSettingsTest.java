package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Explicit opt-in, read-only real-server acceptance. Never changes remote settings. */
@RunWith(AndroidJUnit4.class)
public class LiveSettingsTest {
    @Test public void realRuntimeEnvironmentAndTokenPages()throws Exception{
        Bundle args=InstrumentationRegistry.getArguments();String base=args.getString("liveBase","");Assume.assumeFalse(base.isEmpty());
        JSONObject auth=Json.object(new ApiClient(base,"","default").call("POST","/auth/login",Json.obj("username",args.getString("liveUser"),"password",args.getString("livePassword"))));
        ChatController chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        String oldBase=chat.base,oldToken=chat.token,oldAgent=chat.agent;boolean oldLogin=chat.loggedIn;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{chat.base=base;chat.token=auth.optString("token");chat.agent="default";chat.loggedIn=true;});
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SettingsActivity.class))){
            awaitText("LLM 自动重试");Thread.sleep(1800);capture("react");
            onView(allOf(withText("LLM 自动重试"),isAssignableFrom(android.widget.Button.class))).perform(click());onView(withHint("最大重试次数")).check(matches(isDisplayed()));capture("retry");
            onView(allOf(withText("上下文管理"),isAssignableFrom(android.widget.Button.class))).perform(click());onView(withText("上下文压缩 ▾")).perform(scrollTo(),click());onView(withHint("上下文压缩阈值比例")).perform(scrollTo());capture("context");
            onView(allOf(withText("工具审批模式"),isAssignableFrom(android.widget.Button.class))).perform(scrollTo(),click());onView(withText(startsWith("严格模式\n"))).check(matches(isDisplayed()));capture("approval");
            onView(allOf(withText("环境变量"),isAssignableFrom(android.widget.Button.class))).perform(click());awaitText("添加变量");capture("environment");
            onView(withText("Token 分析")).perform(click());awaitText("刷新 Token 数据");capture("tokens");onView(withTagValue(is((Object)"chart:输入 Token"))).perform(scrollTo());Thread.sleep(650);onView(withTagValue(is((Object)"chart:输入 Token"))).check(matches(isDisplayed())).perform(click());capture("token-trend");
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{chat.base=oldBase;chat.token=oldToken;chat.agent=oldAgent;chat.loggedIn=oldLogin;});}
    }
    private void awaitText(String text)throws Exception{long end=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<end){try{onView(allOf(withText(text),isAssignableFrom(android.widget.Button.class))).check(matches(isDisplayed()));return;}catch(AssertionError|RuntimeException e){Thread.sleep(350);}}throw new AssertionError("Page did not load: "+text);}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(650);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"settings-live-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}


