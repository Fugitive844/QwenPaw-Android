package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
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

/** Explicit opt-in: only login and GETs; never modifies real skills or tool settings. */
@RunWith(AndroidJUnit4.class)
public class LiveSkillsTest {
    @Test public void realSkillsToolsAndPoolReadOnly()throws Exception{
        Bundle args=InstrumentationRegistry.getArguments();String base=args.getString("liveBase","");Assume.assumeFalse(base.isEmpty());
        JSONObject auth=Json.object(new ApiClient(base,"","default").call("POST","/auth/login",Json.obj("username",args.getString("liveUser"),"password",args.getString("livePassword"))));assertFalse(auth.optString("token").isEmpty());
        ChatController chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        String oldBase=chat.base,oldToken=chat.token,oldAgent=chat.agent;boolean oldLogin=chat.loggedIn;JSONArray oldAgents=chat.agents;
        ApiClient api=new ApiClient(base,auth.optString("token"),"default");JSONArray workspaces=Json.array(api.call("GET","/skills/workspaces",null));JSONArray agents=new JSONArray();for(int i=0;i<workspaces.length();i++){JSONObject ws=Json.object(workspaces.opt(i));agents.put(Json.obj("id",ws.optString("agent_id"),"name",ws.optString("agent_name")));}
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{chat.base=base;chat.token=auth.optString("token");chat.agent="default";chat.loggedIn=true;chat.agents=agents;});
        try{
            try(ActivityScenario<SkillsActivity> s=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SkillsActivity.class))){awaitLoaded("正在读取技能…");onView(withText("批量管理")).check(matches(isDisplayed()));capture("skills");}
            try(ActivityScenario<ToolsActivity> s=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),ToolsActivity.class))){awaitLoaded("正在读取工具…");onView(withText("全部启用")).check(matches(isDisplayed()));capture("tools");}
            try(ActivityScenario<SkillsActivity> s=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SkillsActivity.class).putExtra("pool",true))){awaitLoaded("正在读取技能…");onView(withText("导入内置技能")).check(matches(isDisplayed()));capture("pool");}
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{chat.base=oldBase;chat.token=oldToken;chat.agent=oldAgent;chat.loggedIn=oldLogin;chat.agents=oldAgents;});}
    }
    private void awaitLoaded(String loading)throws Exception{long end=System.currentTimeMillis()+90000;while(System.currentTimeMillis()<end){try{onView(withText(loading)).check(doesNotExist());onView(withText("操作未完成")).check(doesNotExist());onView(withText("操作未完成，可重试")).check(doesNotExist());return;}catch(AssertionError|RuntimeException e){Thread.sleep(250);}}throw new AssertionError("Management page failed to load");}
    private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(350);Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,"skills-tools-live-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
