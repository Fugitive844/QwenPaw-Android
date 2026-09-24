package cn.qwenpaw.android;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import org.json.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native administration screens. Every request stays bound to the opening account and agent. */
public final class SettingsActivity extends AppCompatActivity {
    private Ui ui;
    private ChatController chat;
    private ApiClient api;
    private long scope;
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private JSONObject strings=new JSONObject(),config;
    private final Map<String,String> edits=new LinkedHashMap<>();
    private JSONArray envs=new JSONArray(),catalog=new JSONArray(),memory=new JSONArray(),records=new JSONArray(),trend=new JSONArray();
    private JSONObject projects=new JSONObject();
    private String language="",timezone="",envQuery="",error="",trendError="",extrasError="";
    private Boolean coding;
    private long contextLimit;
    private int page,section,revision,trendRevision;
    private boolean loading,busy,envLoaded,usageLoaded;
    private LinearLayout root,body,footer;
    private ScrollView contentScroll;
    private int renderedPage=-1,renderedSection=-1;
    private LocalDate start=LocalDate.now().minusDays(30),end=LocalDate.now();
    private final Set<String> hiddenModels=new HashSet<>(),hiddenTypes=new HashSet<>();
    private final Set<String> expandedPanels=new HashSet<>();

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new Ui(this);chat=((PawApplication)getApplication()).ensureChat();
        if(!chat.loggedIn){finish();return;}api=chat.api();scope=chat.scope();page=getIntent().getIntExtra("page",0);
        try(InputStream in=getAssets().open("settings-zh.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);strings=Json.object(Json.parse(out.toString("UTF-8").replace("\ufeff","")));
        }catch(IOException ignored){}
        if(saved!=null){page=saved.getInt("page",page);section=saved.getInt("section",0);JSONObject draft=Json.object(Json.parse(saved.getString("runtimeEdits","{}")));Iterator<String> keys=draft.keys();while(keys.hasNext()){String key=keys.next();edits.put(key,draft.optString(key));}start=LocalDate.parse(saved.getString("start",start.toString()));end=LocalDate.parse(saved.getString("end",end.toString()));}
        load();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",page);out.putInt("section",section);JSONObject draft=new JSONObject();edits.forEach((k,v)->Json.put(draft,k,v));out.putString("runtimeEdits",draft.toString());out.putString("start",start.toString());out.putString("end",end.toString());}
    @Override protected void onResume(){super.onResume();if(api!=null&&!current())finish();}
    @Override protected void onDestroy(){revision++;trendRevision++;io.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){if(busy){toast("正在保存，请稍候");return;}if(!edits.isEmpty())confirm("放弃未保存的修改？","运行配置的修改尚未保存。",this::finish);else super.onBackPressed();}
    private boolean current(){return !isFinishing()&&!isDestroyed()&&chat.loggedIn&&scope==chat.scope()&&api.base.equals(chat.base)&&api.token.equals(chat.token)&&api.agent.equals(chat.agent);}
    private String t(String path){Object value=RuntimeConfig.get(strings,path);return value instanceof String?(String)value:path;}
    private String ac(String key){return t("agentConfig."+key);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private String failure(Exception e){if(e instanceof ApiClient.ApiException a&&a.status==401){chat.failure(e);finish();}return e.getMessage()==null?"连接失败，请重试":e.getMessage();}
    private interface Work {Object run() throws Exception;}
    private void request(Work work,Consumer<Object> success){
        int id=++revision;io.execute(()->{try{Object result=work.run();runOnUiThread(()->{if(!current()||id!=revision)return;loading=false;busy=false;error="";success.accept(result);render();});}
            catch(Exception e){runOnUiThread(()->{if(!current()||id!=revision)return;loading=false;busy=false;error=failure(e);render();});}});
    }
    private Object get(String path)throws IOException{return api.call("GET",path,null);}
    private JSONObject object(String path)throws IOException{Object value=get(path);if(!(value instanceof JSONObject))throw new IOException("服务器返回了无效的配置数据");return (JSONObject)value;}
    private JSONArray array(String path)throws IOException{Object value=get(path);if(!(value instanceof JSONArray))throw new IOException("服务器未提供此功能或返回了无效列表");return (JSONArray)value;}
    private void load(){
        loading=true;error="";render();
        if(page==0){request(()->object("/workspace/running-config"),r->{config=(JSONObject)r;loadExtras();});}
        else if(page==1){request(()->Json.obj("envs",array("/envs"),"catalog",array("/envs/catalog")),r->{JSONObject o=(JSONObject)r;envs=o.optJSONArray("envs");catalog=o.optJSONArray("catalog");envLoaded=true;});}
        else {loadUsage();}
    }
    private void loadExtras(){
        // Optional capabilities have independent errors; an older server must not block retry/approval settings.
        io.execute(()->{
            Map<String,Object> values=new HashMap<>();List<String> unavailable=new ArrayList<>();
            String[][] paths={{"language","/workspace/language"},{"timezone","/config/user-timezone"},{"memory","/agents/memory/backends"},{"projects","/workspace/project-directory/dirs"},{"coding","/coding-mode"},{"model","/models/active?scope=effective&agent_id="+ApiClient.enc(api.agent)}};
            for(String[] p:paths)try{values.put(p[0],get(p[1]));}catch(Exception e){if(e instanceof ApiClient.ApiException a&&a.status==401){runOnUiThread(()->{if(current())failure(e);});return;}unavailable.add(p[0]);}
            runOnUiThread(()->{if(!current())return;
                language=Json.object(values.get("language")).optString("language","");timezone=Json.object(values.get("timezone")).optString("timezone","");memory=Json.array(values.get("memory"));projects=Json.object(values.get("projects"));
                JSONObject mode=Json.object(values.get("coding"));coding=mode.has("enabled")?mode.optBoolean("enabled"):null;
                contextLimit=Json.object(values.get("model")).optLong("effective_max_input_length",0);
                extrasError=unavailable.isEmpty()?"":"部分附加配置读取失败或服务器版本不支持："+String.join("、",unavailable);
                if(page==0&&!busy&&!loading&&!(getCurrentFocus() instanceof EditText))render();
            });
        });
    }
    private void render(){
        if(!current())return;
        int scrollY=contentScroll!=null&&renderedPage==page&&renderedSection==section?contentScroll.getScrollY():0;
        renderedPage=page;renderedSection=section;
        root=ui.column();root.setBackgroundColor(Ui.BG);root.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(ui.dp(16),i.getSystemWindowInsetTop(),ui.dp(16),Math.max(ui.dp(8),i.getSystemWindowInsetBottom()));return i;});
        LinearLayout head=ui.row();head.addView(ui.iconButton("返回对话","close",v->onBackPressed()));head.addView(ui.heading(new String[]{"运行配置","环境变量","Token 消耗分析"}[page],22),new LinearLayout.LayoutParams(0,-2,1));root.addView(head);
        root.addView(ui.text(page==0?"当前智能体 · "+api.agent:page==1?"当前账户的运行环境":"当前账户 · 所有智能体",12,Ui.MUTED));ui.gap(root,10);
        LinearLayout nav=ui.row();String[] pages={"运行配置","环境变量","Token 分析"};for(int i=0;i<pages.length;i++){final int target=i;var b=ui.button(pages[i],null,page==i,v->{if(!busy){page=target;revision++;loading=false;error="";if(page==0&&config!=null||page==1&&envLoaded||page==2&&usageLoaded)render();else load();}});b.setEnabled(!busy);nav.addView(b,new LinearLayout.LayoutParams(0,ui.dp(48),1));}root.addView(nav);
        ScrollView scroll=new ScrollView(this);contentScroll=scroll;scroll.setFillViewport(true);body=ui.column();body.setPadding(0,ui.dp(8),0,ui.dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.post(()->scroll.scrollTo(0,scrollY));
        footer=ui.column();root.addView(footer);
        if(loading){body.addView(ui.text("正在加载…",15,Ui.MUTED));return;}
        if(!error.isEmpty()){note(body,error,true);body.addView(ui.button("重新加载",null,false,v->{if(busy)return;if(page==0&&!edits.isEmpty())confirm("重新加载配置？","将保留当前未保存的字段修改。",this::load);else load();}));}
        if(page==0&&config!=null)runtime();else if(page==1&&envLoaded)environments();else if(page==2&&usageLoaded)usage();
    }
    private void note(LinearLayout parent,String text,boolean warning){TextView v=ui.text(text,12,warning?Ui.DANGER:Ui.MUTED);v.setLineSpacing(ui.dp(4),1);v.setPadding(0,ui.dp(7),0,ui.dp(9));parent.addView(v);}
    private LinearLayout card(LinearLayout parent,String title){LinearLayout box=ui.column();ui.padding(box,16,12);box.setBackground(ui.shape(Ui.WHITE,18,Ui.LINE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=ui.dp(12);parent.addView(box,lp);if(!title.isEmpty()){box.addView(ui.heading(title,17));ui.gap(box,10);}return box;}
    private EditText edit(LinearLayout parent,String label,String value,boolean numeric){parent.addView(ui.text(label,13,Ui.INK));EditText e=new androidx.appcompat.widget.AppCompatEditText(this);e.setTextSize(15);e.setTextColor(Ui.INK);e.setSingleLine(true);e.setText(value);e.setHint(label);e.setContentDescription(label);e.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));e.setMinHeight(ui.dp(48));e.setBackground(ui.shape(Ui.BG,10,Ui.LINE));if(numeric)e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.dp(7);lp.bottomMargin=ui.dp(8);parent.addView(e,lp);return e;}
    private void watch(EditText e,Consumer<String> changed){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.accept(s.toString());}public void afterTextChanged(Editable e){}});}
    private void confirm(String title,String text,Runnable action){new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(text).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->action.run()).show();}
    private Object value(String path){if(edits.containsKey(path))try{return RuntimeConfig.parse(RuntimeConfig.field(path),edits.get(path));}catch(IllegalArgumentException ignored){}return RuntimeConfig.get(config,path);}
    private void change(String path,String v){String original=RuntimeConfig.display(RuntimeConfig.get(config,path));if(original.equals(v))edits.remove(path);else edits.put(path,v);}

    private void runtime(){
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout row=ui.row();String[] names={"ReAct 智能体","LLM 自动重试","上下文管理","工具审批模式"};for(int i=0;i<names.length;i++){final int selected=i;row.addView(ui.button(names[i],null,section==i,v->{section=selected;render();}));}tabs.addView(row);body.addView(tabs);tabs.post(()->tabs.scrollTo(Math.max(0,row.getChildAt(section).getLeft()-ui.dp(30)),0));
        LinearLayout box=card(body,names[section]);
        if(section==0){reactExtras(box);fields(box,0,4);note(box,ac("memoryManagerBackendRestartWarning"),false);}
        if(section==1){fields(box,4,8);}
        if(section==2){
            fields(box,8,10);String strategy=String.valueOf(RuntimeConfig.get(config,RuntimeConfig.LIGHT+"strategy"));note(box,"上下文策略："+(strategy.equals("native")?"Native":"Scroll"),false);
            LinearLayout compact=collapse(box,ac("contextCompactCollapseLabel"));fields(compact,10,13);
            TextView preview=ui.text("",13,Ui.GREEN);compact.addView(preview);updateThreshold(preview);
            // Keep the live threshold preview synchronized without rebuilding focused input fields.
            for(int i=0;i<compact.getChildCount();i++)if(compact.getChildAt(i) instanceof EditText e)watch(e,s->updateThreshold(preview));
            if(!strategy.equals("native")){fields(compact,13,14);TextView hint=ui.text("",12,Ui.DANGER);compact.addView(hint);Runnable update=()->{double days=RuntimeConfig.number(config,RuntimeConfig.LIGHT+"scroll_config.history_retention_days",30);try{days=Double.parseDouble(edits.getOrDefault(RuntimeConfig.LIGHT+"scroll_config.history_retention_days",String.valueOf(days)));}catch(Exception ignored){}hint.setText(days==0?ac("historyRetentionDaysForeverWarning"):days>30?ac("historyRetentionDaysLargeWarning"):"");};update.run();for(int i=0;i<compact.getChildCount();i++)if(compact.getChildAt(i) instanceof EditText e)watch(e,s->update.run());}
            LinearLayout pruning=collapse(box,ac("toolResultPruningCollapseLabel"));fields(pruning,14,15);if(strategy.equals("native"))fields(pruning,15,17);fields(pruning,17,19);if(strategy.equals("native"))fields(pruning,19,21);
            LinearLayout visual=collapse(box,ac("visualCompactCollapseLabel"));note(visual,ac("visualCompactDescription"),false);fields(visual,21,23);note(visual,ac("visualCompactCapabilityNote")+" "+ac("visualCompactQualityNote"),false);
        }
        if(section==3){note(box,ac("toolExecutionLevel.alertMessage"),false);note(box,"保存为当前智能体的默认模式；聊天中的单独选择继续优先。",false);fields(box,23,24);}
        LinearLayout actions=ui.row();var reset=ui.button("重置",null,false,v->confirm("重置运行配置？","重新读取服务器配置，放弃未保存的修改。",()->{edits.clear();load();}));var save=ui.button(busy?"正在保存…":"保存配置",null,true,v->saveRuntime());reset.setEnabled(!busy);save.setEnabled(!busy);actions.addView(reset,new LinearLayout.LayoutParams(0,ui.dp(50),1));actions.addView(save,new LinearLayout.LayoutParams(0,ui.dp(50),1));footer.addView(actions);if(busy)setEnabled(body,false);
    }
    private void updateThreshold(TextView preview){long trigger=(long)(contextLimit*numericValue(RuntimeConfig.COMPACT+"compact_threshold_ratio",0.8));long reserve=RuntimeConfig.reserve(contextLimit,numericValue(RuntimeConfig.COMPACT+"reserve_threshold_ratio",0.1),String.valueOf(RuntimeConfig.get(config,RuntimeConfig.LIGHT+"strategy")));preview.setText(contextLimit>0?ac("contextCompactThreshold")+"："+trigger+"\n"+ac("contextCompactReserveThreshold")+"："+reserve:"模型上下文上限暂不可用，阈值将在获取后显示");}
    private double numericValue(String path,double fallback){try{return Double.parseDouble(edits.getOrDefault(path,String.valueOf(RuntimeConfig.number(config,path,fallback))));}catch(Exception e){return fallback;}}
    private LinearLayout collapse(LinearLayout parent,String title){String key=page+":"+section+":"+title;LinearLayout inside=ui.column();inside.setVisibility(expandedPanels.contains(key)?View.VISIBLE:View.GONE);var toggle=ui.button(title+" ▾",null,false,v->{boolean open=inside.getVisibility()!=View.VISIBLE;inside.setVisibility(open?View.VISIBLE:View.GONE);if(open)expandedPanels.add(key);else expandedPanels.remove(key);});parent.addView(toggle);parent.addView(inside);return inside;}
    private void fields(LinearLayout parent,int from,int to){for(int i=from;i<to;i++)field(parent,RuntimeConfig.FIELDS.get(i));}
    private void field(LinearLayout parent,RuntimeConfig.Field f){
        Object original=RuntimeConfig.get(config,f.path());String label=ac(f.label());Object val=value(f.path());
        if(original==null){note(parent,label+"：当前服务器版本未提供",false);return;}
        boolean enabled=!(f.path().startsWith("llm_")&&!f.path().equals("llm_retry_enabled")&&Boolean.FALSE.equals(value("llm_retry_enabled")))&&!(f.path().equals(RuntimeConfig.VISUAL+"effort")&&!Boolean.TRUE.equals(value(RuntimeConfig.VISUAL+"enabled")));
        String help=ac(f.label()+"Tooltip");if(help.startsWith("agentConfig."))help="";
        if(f.type().equals("boolean")){SwitchMaterial toggle=new SwitchMaterial(this);toggle.setText(label);toggle.setTextColor(Ui.INK);toggle.setChecked(Boolean.TRUE.equals(val));toggle.setMinHeight(ui.dp(48));parent.addView(toggle);toggle.setOnCheckedChangeListener((b,checked)->{change(f.path(),Boolean.toString(checked));if(f.path().equals("llm_retry_enabled")||f.path().equals(RuntimeConfig.VISUAL+"enabled"))updateDependentFields();});}
        else if(List.of("memory","effort","approval").contains(f.type())){
            List<String> keys=new ArrayList<>(),labels=new ArrayList<>();List<Boolean> available=new ArrayList<>();
            if(f.type().equals("memory")){for(int n=0;n<memory.length();n++){JSONObject m=memory.optJSONObject(n);if(m==null)continue;keys.add(m.optString("id"));labels.add(m.optString("label",m.optString("id"))+(m.optBoolean("available",true)?"":"（不可用）"));available.add(m.optBoolean("available",true));}if(!keys.contains(String.valueOf(val))){keys.add(String.valueOf(val));labels.add(val+"（当前后端）");available.add(false);}}
            if(f.type().equals("effort")){keys.addAll(List.of("low","medium","high"));labels.addAll(List.of(ac("visualCompactLow"),ac("visualCompactMedium"),ac("visualCompactHigh")));}
            if(f.type().equals("approval")){keys.addAll(List.of("STRICT","SMART","AUTO","OFF"));for(String k:keys)labels.add(ac("toolExecutionLevel."+k.toLowerCase(Locale.ROOT))+"\n"+ac("toolExecutionLevel."+k.toLowerCase(Locale.ROOT)+"Desc"));}
            parent.addView(ui.text(label,13,Ui.INK));RadioGroup group=new RadioGroup(this);group.setTag(f.path());int[] colors={0xffa12a25,0xff895d00,0xff23599e,0xff246543};
            for(int n=0;n<keys.size();n++){String key=keys.get(n);RadioButton button=new RadioButton(this);button.setId(View.generateViewId());button.setText(labels.get(n));button.setTextSize(14);button.setMinHeight(ui.dp(48));button.setTextColor(f.type().equals("approval")?colors[n]:Ui.INK);button.setEnabled(enabled&&(available.isEmpty()||available.get(n)));group.addView(button);button.setChecked(key.equals(String.valueOf(val)));button.setOnClickListener(v->{change(f.path(),key);if(f.type().equals("effort"))toast(ac("visualCompact"+key.substring(0,1).toUpperCase(Locale.ROOT)+key.substring(1)+"Description"));});}parent.addView(group);
        }else{
            EditText e=edit(parent,label,edits.getOrDefault(f.path(),RuntimeConfig.display(val)),f.type().equals("number")||f.type().equals("integer"));e.setTag(f.path());e.setEnabled(enabled);watch(e,s->{change(f.path(),s);e.setError(null);});
        }
        if(!help.isEmpty())note(parent,help,false);ui.gap(parent,8);
    }
    private void updateDependentFields(){updateDependentFields(body);}
    private void updateDependentFields(View v){if(v.getTag() instanceof String path){if(path.startsWith("llm_")&&!path.equals("llm_retry_enabled"))setEnabled(v,!Boolean.FALSE.equals(value("llm_retry_enabled")));if(path.equals(RuntimeConfig.VISUAL+"effort"))setEnabled(v,Boolean.TRUE.equals(value(RuntimeConfig.VISUAL+"enabled")));}if(v instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++)updateDependentFields(g.getChildAt(i));}
    private void setEnabled(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof android.view.ViewGroup g)for(int i=0;i<g.getChildCount();i++)setEnabled(g.getChildAt(i),enabled);}
    private void saveRuntime(){
        if(busy)return;try{for(Map.Entry<String,String> e:edits.entrySet())try{RuntimeConfig.parse(RuntimeConfig.field(e.getKey()),e.getValue());}catch(IllegalArgumentException ex){throw new IllegalArgumentException(ac(RuntimeConfig.field(e.getKey()).label())+"："+ex.getMessage());}RuntimeConfig.merge(config,edits);}catch(IllegalArgumentException e){toast(e.getMessage());return;}
        if(edits.isEmpty()){toast("没有需要保存的修改");return;}Map<String,String> pending=new LinkedHashMap<>(edits);busy=true;render();
        request(()->{JSONObject latest=object("/workspace/running-config");return api.saveRunningConfig(RuntimeConfig.merge(latest,pending));},r->{config=(JSONObject)r;edits.clear();chat.refreshCatalog();toast(ac("saveSuccess"));});
    }
    private void reactExtras(LinearLayout box){
        if(!extrasError.isEmpty()){note(box,extrasError,true);box.addView(ui.button("重试附加配置",null,false,v->loadExtras()));}
        box.addView(ui.button(ac("language")+" · "+(language.isEmpty()?"加载中 / 不可用":language),null,false,v->{if(language.isEmpty())return;String[] labels={"中文","English","Bahasa Indonesia","Русский"},keys={"zh","en","id","ru"};new MaterialAlertDialogBuilder(this).setTitle(ac("language")).setItems(labels,(d,i)->{if(!keys[i].equals(language))confirm(ac("languageConfirmTitle"),ac("languageConfirmContent"),()->immediate("PUT","/workspace/language",Json.obj("language",keys[i]),r->{language=r.optString("language",keys[i]);toast(ac("languageSaveSuccess"));}));}).show();}));
        box.addView(ui.button(ac("timezone")+" · "+(timezone.isEmpty()?"加载中 / 不可用":timezone),null,false,v->{if(!timezone.isEmpty())timezonePicker();}));
        LinearLayout project=card(box,ac("projectDirectoryTitle"));JSONArray dirs=Json.array(projects.opt("project_dirs"));StringBuilder paths=new StringBuilder();for(int i=0;i<dirs.length();i++)paths.append(i==0?"主要 · ":"附加 · ").append(Json.object(dirs.opt(i)).optString("path")).append('\n');if(paths.length()==0)paths.append(projects.optString("workspace_dir","加载中 / 不可用"));note(project,paths.toString().trim(),false);var manage=ui.button("管理默认工作区",null,false,v->manageProjects());manage.setEnabled(projects.has("project_dirs"));project.addView(manage);
        if(coding!=null){SwitchMaterial toggle=new SwitchMaterial(this);toggle.setText(ac("enhancedCodeCapability"));toggle.setChecked(coding);box.addView(toggle);note(box,ac("enhancedCodeCapabilityDescription"),false);toggle.setOnCheckedChangeListener((v,on)->immediate("POST","/coding-mode",Json.obj("enabled",on),r->coding=r.optBoolean("enabled")));}
    }
    private void immediate(String method,String path,Object payload,Consumer<JSONObject> done){if(busy)return;busy=true;render();request(()->Json.object(api.call(method,path,payload)),r->{done.accept((JSONObject)r);});}
    private void timezonePicker(){LinearLayout box=ui.column();ui.padding(box,20,12);EditText search=edit(box,"搜索时区", "",false);ListView list=new ListView(this);box.addView(list,new LinearLayout.LayoutParams(-1,ui.dp(320)));List<String> all=new ArrayList<>(java.time.ZoneId.getAvailableZoneIds());all.add("UTC");Collections.sort(all);List<String> shown=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,shown);list.setAdapter(adapter);Consumer<String> filter=q->{shown.clear();for(String s:all)if(s.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))shown.add(s);adapter.notifyDataSetChanged();};filter.accept("");watch(search,filter);AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(ac("timezone")).setView(box).setNegativeButton("取消",null).create();list.setOnItemClickListener((p,v,i,id)->{String zone=shown.get(i);dialog.dismiss();immediate("PUT","/config/user-timezone",Json.obj("timezone",zone),r->{timezone=r.optString("timezone",zone);toast(ac("timezoneSaveSuccess"));});});dialog.show();}

    private void manageProjects(){
        LinearLayout box=ui.column();ui.padding(box,16,8);List<JSONObject> dirs=new ArrayList<>();JSONArray old=Json.array(projects.opt("project_dirs"));for(int i=0;i<old.length();i++)dirs.add(Json.copy(Json.object(old.opt(i))));
        LinearLayout rows=ui.column();ScrollView scroll=new ScrollView(this);scroll.addView(rows);box.addView(scroll,new LinearLayout.LayoutParams(-1,ui.dp(240)));Runnable[] draw=new Runnable[1];draw[0]=()->{rows.removeAllViews();for(int i=0;i<dirs.size();i++){final int index=i;JSONObject dir=dirs.get(i);LinearLayout item=card(rows,i==0?"主要工作区":"附加工作区");note(item,dir.optString("path")+(dir.optBoolean("exists",true)?"":"\n目录不存在"),!dir.optBoolean("exists",true));if(!dir.optString("nested_with").isEmpty()&&!dir.isNull("nested_with"))note(item,"与其他工作区嵌套："+dir.optString("nested_with"),true);EditText label=edit(item,"显示名称（可选）",dir.isNull("label")?"":dir.optString("label"),false);watch(label,s->Json.put(dir,"label",s));LinearLayout actions=ui.row();if(i>0)actions.addView(ui.button("设为主要",null,false,v->{dirs.remove(index);dirs.add(0,dir);draw[0].run();}));actions.addView(ui.button("移除",null,false,v->{dirs.remove(index);draw[0].run();}));item.addView(actions);}};draw[0].run();
        EditText path=edit(box,"服务器目录路径","",false);box.addView(ui.button("添加目录",null,false,v->{String value=path.getText().toString().trim();if(value.isEmpty())return;if(dirs.size()>=10){toast("最多可添加 10 个工作区");return;}for(JSONObject d:dirs)if(value.equals(d.optString("path"))){toast("该目录已添加");return;}dirs.add(Json.obj("path",value));path.setText("");draw[0].run();}));box.addView(ui.button("浏览服务器目录",null,false,v->browseDirectory("~",selected->path.setText(selected))));
        box.addView(ui.button("恢复智能体默认工作区",null,false,v->{dirs.clear();draw[0].run();}));
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("默认工作区").setView(box).setNegativeButton("取消",null).setPositiveButton("应用",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{JSONArray payload=new JSONArray();for(JSONObject dir:dirs)payload.put(Json.obj("path",dir.optString("path"),"label",dir.opt("label")));dialog.dismiss();immediate(payload.length()==0?"DELETE":"PUT","/workspace/project-directory/dirs",payload.length()==0?null:Json.obj("project_dirs",payload),r->{projects=r;toast("默认工作区已更新");});}));dialog.show();
    }
    private void browseDirectory(String path,Consumer<String> selected){
        LinearLayout box=ui.column();ui.padding(box,16,12);TextView heading=ui.text("正在加载…",13,Ui.MUTED);box.addView(heading);CheckBox hidden=new CheckBox(this);hidden.setText("显示隐藏目录");box.addView(hidden);ListView list=new ListView(this);box.addView(list,new LinearLayout.LayoutParams(-1,ui.dp(300)));String[] currentPath={path};JSONObject[] data={new JSONObject()};int[] seq={0};Runnable[] fetch=new Runnable[1];
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("服务器目录").setView(box).setNegativeButton("取消",null).setNeutralButton("新建文件夹",null).setPositiveButton("选择此目录",null).create();
        fetch[0]=()->{int id=++seq[0];String target=currentPath[0];boolean show=hidden.isChecked();heading.setText("正在加载…");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);io.execute(()->{try{JSONObject result=object("/workspace/project-directory/browse-dirs?path="+ApiClient.enc(target)+"&show_hidden="+show);runOnUiThread(()->{if(!current()||!dialog.isShowing()||id!=seq[0])return;data[0]=result;currentPath[0]=result.optString("current",target);heading.setText(currentPath[0]);List<String> labels=new ArrayList<>(),paths=new ArrayList<>();if(!result.isNull("parent")){labels.add(".. 上一级");paths.add(result.optString("parent"));}JSONArray dirs=Json.array(result.opt("dirs"));for(int i=0;i<dirs.length();i++){JSONObject item=Json.object(dirs.opt(i));labels.add(item.optString("name"));paths.add(item.optString("path"));}list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));list.setOnItemClickListener((p,v,n,k)->{currentPath[0]=paths.get(n);fetch[0].run();});dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(result.optBoolean("selectable",true));});}catch(Exception e){runOnUiThread(()->{if(current()&&dialog.isShowing()&&id==seq[0])heading.setText(failure(e));});}});};
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{selected.accept(currentPath[0]);dialog.dismiss();});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{EditText name=new EditText(this);new MaterialAlertDialogBuilder(this).setTitle("新建文件夹").setView(name).setNegativeButton("取消",null).setPositiveButton("创建",(q,w)->{String nameValue=name.getText().toString().trim(),parent=currentPath[0];io.execute(()->{try{api.call("POST","/workspace/project-directory/browse-dirs/create",Json.obj("parent",parent,"name",nameValue));runOnUiThread(()->{if(current()&&dialog.isShowing())fetch[0].run();});}catch(Exception e){runOnUiThread(()->{if(current())toast(failure(e));});}});}).show();});hidden.setOnCheckedChangeListener((b,c)->fetch[0].run());heading.setOnClickListener(v->fetch[0].run());fetch[0].run();});dialog.show();
    }

    private void environments(){
        body.addView(ui.button(t("environments.addVariable"),"plus",true,v->envEditor(null,null)));
        EditText search=edit(body,t("environments.searchPlaceholder"),envQuery,false);LinearLayout results=ui.column();body.addView(results);Runnable populate=()->envRows(results);watch(search,s->{envQuery=s;populate.run();});populate.run();footer.addView(ui.button("刷新环境变量",null,false,v->load()));if(busy)setEnabled(body,false);
    }
    private String description(JSONObject spec){String key=spec.optString("description_key");String text=t(key);return text.equals(key)?t("environments.mutabilityDescription."+spec.optString("mutability","hot_runtime")):text;}
    private void envRows(LinearLayout parent){parent.removeAllViews();Map<String,String> configured=new HashMap<>();Set<String> known=new HashSet<>();for(int i=0;i<envs.length();i++){JSONObject e=Json.object(envs.opt(i));configured.put(e.optString("key"),e.optString("value"));}for(int i=0;i<catalog.length();i++)known.add(Json.object(catalog.opt(i)).optString("key"));String query=envQuery.trim().toLowerCase(Locale.ROOT);
        for(int group=0;group<3;group++){parent.addView(ui.heading(t("environments."+new String[]{"customSettings","liveSettings","readonlySettings"}[group]),17));int count=0;JSONArray rows=group==0?envs:catalog;
            for(int i=0;i<rows.length();i++){JSONObject spec=Json.object(rows.opt(i));String key=spec.optString("key"),desc=group==0?"":description(spec);boolean custom=group==0;if(custom&&known.contains(key)||!custom&&(group==1)!=spec.optBoolean("editable"))continue;if(!(key+" "+desc).toLowerCase(Locale.ROOT).contains(query))continue;count++;String val=configured.getOrDefault(key,spec.optString("effective_value"));LinearLayout card=card(parent,key);TextView value=ui.text(custom?"••••••••":val.isEmpty()?"—":val,14,Ui.INK);value.setTextIsSelectable(true);card.addView(value);if(custom){var eye=ui.button(t("environments.showValue"),"eye",false,v->{boolean reveal=!Boolean.TRUE.equals(value.getTag());value.setTag(reveal);value.setText(reveal?val:"••••••••");((TextView)v).setText(t("environments."+(reveal?"hideValue":"showValue")));});card.addView(eye);}
                note(card,t("environments.source."+(custom?"user":spec.optString("source","default"))),false);if(!custom){note(card,desc,false);note(card,"默认值："+spec.optString("default"),false);}
                if(custom||spec.optBoolean("editable")){LinearLayout actions=ui.row();actions.addView(ui.button("编辑",null,false,v->envEditor(key,val)));if(custom||spec.optBoolean("configured")){boolean reset=!custom;actions.addView(ui.button(reset?"重置":"删除",null,false,v->confirm(reset?t("environments.resetVariable"):t("environments.deleteVariable"),t("environments.deleteConfirm").replace("{{name}}",key),()->envMutation(reset?"POST":"DELETE","/envs/"+ApiClient.enc(key)+(reset?"/reset":""),null))));}card.addView(actions);}else note(card,t("environments.readonlyReason."+spec.optString("readonly_reason_code","startup")),false);
            }if(count==0)note(parent,t("environments."+new String[]{"noCustomVariables","noLiveVariables","noReadonlyVariables"}[group]),false);
        }
    }
    private void envEditor(String existing,String old){LinearLayout box=ui.column();ui.padding(box,20,12);EditText key=edit(box,t("environments.key"),existing==null?"":existing,false);key.setEnabled(existing==null);EditText val=edit(box,t("environments.value"),old==null?"":old,false);val.setSingleLine(false);val.setMaxLines(5);val.setTransformationMethod(PasswordTransformationMethod.getInstance());CheckBox reveal=new CheckBox(this);reveal.setText(t("environments.showValue"));box.addView(reveal);reveal.setOnCheckedChangeListener((b,on)->val.setTransformationMethod(on?null:PasswordTransformationMethod.getInstance()));note(box,t("environments.applyHint"),false);
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(t("environments."+(existing==null?"addVariable":"editVariable"))).setView(box).setNegativeButton("取消",null).setPositiveButton(t("environments.applyNow"),null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=key.getText().toString().trim();try{RuntimeConfig.validateEnvKey(name,existing==null,envs,catalog);}catch(IllegalArgumentException e){key.setError(e.getMessage());return;}JSONObject payload=Json.obj(name,val.getText().toString());envMutation("PATCH","/envs",payload,dialog);}));dialog.show();}
    private void envMutation(String method,String path,Object payload){envMutation(method,path,payload,null);}
    private void envMutation(String method,String path,Object payload,AlertDialog editor){
        if(busy)return;busy=true;if(editor!=null){editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);editor.setCancelable(false);}else render();
        // Keep an unsuccessful edit available for correction; values are never logged or persisted locally.
        int id=++revision;io.execute(()->{try{api.call(method,path,payload);runOnUiThread(()->{if(!current()||id!=revision)return;busy=false;if(editor!=null)editor.dismiss();toast("环境变量已更新");load();});}catch(Exception e){runOnUiThread(()->{if(!current()||id!=revision)return;busy=false;String message=failure(e);if(editor!=null&&editor.isShowing()){editor.setCancelable(true);editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);toast(message);}else{error=message;render();}});}});
    }

    private void loadUsage(){
        final String query;try{query=TokenAnalysis.query(start,end);}catch(IllegalArgumentException e){loading=false;error=e.getMessage();render();return;}
        usageLoaded=false;records=new JSONArray();trend=new JSONArray();trendError="正在加载调用趋势…";
        request(()->array("/token-usage/details"+query),r->{records=(JSONArray)r;usageLoaded=true;});loadTrend(query);
    }
    private void loadTrend(String query){int id=++trendRevision;io.execute(()->{try{JSONArray data=array("/agent-stats/llm-tool-trend"+query);runOnUiThread(()->{if(!current()||id!=trendRevision)return;trend=data;trendError="";if(page==2&&!loading)render();});}catch(Exception e){runOnUiThread(()->{if(!current()||id!=trendRevision)return;trendError=failure(e);if(page==2&&!loading)render();});}});}
    private void usage(){
        LinearLayout dates=ui.row();dates.addView(ui.button(start.toString(),"clock",false,v->pickDate(true)),new LinearLayout.LayoutParams(0,ui.dp(50),1));dates.addView(ui.text("至",13,Ui.MUTED));dates.addView(ui.button(end.toString(),"clock",false,v->pickDate(false)),new LinearLayout.LayoutParams(0,ui.dp(50),1));body.addView(dates);LinearLayout presets=ui.row();for(int days:new int[]{7,30,90})presets.addView(ui.button("近 "+days+" 天",null,false,v->{end=LocalDate.now();start=end.minusDays(days-1);load();}));body.addView(presets);
        TokenAnalysis a=new TokenAnalysis(records);LinearLayout summary=card(body,"用量总览");stats(summary,a.total);note(summary,"总 Token = 输入 + 输出；缓存命中是输入的一部分。缓存命中率按可观测输入加权，未提供统计时显示 —。",false);
        if(records.length()==0)note(body,t("tokenUsage.noData"),false);
        chartTokenTypes(a);chartModels(a);chartCalls();
        table("按模型",a.models,false);table("按日期",a.dates,false);table("按智能体",a.agents,true);
        footer.addView(ui.button("刷新 Token 数据",null,false,v->load()));
    }
    private void pickDate(boolean first){LocalDate initial=first?start:end;DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{LocalDate date=LocalDate.of(y,m+1,d),from=first?date:start,to=first?end:date;try{TokenAnalysis.query(from,to);start=from;end=to;load();}catch(IllegalArgumentException e){toast(e.getMessage());}},initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();}
    private void stats(LinearLayout parent,TokenAnalysis.Stats s){String[][] items={{"总 Token",format(s.total())},{"输入 Token",format(s.prompt)},{"输出 Token",format(s.completion)},{"缓存命中 Token",format(s.read)},{"缓存写入 Token",format(s.write)},{"缓存命中率",s.hit()},{"LLM 调用",format(s.calls)}};for(String[] item:items){LinearLayout row=ui.row();row.setBaselineAligned(false);row.addView(ui.text(item[0],13,Ui.MUTED),new LinearLayout.LayoutParams(0,ui.dp(30),1));row.addView(ui.text(item[1],15,Ui.INK));parent.addView(row);}}
    private String format(long value){return String.format(Locale.CHINA,"%,d",value);}
    private void table(String title,Map<String,TokenAnalysis.Stats> rows,boolean agents){if(rows.isEmpty())return;LinearLayout section=collapse(body,title);List<Map.Entry<String,TokenAnalysis.Stats>> sorted=new ArrayList<>(rows.entrySet());if(title.equals("按日期"))sorted.sort(Map.Entry.<String,TokenAnalysis.Stats>comparingByKey().reversed());else sorted.sort((x,y)->Long.compare(y.getValue().total(),x.getValue().total()));int[] limit={20};LinearLayout content=ui.column();section.addView(content);Runnable[] draw=new Runnable[1];draw[0]=()->{content.removeAllViews();for(int i=0;i<Math.min(limit[0],sorted.size());i++){var entry=sorted.get(i);String name=entry.getKey();if(agents){if(name.isEmpty())name="未归属";else for(int n=0;n<chat.agents.length();n++){JSONObject agent=Json.object(chat.agents.opt(n));if(name.equals(agent.optString("id"))){name=agent.optString("name",name);break;}}}stats(card(content,name),entry.getValue());}if(limit[0]<sorted.size())content.addView(ui.button("加载更多（"+limit[0]+" / "+sorted.size()+"）",null,false,v->{limit[0]+=20;draw[0].run();}));};draw[0].run();}
    private void chartTokenTypes(TokenAnalysis a){LinearLayout box=card(body,t("tokenUsage.tokenTypeChart"));String[] names={"输入 Token","输出 Token","缓存命中 Token"};Map<String,Map<String,Long>> data=new LinkedHashMap<>();for(int n=0;n<names.length;n++){final int type=n;Map<String,Long> values=new TreeMap<>();a.dates.forEach((date,s)->values.put(date,type==0?s.prompt:type==1?s.completion:s.read));data.put(names[n],values);}chart(box,data,hiddenTypes);}
    private void chartModels(TokenAnalysis a){LinearLayout box=card(body,t("tokenUsage.modelTrend"));Map<String,Map<String,Long>> data=new LinkedHashMap<>();for(String model:a.models.keySet()){Map<String,Long> values=new TreeMap<>();a.modelDates.forEach((date,models)->{TokenAnalysis.Stats s=models.get(model);values.put(date,s==null?0:s.total());});data.put(model,values);}chart(box,data,hiddenModels);}
    private void chartCalls(){LinearLayout box=card(body,t("tokenUsage.llmAndToolTrend"));note(box,t("tokenUsage.llmAndToolTrendTooltip"),false);if(!trendError.isEmpty()){note(box,trendError,true);box.addView(ui.button("重试调用趋势",null,false,v->loadTrend(TokenAnalysis.query(start,end))));return;}Map<String,Long> llm=new TreeMap<>(),tools=new TreeMap<>();for(int i=0;i<trend.length();i++){JSONObject r=Json.object(trend.opt(i));llm.put(r.optString("date"),r.optLong("agent_llm_calls"));tools.put(r.optString("date"),r.optLong("tool_calls"));}Map<String,Map<String,Long>> data=new LinkedHashMap<>();data.put("已记录助手轮次",llm);data.put("工具调用",tools);chart(box,data,new HashSet<>());}
    private void chart(LinearLayout box,Map<String,Map<String,Long>> series,Set<String> hidden){if(series.isEmpty()){note(box,"暂无趋势数据",false);return;}UsageChart chart=new UsageChart(this,start,end,series,hidden);chart.setTag("chart:"+series.keySet().iterator().next());box.addView(chart,new LinearLayout.LayoutParams(-1,ui.dp(210)));TextView selected=ui.text("点击图表查看当天数值",12,Ui.MUTED);box.addView(selected);chart.selection=selected::setText;LinearLayout options=collapse(box,"选择显示项（"+series.size()+"）");options.addView(ui.button("全选",null,false,v->{hidden.clear();for(int i=0;i<options.getChildCount();i++)if(options.getChildAt(i) instanceof CheckBox c)c.setChecked(true);chart.invalidate();}));int index=0;for(String name:series.keySet()){CheckBox check=new CheckBox(this);check.setText(name);check.setTextColor(UsageChart.COLORS[index++%UsageChart.COLORS.length]);check.setChecked(!hidden.contains(name));options.addView(check);check.setOnCheckedChangeListener((b,on)->{if(on)hidden.remove(name);else hidden.add(name);chart.invalidate();});}}
}


