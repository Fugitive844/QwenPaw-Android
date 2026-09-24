package cn.qwenpaw.android;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements ChatController.Listener {
    public static final int INK=Ui.INK,MUTED=Ui.MUTED,ACCENT=Ui.GREEN,BG=Ui.BG;
    Ui design;
    private static final int PICK=10,SAVE=11;
    private ChatController c;
    private AppUpdater updater;
    private LinearLayout root,approvalBox,attachmentBox;
    private RecyclerView list;
    private MessageAdapter adapter;
    private TextView status,title;
    private View empty;
    private EditText input;
    private Button send,follow,modelButton,loopButton,levelButton,agentButton,usageButton;
    private Runnable panelRefresh;
    private BottomSheetDialog activePanel;
    private long panelScope;
    private String screen="",approvalSignature="",attachmentSignature="";
    private boolean syncing=false;
    private String downloadUrl="",downloadName="";
    private long pickerScope=-1,downloadScope=-1;
    private LinearLayout promptCards;
    private List<WelcomePrompts.Prompt> displayedPrompts;
    private boolean tailScrollQueued;
    private boolean userScrolling;
    private final Map<String,Long> lastNavigation=new HashMap<>();
    private boolean historyLoading;
    private long historyScope;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private Parcelable listState;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);design=new Ui(this);updater=new AppUpdater(this);
        PawApplication app=(PawApplication)getApplication();
        if(app.needsRecovery()){openRecovery();return;}
        try {
            c=app.ensureChat();
            if(state!=null){downloadUrl=state.getString("downloadUrl","");downloadName=state.getString("downloadName","");pickerScope=state.getLong("pickerScope",-1);downloadScope=state.getLong("downloadScope",-1);listState=state.getParcelable("listState");}
            if(c.loggedIn)showChat();else showLogin();
            if(c.loggedIn && c.agents.length()==0)c.start();
        }catch(RuntimeException e){app.markCrash();openRecovery();}
    }
    private void openRecovery(){screen="recovery";startActivity(new Intent(this,UpdateRecoveryActivity.class));finish();}
    @Override protected void onStart(){super.onStart();if(c!=null&&!isFinishing())c.attach(this);ui.postDelayed(()->{if(!isFinishing()&&!isDestroyed())updater.check(false);},1500);}
    @Override protected void onResume(){super.onResume();lastNavigation.clear();if(updater!=null&&!isFinishing())updater.resume();}
    @Override protected void onStop(){if(c!=null){c.saveDraft();c.detach(this);}super.onStop();}
    @Override protected void onDestroy(){if(updater!=null)updater.close();if(activePanel!=null)activePanel.dismiss();ui.removeCallbacksAndMessages(null);if(list!=null)list.setAdapter(null);super.onDestroy();}
    @Override public void onSaveInstanceState(Bundle state){
        state.putString("downloadUrl",downloadUrl);state.putString("downloadName",downloadName);state.putLong("pickerScope",pickerScope);state.putLong("downloadScope",downloadScope);
        if(list!=null)state.putParcelable("listState",list.getLayoutManager().onSaveInstanceState());super.onSaveInstanceState(state);
    }
    public int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    public LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    public TextView text(String value,int size,int color){return design.text(value,size,color);}
    public Button button(String label,View.OnClickListener action){return design.button(label,null,false,action);}
    private EditText field(String hint,String value){EditText e=new androidx.appcompat.widget.AppCompatEditText(this);e.setHint(hint);e.setText(value);e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setSingleLine(true);e.setPadding(dp(16),dp(14),dp(16),dp(14));e.setBackground(design.shape(Color.WHITE,14,Ui.LINE));e.setMinHeight(dp(52));return e;}
    private void label(LinearLayout parent,String label){TextView t=text(label,12,MUTED);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(2),dp(12),0,dp(8));parent.addView(t,p);}
    private void setRoot(LinearLayout content){
        root=content;root.setBackgroundColor(BG);root.setPadding(dp(16),0,dp(16),dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left=insets.getSystemWindowInsetLeft(),right=insets.getSystemWindowInsetRight();
            v.setPadding(dp(16)+left,insets.getSystemWindowInsetTop(),dp(16)+right,dp(8)+insets.getSystemWindowInsetBottom());return insets;
        });root.requestApplyInsets();
    }
    private Spinner spinner(String[] values,int selected){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values){
        @Override public View getView(int position,View convert,ViewGroup parent){TextView t=design.heading(getItem(position),14);t.setPadding(dp(10),0,0,0);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    };a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(selected);return s;}
    private void showLogin(){
        screen="login";LinearLayout outer=column();setRoot(outer);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);outer.addView(scroll,new LinearLayout.LayoutParams(-1,-1));LinearLayout form=column();form.setPadding(dp(8),dp(28),dp(8),dp(24));scroll.addView(form);
        LinearLayout brand=row();ImageView logo=design.icon("paw",ACCENT,32);brand.addView(logo);TextView word=design.heading("QwenPaw",20);word.setPadding(dp(10),0,0,0);brand.addView(word);form.addView(brand);design.gap(form,24);
        form.addView(design.heading("连接你的智能体",30));design.gap(form,12);TextView subtitle=text("想法、问题和灵感，都从这里开始。",14,MUTED);subtitle.setLineSpacing(dp(4),1);form.addView(subtitle);design.gap(form,12);
        okhttp3.HttpUrl current=null;try{current=okhttp3.HttpUrl.get(c.base);}catch(Exception ignored){}
        label(form,"服务器地址");LinearLayout address=row();address.setBackground(design.shape(Color.WHITE,14,Ui.LINE));
        Spinner protocol=spinner(new String[]{"https","http"},current!=null && current.scheme().equals("http")?1:0);address.addView(protocol,new LinearLayout.LayoutParams(dp(96),dp(54)));
        View divider=new View(this);divider.setBackgroundColor(Ui.LINE);address.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(22)));
        EditText host=field("IP 或域名",current==null?"":current.host());host.setBackgroundColor(Color.TRANSPARENT);host.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.addView(host,new LinearLayout.LayoutParams(0,dp(54),1));form.addView(address);
        label(form,"账号");EditText user=field("用户名",c.username);form.addView(user);
        label(form,"密码");LinearLayout passwordRow=row();passwordRow.setBackground(design.shape(Color.WHITE,14,Ui.LINE));EditText password=field("密码","");password.setBackgroundColor(Color.TRANSPARENT);password.setInputType(129);password.setTransformationMethod(PasswordTransformationMethod.getInstance());passwordRow.addView(password,new LinearLayout.LayoutParams(0,dp(54),1));
        MaterialButton reveal=design.iconButton("显示密码","eye",v->{boolean hidden=password.getTransformationMethod()!=null;password.setTransformationMethod(hidden?null:PasswordTransformationMethod.getInstance());password.setSelection(password.length());v.setContentDescription(hidden?"隐藏密码":"显示密码");});passwordRow.addView(reveal);form.addView(passwordRow);
        LinearLayout advanced=column();advanced.setVisibility(View.GONE);MaterialButton advancedToggle=design.button("高级连接选项","settings",false,v->{advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});design.tone(advancedToggle,Color.TRANSPARENT,MUTED);LinearLayout.LayoutParams toggleParams=new LinearLayout.LayoutParams(-2,dp(48));toggleParams.topMargin=dp(8);form.addView(advancedToggle,toggleParams);form.addView(advanced);
        label(advanced,"端口");EditText port=field("端口（留空使用默认值）",current==null || current.port()==80 || current.port()==443?"":String.valueOf(current.port()));port.setInputType(2);advanced.addView(port);
        label(advanced,"服务器路径");EditText prefix=field("路径前缀（可选，不包含 /api）",current==null?"":current.encodedPath().replaceAll("/+$",""));advanced.addView(prefix);
        label(advanced,"访问令牌");EditText token=field("访问令牌（可选，令牌认证服务器使用）","");token.setInputType(129);token.setTransformationMethod(PasswordTransformationMethod.getInstance());advanced.addView(token);design.gap(advanced,8);
        status=text(c.status,12,MUTED);status.setPadding(dp(2),dp(4),0,dp(12));form.addView(status);
        Button connect=design.button("连接服务器","right",true,v->{
            if(c.busy)return;
            try{String baseAddress=ServerAddress.create(protocol.getSelectedItem().toString(),host.getText().toString(),port.getText().toString(),prefix.getText().toString());
                screen="connecting";c.login(baseAddress,user.getText().toString().trim(),password.getText().toString(),token.getText().toString().trim());password.setText("");token.setText("");
            }catch(Exception e){notice(e.getMessage());}
        });connect.setTextSize(15);form.addView(connect,new LinearLayout.LayoutParams(-1,dp(60)));design.gap(form,16);
        TextView privacy=text("密码不保存到设备 · 登录令牌加密存储",11,MUTED);privacy.setGravity(Gravity.CENTER);form.addView(privacy);
        TextView protocolNote=text("建议使用 HTTPS 保护连接",11,MUTED);protocolNote.setGravity(Gravity.CENTER);design.gap(form,6);form.addView(protocolNote);
        form.addView(button("检查更新 · "+updater.version(),v->updater.check(true)));
        if(c.loggedIn)form.addView(button("返回聊天",v->showChat()));
    }
    private void showChat(){
        screen="chat";approvalSignature="";attachmentSignature="";LinearLayout page=column();setRoot(page);
        LinearLayout top=row();top.addView(design.iconButton("更多选项","more",v->more()));
        title=design.heading("QwenPaw",17);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(8),0,dp(8),0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton newChat=design.iconButton("新建对话","new",v->{if(allowNavigation("new-chat"))c.newChat();});newChat.setTooltipText("新建对话");top.addView(newChat);top.addView(design.iconButton("历史对话","clock",v->history()));page.addView(top,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout selectors=row();agentButton=design.chip("工作区","folder",v->agents());modelButton=design.chip("模型","chevron",v->models());modelButton.setContentDescription("模型设置");
        selectors.addView(agentButton,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams modelParams=new LinearLayout.LayoutParams(0,dp(48),2);modelParams.leftMargin=dp(8);selectors.addView(modelButton,modelParams);page.addView(selectors);
        status=text("已连接",11,MUTED);status.setMaxLines(2);status.setPadding(dp(4),dp(4),dp(4),dp(4));page.addView(status);
        FrameLayout center=new FrameLayout(this);page.addView(center,new LinearLayout.LayoutParams(-1,0,1));
        list=new FollowRecyclerView(this);((FollowRecyclerView)list).afterLayout=()->{if(c.autoFollow)scrollBottom();else followChanged();};list.setLayoutManager(new LinearLayoutManager(this));list.setItemAnimator(null);list.setHasFixedSize(false);list.setClipToPadding(false);list.setPadding(0,dp(12),0,dp(12));list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter=new MessageAdapter(this,c);list.setAdapter(adapter);center.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=welcome();center.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        follow=design.button("回到最新","down",true,v->{userScrolling=false;list.stopScroll();c.autoFollow=true;c.expanded.clear();adapter.update(c.transcript.rows());followChanged();scrollBottom();});
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);bottom.bottomMargin=dp(8);center.addView(follow,bottom);
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrollStateChanged(RecyclerView v,int state){
                if(state==RecyclerView.SCROLL_STATE_DRAGGING){userScrolling=true;c.pauseFollow();followChanged();}
                else if(state==RecyclerView.SCROLL_STATE_IDLE && userScrolling){userScrolling=false;resumeFollowAtBottom();}
            }
            @Override public void onScrolled(RecyclerView v,int dx,int dy){
                if(userScrolling && dy>0)resumeFollowAtBottom();
                else if(userScrolling && dy<0 && c.autoFollow && v.canScrollVertically(1))c.pauseFollow();
                // DRAGGING starts before the first pixels move away from the bottom.
                followChanged();
            }
        });
        approvalBox=column();ScrollView approvalScroll=new ScrollView(this);approvalScroll.addView(approvalBox);page.addView(approvalScroll,new LinearLayout.LayoutParams(-1,-2));
        approvalScroll.setOnHierarchyChangeListener(null);
        attachmentBox=column();page.addView(attachmentBox);
        LinearLayout composer=column();composer.setPadding(dp(8),dp(4),dp(8),dp(6));composer.setBackground(design.shape(Color.WHITE,26,Ui.LINE));
        input=field("发送消息…",c.draft);input.setSingleLine(false);input.setMinLines(1);input.setMaxLines(5);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setBackgroundColor(Color.TRANSPARENT);input.setTextSize(15);input.setPadding(dp(12),dp(12),dp(12),dp(8));composer.addView(input);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!syncing){c.draft=s.toString();updateSend();}}public void afterTextChanged(Editable e){}});
        LinearLayout modes=row();loopButton=design.chip("Loop","loop",v->loopModes());design.tone((MaterialButton)loopButton,Color.TRANSPARENT,MUTED);modes.addView(loopButton,new LinearLayout.LayoutParams(-2,dp(48)));levelButton=design.chip("自动模式","shield",v->approvalModes());design.tone((MaterialButton)levelButton,Color.TRANSPARENT,MUTED);modes.addView(levelButton,new LinearLayout.LayoutParams(-2,dp(48)));modes.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));usageButton=design.chip("用量",null,v->contextUsage());usageButton.setContentDescription("上下文用量");modes.addView(usageButton,new LinearLayout.LayoutParams(-2,dp(48)));page.addView(modes);
        LinearLayout tools=row();tools.addView(design.iconButton("添加附件","plus",v->pickFiles()));MaterialButton commands=design.button("命令","command",false,v->commands());commands.setContentDescription("快捷命令");design.tone(commands,Color.TRANSPARENT,MUTED);tools.addView(commands,new LinearLayout.LayoutParams(-2,dp(48)));
        MaterialButton navigation=design.button("导航","menu",false,v->questionNavigation());navigation.setContentDescription("对话导航");navigation.setSingleLine(true);design.tone(navigation,Color.TRANSPARENT,MUTED);tools.addView(navigation,new LinearLayout.LayoutParams(-2,dp(48)));tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        send=design.iconButton("发送","arrow",v->{if(c.running)c.stop();else c.send();});design.tone((MaterialButton)send,ACCENT,Color.WHITE);tools.addView(send);composer.addView(tools);page.addView(composer);changed();
        if(listState!=null){list.getLayoutManager().onRestoreInstanceState(listState);listState=null;}
    }
    private View welcome(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout content=column();content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(dp(12),dp(24),dp(12),dp(24));scroll.addView(content);
        ImageView mark=design.icon("paw",ACCENT,52);mark.setPadding(dp(10),dp(10),dp(10),dp(10));mark.setBackground(design.shape(Ui.TINT,18,0));content.addView(mark);design.gap(content,24);
        TextView headline=design.heading("有什么想聊的？",28);content.addView(headline);design.gap(content,12);TextView intro=text("一起理清思路，把想法变成下一步。",14,MUTED);intro.setLineSpacing(dp(4),1);content.addView(intro);design.gap(content,28);
        LinearLayout recommendations=row();TextView caption=text("试着问问",12,MUTED);recommendations.addView(caption,new LinearLayout.LayoutParams(0,-2,1));recommendations.addView(design.iconButton("换一组提示词","loop",v->{c.refreshWelcomePrompts();renderPrompts();}));content.addView(recommendations);
        promptCards=column();content.addView(promptCards);displayedPrompts=null;renderPrompts();
        final int[] layoutMode={-1};scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int mode=b-t<dp(240)?2:b-t<dp(390)?1:0;if(layoutMode[0]==mode)return;layoutMode[0]=mode;
            for(int i:new int[]{3,4,5})content.getChildAt(i).setVisibility(mode==2?View.GONE:View.VISIBLE);
            content.getChildAt(0).setVisibility(mode==0?View.VISIBLE:View.GONE);content.getChildAt(1).setVisibility(mode==0?View.VISIBLE:View.GONE);
            content.setPadding(dp(12),dp(mode==0?24:12),dp(12),dp(mode==0?24:12));headline.setTextSize(mode==2?20:mode==1?26:28);
        });return scroll;
    }
    private void renderPrompts(){
        if(promptCards==null||displayedPrompts==c.welcomePrompts)return;displayedPrompts=c.welcomePrompts;promptCards.removeAllViews();
        for(WelcomePrompts.Prompt prompt:displayedPrompts){
            LinearLayout card=column();card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(design.ripple(Color.WHITE,16));card.addView(design.heading(prompt.title(),14));design.gap(card,6);
            TextView summary=text(prompt.text(),12,MUTED);summary.setMaxLines(3);summary.setEllipsize(TextUtils.TruncateAt.END);summary.setLineSpacing(dp(3),1);card.addView(summary);
            card.setContentDescription("推荐提问："+prompt.title());card.setOnClickListener(v->{c.usePrompt(prompt);input.requestFocus();});card.setFocusable(true);promptCards.addView(card,new LinearLayout.LayoutParams(-1,-2));design.gap(promptCards,10);
        }
    }
    @Override public void changed(){
        if(activePanel!=null&&panelScope!=c.scope()){activePanel.dismiss();}else if(panelRefresh!=null)panelRefresh.run();
        if(!c.loggedIn){if(!screen.equals("login") && !screen.equals("connecting"))showLogin();if(status!=null)status.setText(c.status);return;}
        if(screen.equals("connecting")){showChat();return;}if(!screen.equals("chat"))return;
        title.setText(c.chat==null?"QwenPaw":c.chat.optString("name","新对话"));status.setText(c.status+(c.uploads>0?" · 正在上传 "+c.uploads+" 个文件":""));status.setVisibility(c.status.equals("已连接") && c.uploads==0?View.GONE:View.VISIBLE);
        agentButton.setText(agentName());modelButton.setText(currentModelLabel());
        loopButton.setText("Loop · "+loopName(c.selectedLoop));levelButton.setText(levelName(c.approvalLevel));design.tone((MaterialButton)levelButton,levelBackground(c.approvalLevel),levelInk(c.approvalLevel));((MaterialButton)levelButton).setIcon(new Ui.Glyph(levelGlyph(c.approvalLevel),levelInk(c.approvalLevel)));updateUsage();updateSend();
        if(!input.getText().toString().equals(c.draft)){syncing=true;input.setText(c.draft);input.setSelection(input.length());syncing=false;}
        List<Transcript.Row> rows=c.transcript.rows();adapter.update(rows);empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
        renderPrompts();renderAttachments();renderApprovals();followChanged();if(c.autoFollow)scrollBottom();
    }
    private void updateSend(){if(send==null)return;send.setContentDescription(c.stopping()?"正在停止":c.running?"停止":"发送");((MaterialButton)send).setIcon(new Ui.Glyph(c.running?"stop":"arrow",Color.WHITE));send.setEnabled(!c.stopping() && (c.running || (!c.busy && c.uploads==0 && (!c.draft.isBlank() || !c.attachments.isEmpty()))));}
    private void resumeFollowAtBottom(){
        if(list==null||c.autoFollow||list.canScrollVertically(1))return;
        c.autoFollow=true;c.expanded.clear();adapter.update(c.transcript.rows());followChanged();scrollBottom();
    }
    public void followChanged(){
        if(follow!=null)follow.setVisibility(c.autoFollow||list==null?View.GONE:View.VISIBLE);
        if(c.autoFollow&&adapter!=null&&list!=null)adapter.followProcessDetails(list);
    }
    private void scrollBottom(){
        if(!c.autoFollow||list==null||tailScrollQueued)return;
        tailScrollQueued=true;list.postOnAnimation(()->{
            tailScrollQueued=false;if(!c.autoFollow||isDestroyed()||!list.isAttachedToWindow()||list.getAdapter()==null||adapter.getItemCount()==0)return;
            if(list.isComputingLayout()||list.hasPendingAdapterUpdates()){scrollBottom();return;}
            LinearLayoutManager layout=(LinearLayoutManager)list.getLayoutManager();int last=adapter.getItemCount()-1;
            View tail=layout.findViewByPosition(last);
            if(tail==null){layout.scrollToPositionWithOffset(last,list.getHeight()-list.getPaddingTop()-list.getPaddingBottom()-1);return;}
            int delta=layout.getDecoratedBottom(tail)-(list.getHeight()-list.getPaddingBottom());
            if(delta>0)list.scrollBy(0,delta);
        });
    }
    @Override public void notice(String message){if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private String agentName(){for(int i=0;i<c.agents.length();i++){JSONObject a=c.agents.optJSONObject(i);if(a.optString("id").equals(c.agent))return a.optString("name",c.agent);}return c.agent;}
    private String currentModelLabel(){String id=c.thinking.optString("model","");if(id.isEmpty()||id.equals("null"))return "选择模型";
        for(int i=0;i<c.providers.length();i++){JSONObject p=c.providers.optJSONObject(i);if(!p.optString("id").equals(c.thinking.optString("provider_id")))continue;for(String key:List.of("models","extra_models")){JSONArray models=Json.array(p.opt(key));for(int j=0;j<models.length();j++){JSONObject m=models.optJSONObject(j);if(m.optString("id").equals(id))return m.optString("name",id);}}}return c.thinking.optString("model_name",id);}
    private String loopName(JSONObject mode){String id=mode.optString("id","default");if(mode.optString("source","builtin").equals("builtin"))return switch(id){case "default"->"默认";case "goal"->"目标";case "mission"->"任务";default->mode.optString("name",id);};JSONObject labels=mode.optJSONObject("name_i18n");return labels==null?mode.optString("name",id):labels.optString("zh-CN",labels.optString("zh",mode.optString("name",id)));}
    private String levelName(String level){return switch(level){case "STRICT"->"严格模式";case "SMART"->"智能模式";case "OFF"->"关闭模式";default->"自动模式";};}
    private void agents(){
        ArrayList<JSONObject> data=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(int i=0;i<c.agents.length();i++){JSONObject a=c.agents.optJSONObject(i);if(a.optBoolean("enabled",true)&&a.optBoolean("available_in_chat",true)){data.add(a);labels.add(a.optString("name")+"\n"+a.optString("workspace_dir"));}}
        showChoices("智能体工作区","选择与你一起工作的智能体",labels,index->c.selectAgent(data.get(index).optString("id"),false));
    }
    private void models(){
        if(c.thinkingLoading||c.busy||c.running){notice("请等待当前任务或模型加载结束");return;}
        ArrayList<JSONObject> models=new ArrayList<>();ArrayList<String> names=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<c.providers.length();i++){JSONObject p=c.providers.optJSONObject(i);if(!p.optBoolean("enabled",true))continue;
            boolean eligible=p.optString("id").equals("hub-managed") || p.optBoolean("is_free_tier") || p.optBoolean("oauth_connected") || !p.optString("api_key").isEmpty() || (p.optBoolean("is_custom") || !p.optBoolean("require_api_key",true)) && !p.optString("base_url").isEmpty();
            if(!eligible)continue;JSONArray hidden=Json.array(p.opt("hidden_model_ids"));
            for(String key:List.of("models","extra_models")){JSONArray items=Json.array(p.opt(key));for(int j=0;j<items.length();j++){JSONObject m=items.optJSONObject(j);String id=m.optString("id");boolean invisible=false;for(int n=0;n<hidden.length();n++)if(id.equals(hidden.optString(n)))invisible=true;if(invisible || !seen.add(p.optString("id")+":"+id))continue;
                models.add(Json.obj("provider_id",p.optString("id"),"model",id));names.add(m.optString("name",id)+"\n"+p.optString("name"));}}
        }
        if(names.isEmpty()){notice("尚未加载到可选模型，请刷新或先在服务端配置");c.refreshCatalog();return;}
        showModelList(names,index->c.chooseModel(models.get(index)));
    }
    private void loopModes(){
        if(c.loopActive){notice("当前 Loop 正在运行，可在对话中继续或使用命令按钮控制");return;}
        ArrayList<JSONObject> values=new ArrayList<>();values.add(Json.obj("id","default","source","builtin","slash_command",""));
        for(int i=0;i<c.loops.length();i++){JSONObject m=c.loops.optJSONObject(i);if(!m.optString("id").equals("default"))values.add(m);}
        values.sort(Comparator.comparing(m->!m.optString("source","builtin").equals("builtin")));
        List<String> labels=new ArrayList<>();for(JSONObject mode:values)labels.add(loopName(mode)+"\n"+LoopDescriptions.description(mode));
        ListView choices=panelList();BaseAdapter base=choiceAdapter(labels);
        choices.setAdapter(new BaseAdapter(){public int getCount(){return values.size();}public Object getItem(int p){return values.get(p);}public long getItemId(int p){return p;}public View getView(int p,View convert,ViewGroup parent){
            JSONObject mode=values.get(p);boolean builtin=mode.optString("source","builtin").equals("builtin"),selected=mode.optString("id").equals(c.selectedLoop.optString("id","default"));
            LinearLayout item=(LinearLayout)base.getView(p,convert,parent);item.setBackground(design.shape(selected?Ui.TINT:Color.WHITE,16,0));item.setSelected(selected);
            ImageView icon=design.icon(builtin?"loop":"command",selected?ACCENT:MUTED,22);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(22),dp(22));ip.rightMargin=dp(12);item.addView(icon,0,ip);
            if(selected){item.removeViewAt(item.getChildCount()-1);item.addView(design.icon("check",ACCENT,18));}
            LinearLayout wrapper=column();boolean start=p==0||builtin!=values.get(p-1).optString("source","builtin").equals("builtin");if(start){TextView group=text(builtin?"内置模式":"扩展模式",11,MUTED);group.setPadding(dp(8),dp(8),0,dp(8));wrapper.addView(group);}wrapper.addView(item);return wrapper;
        }});
        BottomSheetDialog dialog=sheet("Loop 模式","为这次对话选择工作方式",choices);choices.setOnItemClickListener((p,v,i,id)->{dialog.dismiss();c.selectedLoop=values.get(i);c.emit();});
    }
    private void approvalModes(){
        String[] levels={"STRICT","SMART","AUTO","OFF"};String[] descriptions={"每次工具调用和外部文件访问前均需审批","低风险工具调用和外部文件访问操作自动执行，中高风险需审批","仅命中工具和文件审批规则的操作需审批（默认）","关闭工具和文件审批"};
        String[] labels=new String[4];for(int i=0;i<4;i++)labels[i]=levelName(levels[i])+"\n"+descriptions[i];
        hideKeyboard();ListView choices=panelList();BaseAdapter base=choiceAdapter(Arrays.asList(labels));
        choices.setAdapter(new BaseAdapter(){public int getCount(){return 4;}public Object getItem(int p){return levels[p];}public long getItemId(int p){return p;}public View getView(int p,View convert,ViewGroup parent){LinearLayout item=(LinearLayout)base.getView(p,convert,parent);item.setBackground(design.shape(levelBackground(levels[p]),16,0));ImageView icon=design.icon(levelGlyph(levels[p]),levelAccent(levels[p]),22);LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(22),dp(22));iconParams.rightMargin=dp(12);item.addView(icon,0,iconParams);LinearLayout words=(LinearLayout)item.getChildAt(1);((TextView)words.getChildAt(0)).setTextColor(levelInk(levels[p]));if(c.approvalLevel.equals(levels[p])){item.removeViewAt(item.getChildCount()-1);item.addView(design.icon("check",levelInk(levels[p]),18));}return item;}});
        BottomSheetDialog dialog=sheet("工具审批模式","决定智能体何时需要你的确认",choices);choices.setOnItemClickListener((p,v,index,id)->{dialog.dismiss();c.setApprovalLevel(levels[index]);});
    }
    private int levelAccent(String level){return switch(level){case "STRICT"->0xffff4d4f;case "SMART"->0xfffaad14;case "OFF"->0xff52c41a;default->0xff1890ff;};}
    private int levelInk(String level){return switch(level){case "STRICT"->0xffb8323b;case "SMART"->0xff946200;case "OFF"->0xff2e7d17;default->0xff0969bb;};}
    private int levelBackground(String level){return switch(level){case "STRICT"->0xffffecec;case "SMART"->0xfffff5dc;case "OFF"->0xffedf8e7;default->0xffe9f3ff;};}
    private String levelGlyph(String level){return switch(level){case "STRICT"->"ban";case "SMART"->"warning";case "OFF"->"check";default->"shield";};}

    private void thinking(){
        List<String> levels=ThinkingOptions.levels(c.sessionModels,c.thinking);if(c.thinkingLoading||c.busy||c.running||levels.isEmpty())return;
        long scope=c.scope();String modelKey=c.thinking.optString("model_key");JSONObject control=Json.copy(Json.object(c.thinking.opt("control")));
        JSONObject selected=Json.object(c.thinking.opt("value"));ArrayList<String> names=new ArrayList<>();
        for(String level:levels)names.add((level.equals(selected.optString("level","inherit"))?"✓ ":"")+ThinkingOptions.label(level));
        showChoices("思考强度",currentModelLabel()+" · 仅作用于当前对话和模型",names,i->{
            if(scope!=c.scope()||!modelKey.equals(c.thinking.optString("model_key")))return;
            if(levels.get(i).equals("budget")){
                int min=control.optInt("budget_min"),max=control.optInt("budget_max");
                EditText tokens=field("Token 数量",String.valueOf(selected.optInt("budget_tokens",control.optInt("budget_default",min))));tokens.setInputType(2);
                AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("思考预算").setMessage("范围："+min+"–"+max+" Token").setView(tokens).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
                dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(scope!=c.scope()||!modelKey.equals(c.thinking.optString("model_key"))){dialog.dismiss();return;}
                    try{int value=Integer.parseInt(tokens.getText().toString());if(value<min||value>max)throw new IllegalArgumentException();c.chooseThinking(Json.obj("level","budget","budget_tokens",value));dialog.dismiss();}catch(IllegalArgumentException e){tokens.setError("请输入 "+min+"–"+max+" 之间的整数");}
                }));dialog.show();
            }else c.chooseThinking(Json.obj("level",levels.get(i)));
        });
    }
    private void hideKeyboard(){
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        input.clearFocus();root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void trackPanel(BottomSheetDialog dialog,Runnable refresh){
        activePanel=dialog;panelScope=c.scope();panelRefresh=refresh;
        dialog.setOnDismissListener(d->{if(activePanel==dialog){activePanel=null;panelRefresh=null;}});
    }
    private void commands(){
        hideKeyboard();LinearLayout box=column();box.setFocusableInTouchMode(true);
        EditText search=field("搜索命令、技能或说明","");box.addView(search);design.gap(box,8);
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout categories=row();tabs.addView(categories);box.addView(tabs);
        String[] filter={"全部"};ArrayList<MaterialButton> tabButtons=new ArrayList<>();
        TextView summary=text("",11,MUTED);summary.setPadding(dp(4),dp(8),0,dp(8));box.addView(summary);
        ListView view=panelList();view.setVerticalScrollBarEnabled(true);view.setContentDescription("命令列表");box.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<CommandCatalog.Entry> shown=new ArrayList<>();
        BaseAdapter ad=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){CommandCatalog.Entry entry=shown.get(p);LinearLayout item=row();item.setPadding(dp(14),dp(14),dp(14),dp(14));item.setBackground(design.ripple(Color.WHITE,16));item.addView(design.icon(entry.category().equals("技能")?"spark":entry.category().equals("常用")?"command":"loop",ACCENT,22));LinearLayout words=column();words.setPadding(dp(12),0,dp(6),0);TextView heading=design.heading(entry.title(),14);words.addView(heading);design.gap(words,5);TextView code=text(entry.command()+" · "+entry.category(),11,ACCENT);words.addView(code);if(!entry.description().isEmpty()){design.gap(words,5);TextView description=text(entry.description(),12,MUTED);description.setMaxLines(2);description.setEllipsize(TextUtils.TruncateAt.END);words.addView(description);}item.addView(words,new LinearLayout.LayoutParams(0,-2,1));item.addView(design.icon("right",MUTED,16));return item;}};
        view.setAdapter(ad);
        Runnable populate=()->{shown.clear();List<CommandCatalog.Entry> all=CommandCatalog.build(c.loops,c.skills);for(CommandCatalog.Entry entry:all)if(entry.matches(search.getText().toString(),filter[0]))shown.add(entry);ad.notifyDataSetChanged();summary.setText(c.commandsLoading>0?"正在同步工作区命令…":!c.commandsError.isEmpty()?c.commandsError+"加载失败 · 点击此处重试":shown.isEmpty()?"没有匹配项 · 仅显示已启用且允许控制台使用的技能":"共 "+shown.size()+" 项 · 选择技能或模式后可补充任务内容");for(MaterialButton tab:tabButtons)design.tone(tab,tab.getText().toString().equals(filter[0])?Ui.TINT:Color.TRANSPARENT,tab.getText().toString().equals(filter[0])?ACCENT:MUTED);};
        for(String name:new String[]{"全部","常用","Loop / 插件","技能"}){MaterialButton tab=design.chip(name,null,v->{filter[0]=name;populate.run();view.setSelection(0);});tabButtons.add(tab);categories.addView(tab);}
        summary.setOnClickListener(v->c.refreshCommands());
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int n){populate.run();view.setSelection(0);}});
        box.requestFocus();BottomSheetDialog dialog=sheet("快捷命令","按需选择，无需输入斜杠",box);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);box.requestFocus();trackPanel(dialog,populate);populate.run();
        // Keep the list scrollable even while the user explicitly opens search's keyboard.
        LinearLayout panel=(LinearLayout)box.getParent();panel.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            if(!dialog.isShowing())return;android.graphics.Rect frame=new android.graphics.Rect();panel.getWindowVisibleDisplayFrame(frame);boolean keyboard=frame.height()<getResources().getDisplayMetrics().heightPixels*72/100;
            for(int i:new int[]{0,1,3,4})panel.getChildAt(i).setVisibility(keyboard?View.GONE:View.VISIBLE);
            tabs.setVisibility(keyboard?View.GONE:View.VISIBLE);summary.setVisibility(keyboard?View.GONE:View.VISIBLE);
            // The list receives the remaining full-panel height through its layout weight.
        });
        view.setOnItemClickListener((p,v,index,id)->{CommandCatalog.Entry entry=shown.get(index);dialog.dismiss();if(entry.category().equals("常用")){runContextCommand(entry.command());return;}String previous=c.draft;c.draft=entry.command()+" "+previous;c.saveDraft();c.emit();notice("已填入命令，可补充内容后发送");});
        c.refreshCommands();
    }
    private void runContextCommand(String command){
        if(command.equals("/new")||command.equals("/clear"))new MaterialAlertDialogBuilder(this).setTitle(command.equals("/new")?"开始新上下文？":"清空当前上下文？").setMessage(command.equals("/new")?"按服务端配置保存记忆，并重置当前上下文。输入草稿和待发附件会保留。":"智能体将不再使用当前上下文、压缩摘要和计划。输入草稿和待发附件会保留。").setNegativeButton("取消",null).setPositiveButton("继续",(d,n)->c.contextCommand(command)).show();
        else c.contextCommand(command);
    }
    private void updateUsage(){
        UsageSnapshot snapshot=c.transcript.metrics;boolean known=snapshot!=null&&snapshot.hasContext()&&snapshot.limit(c.activeContextLimit)>0;double value=known?snapshot.percent(c.activeContextLimit):0;
        usageButton.setText(known?UsageSnapshot.ratio(value):"用量");int color=value>=90?0xffb8323b:value>=75?0xff946200:MUTED;
        design.tone((MaterialButton)usageButton,Color.TRANSPARENT,color);((MaterialButton)usageButton).setIcon(new Ui.UsageRing(known?value:-1,color));usageButton.setContentDescription("上下文用量");
    }
    private void contextUsage(){
        hideKeyboard();LinearLayout content=column();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(content);
        Runnable populate=()->{content.removeAllViews();UsageSnapshot data=c.transcript.metrics;long limit=data==null?c.activeContextLimit:data.limit(c.activeContextLimit);boolean known=data!=null&&data.hasContext();double ratio=known?data.percent(c.activeContextLimit):0;
            LinearLayout card=column();card.setPadding(dp(20),dp(20),dp(20),dp(20));card.setBackground(design.shape(Color.WHITE,20,Ui.LINE));LinearLayout headline=row();TextView big=design.heading(known&&limit>0?UsageSnapshot.ratio(ratio):"等待上报",30);headline.addView(big,new LinearLayout.LayoutParams(0,-2,1));ImageView ring=new ImageView(this);ring.setImageDrawable(new Ui.UsageRing(known&&limit>0?ratio:-1,ratio>=90?0xffb8323b:ratio>=75?0xff946200:ACCENT));headline.addView(ring,new LinearLayout.LayoutParams(dp(40),dp(40)));card.addView(headline);design.gap(card,10);
            card.addView(text((known?UsageSnapshot.count(data.tokens()):"—")+" / "+(limit>0?UsageSnapshot.count(limit):"—")+" Tokens",14,INK));design.gap(card,8);card.addView(text("当前上下文占用 · 服务端估算",11,MUTED));content.addView(card);design.gap(content,18);
            content.addView(design.heading("会话缓存命中率",13));design.gap(content,6);content.addView(text(data!=null&&data.hasCache()?UsageSnapshot.ratio(data.cachePercent())+"  ·  "+UsageSnapshot.count(UsageSnapshot.number(data.usage,"session_cache_read_tokens"))+" / "+UsageSnapshot.count(UsageSnapshot.number(data.usage,"session_cache_eligible_input_tokens"))+" Tokens":"模型未上报",14,MUTED));
            if(data!=null&&UsageSnapshot.number(data.usage,"total_tokens")>0){design.gap(content,14);content.addView(text("最近一轮"+(data.usage.optBoolean("estimated")?"（估算）":"")+" · 输入 "+UsageSnapshot.count(UsageSnapshot.number(data.usage,"prompt_tokens"))+" / 输出 "+UsageSnapshot.count(UsageSnapshot.number(data.usage,"completion_tokens")),12,MUTED));}
            design.gap(content,16);TextView note=text(c.running?"任务正在进行，用量会随服务端上报更新。":"压缩会整理上下文，保留继续对话所需的信息。结果以服务端回复为准。",12,MUTED);note.setLineSpacing(dp(3),1);content.addView(note);design.gap(content,12);
            MaterialButton compact=design.button(c.running?"任务执行中":"压缩上下文","compress",true,v->{if(activePanel!=null)activePanel.dismiss();c.contextCommand("/compact");});compact.setEnabled(c.chat!=null&&!c.running&&!c.busy);content.addView(compact,new LinearLayout.LayoutParams(-1,dp(52)));
            MaterialButton fresh=design.button("开始新上下文","new",false,v->{if(activePanel!=null)activePanel.dismiss();runContextCommand("/new");});fresh.setEnabled(c.chat!=null&&!c.running&&!c.busy);content.addView(fresh,new LinearLayout.LayoutParams(-1,dp(52)));};
        populate.run();BottomSheetDialog dialog=sheet("上下文用量","随当前对话更新",scroll);trackPanel(dialog,populate);
    }
    private void renderAttachments(){
        String signature=c.attachments.toString();if(signature.equals(attachmentSignature))return;attachmentSignature=signature;attachmentBox.removeAllViews();
        for(JSONObject item:c.attachments){MaterialButton b=design.button(item.optString("file_name"),"close",false,v->{c.attachments.remove(item);c.emit();});b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setContentDescription("移除附件："+item.optString("file_name"));attachmentBox.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));}
    }
    private void renderApprovals(){
        String signature=c.approvals.toString();if(signature.equals(approvalSignature))return;approvalSignature=signature;approvalBox.removeAllViews();
        ViewGroup.LayoutParams layout=approvalBox.getParent() instanceof View?((View)approvalBox.getParent()).getLayoutParams():null;
        if(layout!=null){layout.height=c.approvals.length()>0?dp(160):0;((View)approvalBox.getParent()).setLayoutParams(layout);}
        for(int i=0;i<c.approvals.length();i++){JSONObject a=c.approvals.optJSONObject(i);LinearLayout box=column();box.setBackground(design.shape(0xfffbf6eb,20,0xffebe1c9));box.setPadding(dp(16),dp(14),dp(16),dp(8));
            LinearLayout heading=row();heading.addView(design.icon("shield",0xff94672b,20));TextView title=design.heading("需要你的确认",14);title.setPadding(dp(8),0,0,0);heading.addView(title);box.addView(heading);design.gap(box,8);
            String summary=Json.text(a.opt("reasoning"));if(summary.isEmpty())summary=Json.text(a.opt("tool_params"));TextView brief=text(a.optString("tool_name")+" · "+summary,12,MUTED);brief.setMaxLines(2);brief.setEllipsize(TextUtils.TruncateAt.END);brief.setLineSpacing(dp(3),1);box.addView(brief);design.gap(box,6);
            LinearLayout actions=row();MaterialButton details=design.button("查看详情",null,false,v->approvalDetails(a));design.tone(details,Color.TRANSPARENT,MUTED);actions.addView(details);actions.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));actions.addView(button("拒绝",v->{EditText reason=field("拒绝原因（可选）","");new MaterialAlertDialogBuilder(this).setTitle("拒绝工具调用").setView(reason).setPositiveButton("拒绝",(d,n)->c.decide(a,false,reason.getText().toString(),"exact")).setNegativeButton("取消",null).show();}));MaterialButton allow=design.button("批准","check",true,v->c.decide(a,true,"","exact"));LinearLayout.LayoutParams allowParams=new LinearLayout.LayoutParams(-2,dp(48));allowParams.leftMargin=dp(8);actions.addView(allow,allowParams);box.addView(actions);approvalBox.addView(box);}
    }
    private void approvalDetails(JSONObject a){
        ScrollView scroll=new ScrollView(this);TextView detail=text(Json.text(a),14,INK);detail.setTextIsSelectable(true);scroll.addView(detail);
        new MaterialAlertDialogBuilder(this).setTitle("审批详情").setView(scroll).setPositiveButton("批准本次",(d,n)->c.decide(a,true,"","exact")).setNeutralButton("批准同类",(d,n)->c.decide(a,true,"","similar")).setNegativeButton("返回",null).show();
    }
    private boolean allowNavigation(String key){
        long now=SystemClock.elapsedRealtime(),previous=lastNavigation.getOrDefault(key,0L);
        if(now-previous<2500)return false;
        lastNavigation.put(key,now);return true;
    }
    private void openPage(String key,Intent intent){if(allowNavigation(key))startActivity(intent);}
    private void more(){
        String[] actions={"个人中心","运行配置","环境变量管理","Token 消耗分析","智能体管理 · 技能","智能体管理 · 工具","全局设置 · 技能池管理","刷新 / 重连","搜索当前对话","导出当前对话","连接设置","退出登录","检查更新 · "+updater.version()};
        showChoices("对话选项","账号与设置 · 当前对话操作",Arrays.asList(actions),i->{switch(i){
            case 0->openPage("account",new Intent(this,AccountActivity.class));
            case 1,2,3->openPage("settings-"+i,new Intent(this,SettingsActivity.class).putExtra("page",i-1));
            case 4->openPage("skills",new Intent(this,SkillsActivity.class));
            case 5->openPage("tools",new Intent(this,ToolsActivity.class));
            case 6->openPage("skill-pool",new Intent(this,SkillsActivity.class).putExtra("pool",true));
            case 7->{if(allowNavigation("reconnect")){c.refreshCatalog();c.refreshChats(false);if(c.chat!=null)c.reconnect();}}
            case 8->searchMessages();case 9->exportChat();case 10->showLogin();case 11->c.logout();
            case 12->updater.check(true);
        }});
    }
    private BottomSheetDialog sheet(String title,String subtitle,View body){
        if(activePanel!=null)activePanel.dismiss();
        BottomSheetDialog dialog=new BottomSheetDialog(this);LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),dp(20));box.setBackground(design.shape(BG,28,0));
        View handle=new View(this);handle.setBackground(design.shape(0xffd7ded7,3,0));LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(32),dp(4));handleParams.gravity=Gravity.CENTER_HORIZONTAL;box.addView(handle,handleParams);design.gap(box,20);
        LinearLayout heading=row();TextView name=design.heading(title,22);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));heading.addView(design.iconButton("关闭面板","close",v->dialog.dismiss()));box.addView(heading);if(!subtitle.isEmpty()){TextView sub=text(subtitle,12,MUTED);sub.setLineSpacing(dp(3),1);box.addView(sub);design.gap(box,16);}
        if(body instanceof LinearLayout content){for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);if(child instanceof ListView||child instanceof ScrollView)child.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));}}
        box.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(box,new ViewGroup.LayoutParams(-1,-1));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View frame=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if(frame!=null){frame.getLayoutParams().height=ViewGroup.LayoutParams.MATCH_PARENT;frame.requestLayout();}
        BottomSheetBehavior<?> behavior=dialog.getBehavior();behavior.setFitToContents(true);behavior.setSkipCollapsed(true);behavior.setDraggable(false);behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();trackPanel(dialog,null);return dialog;
    }
    private ListView panelList(){ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(6));list.setSelector(design.ripple(Ui.SOFT,16));list.setVerticalScrollBarEnabled(false);return list;}
    private BaseAdapter choiceAdapter(List<String> labels){return new BaseAdapter(){
        public int getCount(){return labels.size();}public Object getItem(int p){return labels.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View convert,ViewGroup parent){String[] parts=labels.get(position).split("\n",2);LinearLayout item=row();item.setPadding(dp(16),dp(16),dp(12),dp(16));item.setBackground(design.shape(Color.WHITE,16,0));LinearLayout words=column();TextView label=design.heading(parts[0],14);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);words.addView(label);if(parts.length>1){design.gap(words,6);TextView sub=text(parts[1],12,MUTED);sub.setLineSpacing(dp(3),1);words.addView(sub);}item.addView(words,new LinearLayout.LayoutParams(0,-2,1));item.addView(design.icon("right",MUTED,16));return item;}
    };}
    private void showChoices(String title,String subtitle,List<String> labels,java.util.function.IntConsumer selected){
        ListView choices=panelList();choices.setAdapter(choiceAdapter(labels));BottomSheetDialog dialog=sheet(title,subtitle,choices);choices.setOnItemClickListener((p,v,index,id)->{dialog.dismiss();selected.accept(index);});
    }
    private void showModelList(List<String> names,java.util.function.IntConsumer selected){
        showSearchList("模型设置",names,selected,true);
    }
    private void showSearchList(String title,List<String> names,java.util.function.IntConsumer selected){
        showSearchList(title,names,selected,false);
    }
    private void showSearchList(String title,List<String> names,java.util.function.IntConsumer selected,boolean modelSelection){
        LinearLayout box=column();Button thinkingButton=design.button("思考强度", "spark",false,v->{});box.addView(thinkingButton,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText search=field(modelSelection?"搜索模型":"搜索","");box.addView(search);design.gap(box,12);ListView view=panelList();box.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<String> labels=new ArrayList<>(names);ArrayList<Integer> indices=new ArrayList<>();for(int i=0;i<names.size();i++)indices.add(i);
        BaseAdapter listAdapter=choiceAdapter(labels);view.setAdapter(listAdapter);
        BottomSheetDialog dialog=sheet(title,modelSelection?("当前模型："+currentModelLabel()+"\n"+(c.sessionModels?"模型与思考选项作用于当前对话":"此服务器的模型选择作用于当前工作区")):"搜索并定位当前对话中的消息",box);
        Runnable refresh=()->{boolean supported=modelSelection&&!c.thinkingLoading&&!ThinkingOptions.levels(c.sessionModels,c.thinking).isEmpty();thinkingButton.setVisibility(supported?View.VISIBLE:View.GONE);thinkingButton.setEnabled(!c.busy&&!c.running);JSONObject value=Json.object(c.thinking.opt("value"));thinkingButton.setText("思考强度 · "+ThinkingOptions.label(value.optString("level","inherit")));thinkingButton.setContentDescription("当前模型思考强度");};
        thinkingButton.setOnClickListener(v->{dialog.dismiss();thinking();});trackPanel(dialog,refresh);refresh.run();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int n){labels.clear();indices.clear();String q=s.toString().toLowerCase(Locale.ROOT);for(int i=0;i<names.size();i++)if(names.get(i).toLowerCase(Locale.ROOT).contains(q)){labels.add(names.get(i));indices.add(i);}listAdapter.notifyDataSetChanged();}});
        view.setOnItemClickListener((p,v,pos,id)->{dialog.dismiss();selected.accept(indices.get(pos));});
    }
    private void history(){loadHistory(true);}
    private void loadHistory(boolean throttle){
        if(historyLoading && historyScope==c.scope())return;
        if(throttle && !allowNavigation("history"))return;
        historyLoading=true;historyScope=c.scope();
        c.refreshChats(()->{if(historyScope!=c.scope())return;historyLoading=false;if(screen.equals("chat")&&!isFinishing()&&getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))showHistory();},
            ()->{if(historyScope==c.scope())historyLoading=false;});
    }
    private void showHistory(){
        LinearLayout box=column();EditText search=field("搜索历史对话","");box.addView(search);LinearLayout controls=row();Button archive=button(c.archived?"查看未归档":"查看已归档",v->{});controls.addView(archive);controls.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));MaterialButton create=design.button("新建对话","plus",true,v->{});controls.addView(create);box.addView(controls);design.gap(box,8);
        List<JSONObject> snapshot=ChatHistory.sorted(c.chats);
        ListView view=panelList();box.addView(view,new LinearLayout.LayoutParams(-1,0,1));ArrayList<JSONObject> shown=new ArrayList<>(snapshot);ArrayList<String> labels=new ArrayList<>();
        Runnable populate=()->{labels.clear();for(JSONObject s:shown)labels.add((s.optBoolean("pinned")?"★ ":"")+s.optString("name","New Chat")+"\n"+s.optString("updated_at","")+(s.optString("status").equals("running")?" · 运行中":""));};populate.run();
        BaseAdapter ad=choiceAdapter(labels);view.setAdapter(ad);
        BottomSheetDialog dialog=sheet("你的对话","继续上次的想法 · 长按对话可管理",box);create.setOnClickListener(v->{dialog.dismiss();c.newChat();});
        archive.setOnClickListener(v->{dialog.dismiss();c.archived=!c.archived;loadHistory(false);});
        JSONArray[] displayed={c.chats};
        trackPanel(dialog,()->{if(displayed[0]==c.chats)return;displayed[0]=c.chats;snapshot.clear();snapshot.addAll(ChatHistory.sorted(c.chats));shown.clear();String query=search.getText().toString().toLowerCase(Locale.ROOT);for(JSONObject item:snapshot)if(item.optString("name").toLowerCase(Locale.ROOT).contains(query))shown.add(item);populate.run();ad.notifyDataSetChanged();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int n){shown.clear();for(JSONObject item:snapshot)if(item.optString("name").toLowerCase(Locale.ROOT).contains(s.toString().toLowerCase(Locale.ROOT)))shown.add(item);populate.run();ad.notifyDataSetChanged();}});
        view.setOnItemClickListener((p,v,i,id)->{dialog.dismiss();c.open(shown.get(i));});view.setOnItemLongClickListener((p,v,i,id)->{dialog.dismiss();chatActions(shown.get(i));return true;});
    }
    private void chatActions(JSONObject chat){
        String path="/chats/"+ApiClient.enc(chat.optString("id"));String[] items={"重命名",chat.optBoolean("pinned")?"取消置顶":"置顶",chat.optBoolean("archived")?"取消归档":"归档","删除"};
        new MaterialAlertDialogBuilder(this).setTitle(chat.optString("name")).setItems(items,(d,i)->{
            if(i==0){EditText name=field("对话名称",chat.optString("name"));new MaterialAlertDialogBuilder(this).setTitle("重命名").setView(name).setPositiveButton("保存",(x,n)->{String value=name.getText().toString().trim();if(!value.isEmpty())updateChat("PUT",path,Json.obj("name",value),chat);}).setNegativeButton("取消",null).show();}
            else if(i==1)updateChat("PUT",path,Json.obj("pinned",!chat.optBoolean("pinned")),chat);
            else if(i==2)updateChat("POST",path+(chat.optBoolean("archived")?"/unarchive":"/archive"),null,chat);
            else new MaterialAlertDialogBuilder(this).setTitle("删除此对话？").setMessage("服务端对话记录将被删除，无法在 App 中撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(x,n)->updateChat("DELETE",path,null,chat)).show();
        }).show();
    }
    private void updateChat(String method,String path,Object body,JSONObject spec){c.task(a->a.call(method,path,body),r->{if(c.chat!=null && c.chat.optString("id").equals(spec.optString("id"))){if(method.equals("DELETE"))c.newChat();else if(r instanceof JSONObject o)c.chat=o;}c.refreshChats(false);c.emit();});}
    private void questionNavigation(){
        hideKeyboard();List<QuestionNavigator.Question> questions=QuestionNavigator.questions(c.transcript.rows());
        long scope=c.scope();ListView choices=panelList();choices.setVerticalScrollBarEnabled(true);choices.setContentDescription("本对话提问列表");
        BottomSheetDialog[] panel={null};AlertDialog[] full={null};
        choices.setAdapter(new BaseAdapter(){
            public int getCount(){return questions.size();}public Object getItem(int p){return questions.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){
                QuestionNavigator.Question question=questions.get(p);LinearLayout item=row();item.setPadding(dp(12),dp(12),dp(4),dp(12));item.setBackground(design.ripple(Color.WHITE,16));
                TextView number=text(String.valueOf(p+1),12,ACCENT);number.setGravity(Gravity.CENTER);item.addView(number,new LinearLayout.LayoutParams(dp(28),-2));
                TextView summary=text(question.text(),14,INK);summary.setMaxLines(3);summary.setEllipsize(TextUtils.TruncateAt.END);summary.setLineSpacing(dp(3),1);summary.setPadding(dp(8),0,dp(8),0);item.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
                MaterialButton details=design.button("全文",null,false,v->{
                    if(scope!=c.scope())return;
                    ScrollView scroll=new ScrollView(MainActivity.this);TextView body=text(question.text(),15,INK);body.setTextIsSelectable(true);body.setPadding(dp(20),dp(12),dp(20),dp(12));body.setLineSpacing(dp(4),1);scroll.addView(body);
                    full[0]=new MaterialAlertDialogBuilder(MainActivity.this).setTitle("第 "+(p+1)+" 次提问").setView(scroll).setPositiveButton("关闭",null).show();
                });details.setTextSize(12);details.setContentDescription("查看第 "+(p+1)+" 次提问全文");item.addView(details,new LinearLayout.LayoutParams(-2,dp(48)));
                item.setContentDescription("跳转到第 "+(p+1)+" 次提问");item.setFocusable(true);item.setOnClickListener(v->{panel[0].dismiss();jumpToQuestion(question,scope);});return item;
            }
        });
        LinearLayout content=column();if(questions.isEmpty()){TextView empty=text("当前对话还没有提问",14,MUTED);content.addView(empty);}content.addView(choices,new LinearLayout.LayoutParams(-1,0,1));
        panel[0]=sheet("对话导航","共 "+questions.size()+" 次提问 · 点击条目跳转，点击全文阅读",content);
        panel[0].setOnDismissListener(d->{if(full[0]!=null)full[0].dismiss();if(activePanel==panel[0]){activePanel=null;panelRefresh=null;}});
    }
    private void jumpToQuestion(QuestionNavigator.Question question,long scope){
        if(scope!=c.scope()||list==null||adapter==null)return;
        userScrolling=false;c.pauseFollow();list.stopScroll();adapter.update(c.transcript.rows());
        int position=QuestionNavigator.position(adapter.rows(),question);
        if(position<0){notice("这次提问已不在当前对话中");return;}
        ((LinearLayoutManager)list.getLayoutManager()).scrollToPositionWithOffset(position,0);followChanged();
    }
    private void searchMessages(){List<Transcript.Row> rows=adapter.rows();ArrayList<String> texts=new ArrayList<>();ArrayList<Integer> positions=new ArrayList<>();for(int i=0;i<rows.size();i++){Transcript.Row r=rows.get(i);if(r.kind.equals("text")){texts.add((r.role.equals("user")?"你：":"QwenPaw：")+r.text);positions.add(i);}}showSearchList("搜索当前对话",texts,index->{c.autoFollow=false;list.scrollToPosition(positions.get(index));followChanged();});}
    public void copy(String text){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("QwenPaw",text));notice("已复制");}
    public void diagram(String source){
        LinearLayout box=column();DiagramView diagram=new DiagramView(this,source);box.addView(diagram,new LinearLayout.LayoutParams(-1,dp(380)));TextView code=text(source,13,MUTED);code.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(code);box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(120)));
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("Mermaid 图表").setView(box).setPositiveButton("复制源码",(d,n)->copy(source)).setNegativeButton("关闭",null).create();dialog.setOnDismissListener(d->diagram.destroy());dialog.show();
    }
    public void openLink(String link){
        if(link.startsWith("file://") || link.startsWith("/api/files/") || link.startsWith("/files/") || link.startsWith("/")){saveFile(link,Uri.parse(link).getLastPathSegment());return;}
        Uri uri=Uri.parse(link);if(!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())){notice("不支持此链接类型");return;}
        if(link.startsWith(c.base+"/api/files/")){saveFile(link,uri.getLastPathSegment());return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){notice("未安装可打开链接的应用");}
    }
    private void pickFiles(){if(c.uploads>=4){notice("请等待当前上传完成");return;}pickerScope=c.scope();Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(intent,PICK);}
    public void saveFile(String url,String name){downloadUrl=url;downloadName=safeName(name);downloadScope=c.scope();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,downloadName);startActivityForResult(intent,SAVE);}
    public void previewFile(String url,String name){
        AttachmentPreview.Kind kind=AttachmentPreview.kind(name);if(kind==AttachmentPreview.Kind.UNSUPPORTED)return;
        long scope=c.scope();ApiClient api=c.api();String filename=safeName(name);
        notice("正在准备预览："+filename);
        new Thread(()->{
            File file=null;
            try{
                File dir=new File(getCacheDir(),"previews");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建预览缓存");
                File[] stale=dir.listFiles();if(stale!=null)for(File old:stale)
                    if(old.isFile()&&System.currentTimeMillis()-old.lastModified()>24L*60*60*1000)old.delete();
                file=File.createTempFile("attachment-","."+AttachmentPreview.extension(filename),dir);
                long limit=kind==AttachmentPreview.Kind.TEXT?AttachmentPreview.TEXT_LIMIT:AttachmentPreview.EXTERNAL_LIMIT;
                try(OutputStream out=new FileOutputStream(file)){api.downloadBounded(url,out,limit);}
                File ready=file;
                c.main.post(()->{if(scope!=c.scope()||isFinishing()||isDestroyed()){ready.delete();return;}
                    if(kind==AttachmentPreview.Kind.TEXT)showTextPreview(ready,filename);
                    else if(kind==AttachmentPreview.Kind.IMAGE)showImagePreview(ready,filename);
                    else openExternalPreview(ready,filename);
                });
            }catch(ApiClient.FileTooLargeException e){if(file!=null)file.delete();
                c.main.post(()->{if(scope!=c.scope()||isFinishing()||isDestroyed())return;
                    if(kind==AttachmentPreview.Kind.TEXT)new MaterialAlertDialogBuilder(this).setTitle("文本超过 3 MB")
                        .setMessage("为避免卡顿，无法在应用内预览。是否下载后交给系统应用打开？")
                        .setNegativeButton("取消",null).setPositiveButton("使用系统应用打开",(d,w)->openLargeText(url,filename,scope,api)).show();
                    else notice("文件超过 512 MB，无法直接打开；可尝试保存文件");
                });
            }catch(Exception e){if(file!=null)file.delete();c.main.post(()->{if(scope==c.scope())c.failure(e);});}
        },"attachment-preview").start();
    }
    private void openLargeText(String url,String name,long scope,ApiClient api){
        notice("正在下载："+name);
        new Thread(()->{File file=null;try{
            File dir=new File(getCacheDir(),"previews");file=File.createTempFile("attachment-","."+AttachmentPreview.extension(name),dir);
            try(OutputStream out=new FileOutputStream(file)){api.downloadBounded(url,out,AttachmentPreview.EXTERNAL_LIMIT);}
            File ready=file;c.main.post(()->{if(scope==c.scope()&&!isFinishing()&&!isDestroyed())openExternalPreview(ready,name);else ready.delete();});
        }catch(Exception e){if(file!=null)file.delete();c.main.post(()->{if(scope==c.scope())c.failure(e);});}},"large-text-open").start();
    }
    private void showTextPreview(File file,String name){
        try{
            byte[] bytes=new byte[(int)file.length()];try(InputStream in=new FileInputStream(file)){
                int offset=0,n;while(offset<bytes.length&&(n=in.read(bytes,offset,bytes.length-offset))!=-1)offset+=n;
                if(offset!=bytes.length)throw new IOException("文本读取不完整");
            }
            String body=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);file.delete();
            boolean isMarkdown=AttachmentPreview.markdown(name);
            io.noties.markwon.Markwon previewMarkdown=isMarkdown?ChatMarkdown.create(this):null;
            // Parse the entire Markdown document before paging, preserving fences and references.
            CharSequence display=isMarkdown?previewMarkdown.toMarkdown(body):AttachmentPreview.formatText(name,body);
            final int pageSize=32768;int pages=Math.max(1,(display.length()+pageSize-1)/pageSize);final int[] page={0};
            LinearLayout box=column();box.setPadding(dp(16),0,dp(16),0);
            TextView filename=text(name,13,MUTED);box.addView(filename);
            TextView count=text("",12,MUTED);box.addView(count);
            ScrollView scroll=new ScrollView(this);TextView content=text("",isMarkdown?15:13,INK);if(!isMarkdown)content.setTypeface(Typeface.MONOSPACE);content.setTextIsSelectable(true);content.setLinkTextColor(Ui.GREEN);content.setLineSpacing(dp(4),1);scroll.addView(content);
            box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(400)));
            LinearLayout controls=row();MaterialButton previous=design.button("上一页",null,false,v->{}),next=design.button("下一页",null,false,v->{});controls.addView(previous);controls.addView(next);box.addView(controls);
            controls.setVisibility(pages>1?View.VISIBLE:View.GONE);count.setVisibility(pages>1?View.VISIBLE:View.GONE);
            Runnable render=()->{int start=page[0]*pageSize;CharSequence slice=display.subSequence(start,Math.min(display.length(),start+pageSize));if(isMarkdown)previewMarkdown.setParsedMarkdown(content,new android.text.SpannedString(slice));else content.setText(slice);count.setText("第 "+(page[0]+1)+" / "+pages+" 页");previous.setEnabled(page[0]>0);next.setEnabled(page[0]<pages-1);scroll.scrollTo(0,0);};
            previous.setOnClickListener(v->{page[0]--;render.run();});next.setOnClickListener(v->{page[0]++;render.run();});render.run();
            new MaterialAlertDialogBuilder(this).setTitle("预览").setView(box).setPositiveButton("关闭",null).show();
        }catch(Exception e){file.delete();c.failure(e);}
    }
    private void showImagePreview(File file,String name){
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("无法识别图片");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>2048||bounds.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(bitmap==null)throw new IOException("无法打开图片");
            ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(true);image.setMaxHeight(dp(480));
            final boolean[] external={false};
            new MaterialAlertDialogBuilder(this).setTitle(name).setView(image).setNegativeButton("关闭",null)
                .setPositiveButton("使用系统应用打开",(d,w)->{external[0]=true;openExternalPreview(file,name);})
                .setOnDismissListener(d->{bitmap.recycle();if(!external[0])file.delete();}).show();
        }catch(Exception e){file.delete();c.failure(e);}
    }
    private void openExternalPreview(File file,String name){
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",file);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,AttachmentPreview.mime(name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }catch(ActivityNotFoundException e){file.delete();notice("没有可打开此文件的系统应用，请使用“保存文件”");}
        catch(Exception e){file.delete();c.failure(e);}
    }
    private String safeName(String name){if(name==null || name.isBlank())return "qwenpaw-file";return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");}
    private void exportChat(){StringBuilder out=new StringBuilder("# "+(c.chat==null?"QwenPaw":c.chat.optString("name"))+"\n\n");for(Transcript.Row r:c.transcript.rows()){out.append("## ").append(r.kind.equals("file")?"附件":r.kind.equals("tool")?r.title:r.role.equals("user")?"你":"QwenPaw").append("\n\n").append(r.params).append(r.text).append(r.url.isEmpty()?"":"\n"+r.url).append("\n\n");}exportText=out.toString();saveFile("export:markdown","QwenPaw-对话.md");}
    private String exportText="";
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;
        if(request==PICK){if(pickerScope!=c.scope()){notice("会话已切换，请重新选择文件");return;}ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<Math.min(8,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());for(Uri uri:uris)prepareUpload(uri,pickerScope);}
        if(request==SAVE && data.getData()!=null){if(downloadScope!=c.scope()){notice("连接或会话已切换，请重新保存");return;}Uri destination=data.getData();ApiClient api=c.api();String raw=downloadUrl;String exported=exportText;
            c.task(a->{try(OutputStream out=getContentResolver().openOutputStream(destination)){if(out==null)throw new IOException("无法写入所选位置");if(raw.equals("export:markdown"))out.write(exported.getBytes(java.nio.charset.StandardCharsets.UTF_8));else api.download(raw,out);}return true;},ok->notice("文件已保存"));}
    }
    private void prepareUpload(Uri uri,long scope){
        String name="attachment";try(Cursor cur=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cur!=null && cur.moveToFirst())name=cur.getString(0);}catch(Exception ignored){}
        String filename=safeName(name),mime=getContentResolver().getType(uri);notice("正在准备 "+filename);
        // Count the local copy stage too, so Send cannot race ahead of selected attachments.
        c.uploads++;c.emit();new Thread(()->{File tmp=null;try{tmp=File.createTempFile("upload-",".bin",getCacheDir());long count=0;try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("无法读取文件");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){count+=n;if(count>100L*1024*1024)throw new IOException("单个附件不能超过 100 MiB");out.write(b,0,n);}}
            File ready=tmp;c.main.post(()->{if(scope!=c.scope()){ready.delete();return;}c.uploads=Math.max(0,c.uploads-1);c.upload(ready,filename,mime);});
        }catch(Exception e){if(tmp!=null)tmp.delete();c.main.post(()->{if(scope==c.scope()){c.uploads=Math.max(0,c.uploads-1);c.failure(e);}});}},"attachment-copy").start();
    }
}
