package cn.qwenpaw.android;

import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native management shell. Each operation captures an immutable account/workspace. */
abstract class ManagementActivity extends AppCompatActivity {
    protected Ui ui;
    protected ChatController chat;
    protected ApiClient account;
    protected String agent;
    protected LinearLayout body,root;
    protected ScrollView scroll;
    protected TextView status;
    protected Spinner agentSelector;
    protected boolean busy;
    protected int revision;
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    protected interface Job<T>{T run(ApiClient api)throws Exception;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new Ui(this);chat=((PawApplication)getApplication()).ensureChat();
        if(!chat.loggedIn){finish();return;}account=chat.api();agent=state==null?account.agent:state.getString("management-agent",account.agent);
    }
    @Override protected void onResume(){super.onResume();if(account!=null&&!sameAccount())finish();}
    @Override protected void onDestroy(){revision++;io.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("management-agent",agent);super.onSaveInstanceState(out);}
    protected boolean sameAccount(){return chat.loggedIn&&account.base.equals(chat.base)&&account.token.equals(chat.token);}
    protected boolean current(int r){return !isFinishing()&&!isDestroyed()&&sameAccount()&&revision==r;}
    protected void screen(String title,boolean agentScoped,Runnable refresh){
        root=ui.column();root.setBackgroundColor(Ui.BG);root.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(12));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(ui.dp(16)+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),ui.dp(16)+i.getSystemWindowInsetRight(),ui.dp(12)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
        LinearLayout head=ui.row();head.addView(ui.iconButton("返回","close",v->onBackPressed()));head.addView(ui.heading(title,22),new LinearLayout.LayoutParams(0,-2,1));head.addView(ui.iconButton("刷新列表","loop",v->{if(!busy)refresh.run();}));root.addView(head);
        if(agentScoped){
            ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();
            for(int i=0;i<chat.agents.length();i++){JSONObject a=chat.agents.optJSONObject(i);if(a==null||a.optString("id").isBlank())continue;ids.add(a.optString("id"));labels.add(a.optString("name",a.optString("id"))+" · "+a.optString("id"));}
            if(!ids.contains(agent)){ids.add(agent);labels.add(agent);}
            agentSelector=select(root,"当前智能体",labels,ids.indexOf(agent));
            agentSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String next=ids.get(pos);if(next.equals(agent))return;if(busy){agentSelector.setSelection(ids.indexOf(agent));return;}agent=next;revision++;onAgentChanged();}});
        }
        status=ui.text("",12,Ui.MUTED);ui.padding(status,2,6);status.setVisibility(View.GONE);root.addView(status);
        scroll=new ScrollView(this);scroll.setFillViewport(true);body=ui.column();ui.padding(body,2,8);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    protected abstract void onAgentChanged();
    protected void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    protected void notice(String title,String text){new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(text).setPositiveButton("知道了",null).show();}
    protected void confirm(String title,String text,Runnable yes){new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(text).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->{if(!busy)yes.run();}).show();}
    protected void enabled(View view,boolean value){view.setEnabled(value);if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)enabled(g.getChildAt(i),value);}
    protected void working(boolean value){busy=value;if(agentSelector!=null)agentSelector.setEnabled(!value);enabled(body,!value);}
    protected <T> void task(String label,Job<T> job,Consumer<T> done){task(label,job,done,null);}
    protected <T> void task(String label,Job<T> job,Consumer<T> done,Consumer<Exception> failed){
        if(busy||!sameAccount())return;int request=++revision;ApiClient api=new ApiClient(account.base,account.token,agent);working(true);status.setText(label);status.setVisibility(View.VISIBLE);
        io.execute(()->{try{T result=job.run(api);runOnUiThread(()->{if(!current(request))return;working(false);status.setText("");status.setVisibility(View.GONE);done.accept(result);});}
        catch(Exception e){runOnUiThread(()->{if(!current(request))return;working(false);status.setText("操作未完成，可重试");if(e instanceof ApiClient.ApiException a&&a.status==401){chat.failure(e);finish();return;}if(failed!=null)failed.accept(e);else showError(e);});}});
    }
    protected void showError(Exception e){
        if(e instanceof ApiClient.ApiException a&&a.detail instanceof JSONObject detail&&"security_scan_failed".equals(detail.optString("type"))){notice("技能安全扫描未通过",Json.text(detail));return;}
        notice("操作未完成",e.getMessage()==null?"请检查网络并重试。":e.getMessage());
    }
    protected static JSONObject object(Object value)throws IOException{if(value instanceof JSONObject o)return o;throw new IOException("服务器未返回有效对象，请刷新重试");}
    protected static JSONArray array(Object value)throws IOException{if(value instanceof JSONArray a)return a;throw new IOException("服务器未返回有效列表，请刷新重试");}
    protected LinearLayout card(LinearLayout parent){LinearLayout c=ui.column();ui.padding(c,14,14);c.setBackground(ui.shape(Ui.WHITE,16,Ui.LINE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=ui.dp(10);parent.addView(c,p);return c;}
    protected void text(LinearLayout parent,String value){TextView t=ui.text(value,13,Ui.MUTED);t.setLineSpacing(ui.dp(3),1);t.setTextIsSelectable(true);parent.addView(t);ui.gap(parent,6);}
    protected void summary(LinearLayout parent,String value){TextView t=ui.text(value,13,Ui.MUTED);t.setLineSpacing(ui.dp(3),1);t.setMaxLines(3);t.setEllipsize(android.text.TextUtils.TruncateAt.END);parent.addView(t);ui.gap(parent,8);}
    protected EditText field(LinearLayout parent,String label,String value,boolean multiline){
        text(parent,label);EditText e=new androidx.appcompat.widget.AppCompatEditText(this);e.setTextSize(14);e.setTextColor(Ui.INK);e.setHint(label);e.setContentDescription(label);e.setText(value);e.setSingleLine(!multiline);e.setGravity(Gravity.TOP);e.setSaveEnabled(false);
        if(multiline){e.setMinLines(3);e.setMaxLines(12);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);}ui.padding(e,12,12);e.setBackground(ui.shape(Ui.WHITE,12,Ui.LINE));parent.addView(e,new LinearLayout.LayoutParams(-1,-2));ui.gap(parent,12);return e;
    }
    protected Spinner select(LinearLayout parent,String label,List<String> options,int index){text(parent,label);Spinner s=new Spinner(this);s.setContentDescription(label);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(Math.max(0,index));parent.addView(s,new LinearLayout.LayoutParams(-1,ui.dp(48)));return s;}
    protected CheckBox check(LinearLayout parent,String label,boolean value){CheckBox b=new CheckBox(this);b.setText(label);b.setTextColor(Ui.INK);b.setChecked(value);parent.addView(b);return b;}
    protected void action(LinearLayout parent,String label,Runnable click){parent.addView(ui.button(label,null,false,v->{if(!busy)click.run();}),new LinearLayout.LayoutParams(-1,-2));}
    protected void multi(String title,List<String> labels,boolean[] checked,Consumer<boolean[]> done){new MaterialAlertDialogBuilder(this).setTitle(title).setMultiChoiceItems(labels.toArray(new String[0]),checked,(d,index,value)->checked[index]=value).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->done.accept(checked)).show();}
    @Override public void onBackPressed(){if(busy){toast("正在处理，请稍候");return;}super.onBackPressed();}
}
