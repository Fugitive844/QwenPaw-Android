package cn.qwenpaw.android;

import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spanned;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import io.noties.markwon.*;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Read-only counterpart of Console Files > profile; requests capture account and agent. */
public final class AgentDocumentsActivity extends AppCompatActivity {
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private Ui ui;
    private ChatController chat;
    private ApiClient account;
    private Markwon markdown;
    private LinearLayout body;
    private ScrollView scroll;
    private MaterialButton refresh;
    private String agent,selected="",content="",error="",catalogError="";
    private JSONArray files,promptFiles;
    private JSONObject heartbeat;
    private Spanned rendered;
    private int revision;
    private boolean loading,sourceMode,missing;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new Ui(this);chat=((PawApplication)getApplication()).ensureChat();
        if(!chat.loggedIn){finish();return;}account=chat.api();agent=state==null?account.agent:state.getString("agent",account.agent);
        selected=state==null?"":state.getString("document","");if(!selected.isEmpty())try{AgentDocument.find(selected);}catch(IllegalArgumentException e){selected="";}
        markdown=Markwon.builder(this).usePlugin(TablePlugin.create(this)).usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(this))
            .usePlugin(new AbstractMarkwonPlugin(){@Override public void configureConfiguration(MarkwonConfiguration.Builder builder){builder.linkResolver((view,link)->openLink(link));}}).build();
        createScreen();load();
    }
    @Override protected void onResume(){super.onResume();if(account!=null&&!sameAccount())finish();}
    @Override protected void onDestroy(){revision++;io.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("agent",agent);out.putString("document",selected);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(!selected.isEmpty()){selected="";scroll.scrollTo(0,0);load();}else super.onBackPressed();}
    private boolean sameAccount(){return chat.loggedIn&&account.base.equals(chat.base)&&account.token.equals(chat.token);}
    private boolean current(int request){return !isFinishing()&&!isDestroyed()&&sameAccount()&&revision==request;}
    private void createScreen(){
        LinearLayout root=ui.column();root.setBackgroundColor(Ui.BG);root.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(12));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(ui.dp(16)+i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),ui.dp(16)+i.getSystemWindowInsetRight(),ui.dp(12)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
        LinearLayout head=ui.row();head.addView(ui.iconButton("返回个人中心或档案列表","close",v->onBackPressed()));head.addView(ui.heading("智能体档案",21),new LinearLayout.LayoutParams(0,-2,1));refresh=ui.iconButton("刷新智能体档案","loop",v->load());head.addView(refresh);root.addView(head);
        LinearLayout switcher=ui.row();ui.padding(switcher,12,6);switcher.setBackground(ui.shape(Ui.WHITE,14,Ui.LINE));switcher.addView(ui.text("查看智能体",12,Ui.MUTED));
        ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();
        for(int i=0;i<chat.agents.length();i++){JSONObject item=chat.agents.optJSONObject(i);if(item==null||item.optString("id").isBlank())continue;String id=item.optString("id");ids.add(id);names.add(item.optString("name",id)+" · "+id);}
        if(!ids.contains(agent)){ids.add(agent);names.add(agent);}
        Spinner selector=new Spinner(this);selector.setContentDescription("选择档案所属智能体");ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);selector.setAdapter(adapter);selector.setSelection(ids.indexOf(agent));switcher.addView(selector,new LinearLayout.LayoutParams(0,ui.dp(44),1));root.addView(switcher);ui.gap(root,8);
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){String target=ids.get(position);if(!target.equals(agent)){agent=target;scroll.scrollTo(0,0);load();}}});
        scroll=new ScrollView(this);scroll.setFillViewport(true);body=ui.column();ui.padding(body,2,12);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void load(){
        int request=++revision;ApiClient api=new ApiClient(account.base,account.token,agent);String target=selected;
        loading=true;error="";catalogError="";content="";rendered=null;missing=false;sourceMode=false;files=null;promptFiles=null;heartbeat=null;render();
        // Optional status failures never prevent reading a document.
        io.execute(()->{
            JSONArray nextFiles=null,nextPrompt=null;JSONObject nextHeartbeat=null;String failure="";
            try{Object value=api.call("GET","/workspace/files",null);if(!(value instanceof JSONArray))throw new IOException();nextFiles=(JSONArray)value;}catch(Exception e){if(unauthorized(e,request))return;failure="文件目录暂未读取成功，仍可逐项查看或刷新重试。";}
            try{Object value=api.call("GET","/workspace/system-prompt-files",null);if(value instanceof JSONArray)nextPrompt=(JSONArray)value;}catch(Exception e){if(unauthorized(e,request))return;}
            try{Object value=api.call("GET","/config/heartbeat",null);if(value instanceof JSONObject)nextHeartbeat=(JSONObject)value;}catch(Exception e){if(unauthorized(e,request))return;}
            JSONArray listed=nextFiles,prompts=nextPrompt;JSONObject hb=nextHeartbeat;String warning=failure;
            runOnUiThread(()->{if(!current(request))return;files=listed;promptFiles=prompts;heartbeat=hb;catalogError=warning;if(target.isEmpty())loading=false;render();});
        });
        if(!target.isEmpty())io.execute(()->{
            try{
                AgentDocument.find(target);Object value=api.call("GET","/workspace/files/"+ApiClient.enc(target),null);if(!(value instanceof JSONObject))throw new IOException();
                String raw=AgentDocument.content((JSONObject)value);if(raw.length()>500_000)throw new IOException("文档较长，请在网页控制台的文件模块查看全文");
                String display=AgentDocument.markdown(raw);Spanned text=markdown.render(markdown.parse(display));
                runOnUiThread(()->{if(!current(request))return;content=raw;rendered=text;loading=false;render();});
            }catch(Exception e){if(unauthorized(e,request))return;runOnUiThread(()->{if(!current(request))return;loading=false;missing=e instanceof ApiClient.ApiException a&&a.status==404;
                error=missing?"当前智能体尚未创建这份档案。":e instanceof ApiClient.ApiException a&&a.status==403?"当前账号无权读取这份档案。":e instanceof IOException&&e.getMessage()!=null&&e.getMessage().startsWith("文档较长")?e.getMessage():"暂时无法读取正文，请刷新重试。";render();});}
        });
    }
    private boolean unauthorized(Exception e,int request){if(e instanceof ApiClient.ApiException a&&a.status==401){runOnUiThread(()->{if(current(request)){chat.failure(e);finish();}});return true;}return false;}
    private LinearLayout card(int color){LinearLayout card=ui.column();ui.padding(card,16,16);card.setBackground(ui.shape(color,18,color==Ui.WHITE?Ui.LINE:0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=ui.dp(12);body.addView(card,p);return card;}
    private void paragraph(LinearLayout parent,String text,int size,int color){TextView view=ui.text(text,size,color);view.setLineSpacing(ui.dp(4),1);parent.addView(view);}
    private void render(){
        if(isFinishing()||isDestroyed())return;int oldY=scroll.getScrollY();body.removeAllViews();refresh.setEnabled(!loading);
        if(selected.isEmpty()){
            LinearLayout intro=card(Ui.TINT);intro.addView(ui.heading("了解你的智能体",23));ui.gap(intro,10);paragraph(intro,"它怎样做事、如何与你相处、记住了谁，以及会主动检查什么。",14,Ui.GREEN);
            if(loading)paragraph(body,"正在读取当前智能体的档案状态…",12,Ui.MUTED);
            if(!catalogError.isEmpty())paragraph(body,catalogError,12,Ui.MUTED);
            for(AgentDocument.Kind kind:AgentDocument.ALL){
                LinearLayout item=card(Ui.WHITE),heading=ui.row();heading.addView(ui.icon(kind.icon(),Ui.GREEN,24));TextView title=ui.heading(kind.title(),17);title.setPadding(ui.dp(10),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));heading.addView(ui.icon("right",Ui.MUTED,18));item.addView(heading);ui.gap(item,10);paragraph(item,kind.summary(),13,Ui.MUTED);ui.gap(item,12);
                JSONObject info=AgentDocument.metadata(files,kind.file());paragraph(item,kind.file()+" · "+(files==null?(loading?"正在读取":"目录状态未知"):info==null?"尚未创建":"可查看"),11,Ui.MUTED);
                paragraph(item,AgentDocument.status(kind,promptFiles,heartbeat),11,Ui.GREEN);item.setContentDescription("查看"+kind.title());item.setFocusable(true);item.setOnClickListener(v->{selected=kind.file();scroll.scrollTo(0,0);load();});
            }
        }else{
            AgentDocument.Kind kind=AgentDocument.find(selected);LinearLayout intro=card(Ui.TINT);intro.addView(ui.icon(kind.icon(),Ui.GREEN,30));ui.gap(intro,12);intro.addView(ui.heading(kind.title(),24));ui.gap(intro,8);paragraph(intro,kind.summary(),14,Ui.GREEN);ui.gap(intro,12);paragraph(intro,kind.examples(),12,Ui.MUTED);ui.gap(intro,8);paragraph(intro,kind.usage(),12,Ui.MUTED);
            paragraph(body,kind.file()+" · "+AgentDocument.status(kind,promptFiles,heartbeat),12,Ui.GREEN);String updated=AgentDocument.modified(AgentDocument.metadata(files,selected));if(!updated.isEmpty())paragraph(body,"文件更新于 "+updated+"（本地时间）",11,Ui.MUTED);ui.gap(body,16);
            LinearLayout tools=ui.row();tools.addView(ui.heading("当前文档",18),new LinearLayout.LayoutParams(0,-2,1));
            if(!loading&&error.isEmpty()){
                MaterialButton source=ui.button(sourceMode?"阅读模式":"Markdown 源码",null,false,v->{sourceMode=!sourceMode;render();});source.setTextSize(11);tools.addView(source);tools.addView(ui.iconButton("复制文档源码","copy",v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(selected,content));Toast.makeText(this,"已复制文档源码",Toast.LENGTH_SHORT).show();}));
            }body.addView(tools);ui.gap(body,12);
            if(loading)paragraph(body,"正在读取文档…",14,Ui.MUTED);
            else if(!error.isEmpty()){
                LinearLayout notice=card(Ui.WHITE);paragraph(notice,error,14,Ui.MUTED);if(missing)paragraph(notice,"可以在网页控制台的【文件】模块补充这份档案。",12,Ui.MUTED);notice.addView(ui.button("重新读取",null,false,v->load()));
            }else if(content.isBlank()||!sourceMode&&AgentDocument.markdown(content).isBlank()){
                paragraph(body,content.isBlank()?"这份档案目前还没有正文。":"文档目前只有说明元数据，可切换到 Markdown 源码查看。",14,Ui.MUTED);
            }else{
                LinearLayout document=card(Ui.WHITE);TextView text=ui.text("",15,Ui.INK);text.setTag("agent-document-content");text.setTextIsSelectable(true);text.setLinkTextColor(Ui.GREEN);text.setLineSpacing(ui.dp(5),1);
                if(sourceMode){text.setTypeface(Typeface.MONOSPACE);text.setTextSize(12);text.setText(content);}else markdown.setParsedMarkdown(text,rendered);
                document.addView(text,new LinearLayout.LayoutParams(-1,-2));
            }
        }
        scroll.post(()->scroll.scrollTo(0,oldY));
    }
    private void openLink(String link){
        Uri uri=Uri.parse(link);String scheme=uri.getScheme();if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme)){Toast.makeText(this,"工作区内的链接可在网页控制台【文件】中查看",Toast.LENGTH_LONG).show();return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){Toast.makeText(this,"没有可打开链接的浏览器",Toast.LENGTH_SHORT).show();}
    }
}
