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
import java.util.concurrent.atomic.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AccountUiTest {
    private MockWebServer server;
    private ActivityScenario<AccountActivity> scenario;
    private ChatController chat;
    private volatile boolean hub=true;
    private volatile int passwordStatus=200;
    private final AtomicReference<JSONObject> submitted=new AtomicReference<>();
    private final AtomicReference<String> path=new AtomicReference<>();
    private static MockResponse json(Object value){return new MockResponse().setHeader("Content-Type","application/json").setBody(value.toString());}
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    @Before public void setup()throws Exception{
        server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest r){
            String p=r.getPath();
            if(p.equals("/api/auth/status"))return json(Json.obj("enabled",true,"has_users",true,"mode",hub?"hub":"standalone"));
            if(p.equals("/api/hub/me"))return json(Json.obj("username","ui-demo","user_id","user-demo","role","user","disabled",false,"created_at","2026-09-23T08:00:00Z","profile",Json.obj("workspace_dir","/workspace/demo")));
            if(p.equals("/api/auth/verify"))return json(Json.obj("valid",true,"username","ui-demo"));
            if(r.getMethod().equals("POST")){submitted.set(Json.object(Json.parse(r.getBody().readUtf8())));path.set(p);return json(passwordStatus==200?Json.obj("username","ui-demo"):Json.obj("detail","Current password is incorrect")).setResponseCode(passwordStatus);}
            return json(new JSONObject());
        }});server.start();
        chat=((PawApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).chat;
        String base=server.url("/").toString().replaceAll("/$","");
        main(()->{chat.logout();chat.base=base;chat.token="ui-test-token";chat.username="ui-demo";chat.loggedIn=true;});
    }
    private void launch()throws Exception{scenario=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),AccountActivity.class));await("保存新密码");}
    private void await(String text)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(AssertionError|RuntimeException e){try{onView(withText(text)).perform(scrollTo());return;}catch(AssertionError|RuntimeException ignored){}Thread.sleep(100);}}throw new AssertionError("Not visible: "+text);}
    private void fill(String value){onView(withHint("新密码（8–1024 个字符）")).perform(scrollTo(),replaceText(value),closeSoftKeyboard());onView(withHint("确认新密码")).perform(scrollTo(),replaceText(value),closeSoftKeyboard());}
    private void capture(String name)throws Exception{Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("evidence");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void hubValidatesConfirmationThenChangesPasswordAndLogsOut()throws Exception{
        launch();onView(withText("账号资料")).perform(scrollTo());capture("account-hub-profile");
        onView(withHint("当前密码")).check(doesNotExist());fill("new-password");onView(withHint("确认新密码")).perform(scrollTo(),replaceText("mismatch"),closeSoftKeyboard());onView(withText("保存新密码")).perform(scrollTo(),click());await("两次输入的新密码不一致");assertNull(submitted.get());capture("account-password-validation");
        fill("new-password");onView(withText("保存新密码")).perform(scrollTo(),click());
        long end=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<end){AtomicBoolean logged=new AtomicBoolean();main(()->logged.set(chat.loggedIn));if(!logged.get())break;Thread.sleep(100);}
        assertFalse(chat.loggedIn);assertEquals("/api/hub/me/password",path.get());assertEquals("new-password",submitted.get().optString("new_password"));assertFalse(submitted.get().has("current_password"));
    }
    @Test public void standaloneWrongCurrentPasswordKeepsSessionAndCanRetry()throws Exception{
        hub=false;passwordStatus=401;launch();onView(withHint("当前密码")).perform(scrollTo(),replaceText("incorrect"),closeSoftKeyboard());fill("new-password");onView(withText("保存新密码")).perform(scrollTo(),click());await("当前密码不正确，请重新输入");assertTrue(chat.loggedIn);assertEquals("/api/auth/update-profile",path.get());assertEquals("incorrect",submitted.get().optString("current_password"));onView(withHint("当前密码")).perform(scrollTo()).check(matches(withText("")));capture("account-standalone-retry");
    }
    @After public void teardown()throws Exception{if(scenario!=null)scenario.close();main(chat::logout);server.shutdown();}
}
