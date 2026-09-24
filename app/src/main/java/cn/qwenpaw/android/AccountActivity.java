package cn.qwenpaw.android;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.*;

/** Account screen, bound to the identity that opened it. Passwords stay in memory only. */
public final class AccountActivity extends AppCompatActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Ui ui;
    private ChatController chat;
    private ApiClient api;
    private AccountProfile profile;
    private LinearLayout body;
    private TextView message;
    private EditText currentPassword,newPassword,confirmation;
    private MaterialButton save,back;
    private boolean saving;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new Ui(this);chat=((PawApplication)getApplication()).ensureChat();
        if(!chat.loggedIn){finish();return;}api=chat.api();render();load();
    }
    @Override protected void onResume(){super.onResume();if(api!=null&&!sameAccount())finish();}
    @Override protected void onDestroy(){clearPasswords();io.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){if(saving){toast("正在修改密码，请稍候");return;}super.onBackPressed();}
    private boolean sameAccount(){return chat.loggedIn&&api.base.equals(chat.base)&&api.token.equals(chat.token);}
    private boolean current(){return sameAccount()&&!isFinishing()&&!isDestroyed();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void render(){
        LinearLayout root=ui.column();root.setBackgroundColor(Ui.BG);root.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(12));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(ui.dp(16)+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),ui.dp(16)+i.getSystemWindowInsetRight(),ui.dp(12)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
        LinearLayout header=ui.row();back=ui.iconButton("返回对话","close",v->onBackPressed());header.addView(back);header.addView(ui.heading("个人中心",22));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=ui.column();ui.padding(body,4,16);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        documentEntry();detail("服务器",api.base);message=ui.text("正在读取账号资料…",13,Ui.MUTED);body.addView(message);
    }
    private JSONObject object(String path)throws IOException{
        Object value=api.call("GET",path,null);if(!(value instanceof JSONObject))throw new IOException("服务器未返回有效的账号资料");return (JSONObject)value;
    }
    private void load(){
        io.execute(()->{try{
            JSONObject status=object("/auth/status");boolean hub="hub".equals(status.optString("mode"));
            JSONObject identity=status.optBoolean("enabled",false)?object(hub?"/hub/me":"/auth/verify"):new JSONObject();
            if(status.optBoolean("enabled",false)&&(hub?identity.optString("user_id").isBlank():!identity.optBoolean("valid")))throw new IOException("服务器未返回有效的账号身份");
            AccountProfile loaded=new AccountProfile(status,identity);
            runOnUiThread(()->{if(!current())return;profile=loaded;showProfile();});
        }catch(Exception e){runOnUiThread(()->{
            if(!current())return;if(e instanceof ApiClient.ApiException a&&a.status==401){chat.failure(e);finish();return;}
            message.setText("账号资料加载失败，请重试");MaterialButton retry=ui.button("重新加载",null,false,v->{render();load();});body.addView(retry);
        });}});
    }
    private void showProfile(){
        body.removeAllViews();documentEntry();body.addView(ui.heading("账号资料",18));ui.gap(body,12);
        profile.fields().forEach(this::detail);
        detail("连接类型",profile.hub?"Hub 多用户账号":profile.passwordAllowed?"独立服务账号":"访问令牌或免登录连接");detail("服务器",api.base);
        body.addView(ui.text("仅展示服务器返回的账号资料。邮箱、手机号、头像等资料当前未提供。",12,Ui.MUTED));ui.gap(body,24);
        body.addView(ui.heading("修改密码",18));ui.gap(body,10);
        if(!profile.passwordAllowed){body.addView(ui.text("当前连接没有可修改密码的账号。",13,Ui.MUTED));return;}
        body.addView(ui.text(profile.hub?"使用当前登录身份验证。修改后所有旧登录会失效，请使用新密码重新登录。":"需验证当前密码。修改成功后，请使用新密码重新登录。",13,Ui.MUTED));
        if(!profile.hub)currentPassword=passwordField("当前密码");
        newPassword=passwordField("新密码（8–1024 个字符）");confirmation=passwordField("确认新密码");
        message=ui.text("",13,Ui.DANGER);ui.gap(body,12);body.addView(message);
        save=ui.button("保存新密码",null,true,v->savePassword());body.addView(save,new LinearLayout.LayoutParams(-1,ui.dp(52)));
    }
    private void detail(String label,String value){
        body.addView(ui.text(label,12,Ui.MUTED));ui.gap(body,6);TextView text=ui.text(value,15,Ui.INK);text.setTextIsSelectable(true);body.addView(text);ui.gap(body,16);
    }
    private void documentEntry(){
        LinearLayout card=ui.column();ui.padding(card,16,16);card.setBackground(ui.shape(Ui.TINT,18,0));
        card.addView(ui.heading("了解你的智能体",19));ui.gap(card,8);TextView description=ui.text("做事规则、身份与用户画像、沟通风格和主动检查清单。",13,Ui.GREEN);description.setLineSpacing(ui.dp(4),1);card.addView(description);ui.gap(card,10);
        card.addView(ui.button("查看智能体档案","right",false,v->startActivity(new Intent(this,AgentDocumentsActivity.class))),new LinearLayout.LayoutParams(-1,ui.dp(48)));body.addView(card);ui.gap(body,24);
        body.addView(ui.heading("智能体管理",18));
        body.addView(ui.button("技能","spark",false,v->startActivity(new Intent(this,SkillsActivity.class))),new LinearLayout.LayoutParams(-1,-2));
        body.addView(ui.button("工具","settings",false,v->startActivity(new Intent(this,ToolsActivity.class))),new LinearLayout.LayoutParams(-1,-2));
        ui.gap(body,16);body.addView(ui.heading("全局设置",18));
        body.addView(ui.button("技能池管理","folder",false,v->startActivity(new Intent(this,SkillsActivity.class).putExtra("pool",true))),new LinearLayout.LayoutParams(-1,-2));ui.gap(body,24);
    }
    private EditText passwordField(String label){
        ui.gap(body,16);body.addView(ui.text(label,12,Ui.MUTED));ui.gap(body,6);
        EditText field=new androidx.appcompat.widget.AppCompatEditText(this);field.setHint(label);field.setSingleLine(true);field.setTextSize(15);field.setTextColor(Ui.INK);field.setHintTextColor(Ui.MUTED);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setSaveEnabled(false);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);ui.padding(field,14,14);field.setBackground(ui.shape(Color.WHITE,14,Ui.LINE));body.addView(field,new LinearLayout.LayoutParams(-1,ui.dp(56)));return field;
    }
    private void clearPasswords(){for(EditText field:new EditText[]{currentPassword,newPassword,confirmation})if(field!=null)field.setText("");}
    private void savePassword(){
        if(saving||!current()||profile==null)return;
        if(chat.running||chat.busy||chat.uploads>0){message.setText("请等待当前对话任务或附件上传结束后再修改密码");return;}
        String old=currentPassword==null?"":currentPassword.getText().toString(),password=newPassword.getText().toString();
        String error=profile.passwordError(old,password,confirmation.getText().toString());if(!error.isEmpty()){message.setText(error);return;}
        boolean hub=profile.hub;JSONObject payload=hub?Json.obj("new_password",password):Json.obj("current_password",old,"new_password",password);
        saving=true;save.setEnabled(false);back.setEnabled(false);message.setText("正在修改…");clearPasswords();
        for(EditText field:new EditText[]{currentPassword,newPassword,confirmation})if(field!=null)field.setEnabled(false);
        io.execute(()->{try{
            api.call("POST",hub?"/hub/me/password":"/auth/update-profile",payload);
            runOnUiThread(()->{if(sameAccount()){chat.logout();toast("密码已修改，请使用新密码重新登录");}saving=false;finish();});
        }catch(Exception e){
            // Standalone auth uses 401 both for a bad current password and an expired token.
            boolean passwordRejected=false;
            if(!hub&&e instanceof ApiClient.ApiException a&&a.status==401){try{passwordRejected=object("/auth/verify").optBoolean("valid");}catch(Exception ignored){}}
            final boolean rejected=passwordRejected;
            runOnUiThread(()->{
            if(!current())return;saving=false;save.setEnabled(true);back.setEnabled(true);
            for(EditText field:new EditText[]{currentPassword,newPassword,confirmation})if(field!=null)field.setEnabled(true);
            // Validation responses can echo submitted values. Do not display raw password API errors.
            if(e instanceof ApiClient.ApiException a){
                if(a.status==401){if(rejected){message.setText("当前密码不正确，请重新输入");return;}chat.logout();toast("登录已失效，请重新登录后检查密码");finish();return;}
                message.setText(switch(a.status){case 400,422->"密码未被接受，请检查密码要求后重新输入";case 403->hub?"当前账号无权修改密码":"当前密码不正确，或服务器不允许修改";case 404,405->"此服务器暂不支持修改密码";case 429->"操作过于频繁，请稍后重试";default->"服务器未确认修改结果，请先尝试使用新密码登录，勿重复提交";});
            }else message.setText("网络中断，修改结果尚未确认。请先尝试使用新密码登录，勿重复提交。");
        });}finally{payload.remove("current_password");payload.remove("new_password");}});
    }
}
