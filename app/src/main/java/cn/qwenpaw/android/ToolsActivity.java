package cn.qwenpaw.android;

import android.os.Bundle;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Agent-scoped built-in/plugin tool controls driven by server configuration schema. */
public final class ToolsActivity extends ManagementActivity {
    private JSONArray tools=new JSONArray();
    private LinearLayout list;
    private String query="";
    private boolean editing;
    private final Map<String,View> fields=new LinkedHashMap<>();
    private final Map<String,JSONObject> schemas=new LinkedHashMap<>();
    private JSONObject editingTool,initial;
    private String provider="";
    @Override public void onCreate(Bundle state){super.onCreate(state);if(account==null)return;screen("工具",true,()->leave(()->load()));showList();load();}
    @Override protected void onAgentChanged(){editing=false;query="";showList();load();}
    @Override protected void working(boolean value){super.working(value);if(agentSelector!=null)agentSelector.setEnabled(!value&&!editing);}
    private String path(String name){return "/tools/"+ApiClient.enc(name);}
    private void load(){task("正在读取工具…",api->array(api.call("GET","/tools",null)),data->{tools=data;showList();});}
    private void showList(){
        editing=false;fields.clear();schemas.clear();initial=null;if(agentSelector!=null)agentSelector.setEnabled(true);body.removeAllViews();LinearLayout intro=card(body);intro.addView(ui.heading("选择智能体可使用的工具",20));ui.gap(intro,8);text(intro,"启用工具、配置服务以及调整执行方式。配置会保存到所选智能体。");
        EditText search=field(body,"搜索工具名称或描述",query,false);LinearLayout actions=ui.row();actions.addView(ui.button("全部启用",null,false,v->batch(true)),new LinearLayout.LayoutParams(0,-2,1));actions.addView(ui.button("全部禁用",null,false,v->confirm("全部禁用？","将禁用当前智能体的全部工具。",()->batch(false))),new LinearLayout.LayoutParams(0,-2,1));body.addView(actions);list=ui.column();body.addView(list);renderList();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();renderList();}public void afterTextChanged(Editable e){}});
    }
    private void renderList(){if(editing||list==null)return;list.removeAllViews();String q=query.trim().toLowerCase(Locale.ROOT);int count=0;
        for(boolean active:new boolean[]{true,false}){LinearLayout group=ui.column();int section=0;for(int i=0;i<tools.length();i++){JSONObject tool=Json.object(tools.opt(i));if(tool.optBoolean("enabled")!=active||!(tool.optString("name")+" "+tool.optString("description")).toLowerCase(Locale.ROOT).contains(q))continue;section++;count++;String name=tool.optString("name");LinearLayout c=card(group);c.addView(ui.heading(tool.optString("icon","")+" "+name,17));ui.gap(c,8);JSONObject cfg=Json.object(tool.opt("config_values"));
            if(name.equals("browser")){boolean effective=cfg.has("experimental_effective")?cfg.optBoolean("experimental_effective",true):cfg.optBoolean("experimental",true);text(c,effective?"使用新版统一浏览器能力。":"使用原有浏览器实现，适用于需要旧行为的场景。");if(cfg.has("experimental_effective")&&effective!=cfg.optBoolean("experimental",true))text(c,"重启服务后将切换为："+(cfg.optBoolean("experimental",true)?"统一浏览器（Beta）":"旧版兼容模式"));}
            else text(c,tool.optString("description"));
            if(tool.optBoolean("requires_config"))text(c,ToolLogic.configured(tool)?"已配置":"需要配置");
            action(c,active?"禁用":"启用",()->{if(!active&&!ToolLogic.configured(tool))configure(tool,"");else toggle(tool);});
            if(active&&(name.equals("execute_shell_command")||name.equals("delegate_external_agent")))action(c,tool.optBoolean("async_execution")?"异步执行已启用":"异步执行已禁用",()->mutation("PATCH",path(name)+"/async-execution",Json.obj("async_execution",!tool.optBoolean("async_execution"))));
            if(active&&name.equals("browser"))action(c,cfg.optBoolean("experimental",true)?"新版(Beta)":"旧版(兼容)",()->confirm("切换浏览器模式","此设置在服务重启后生效。",()->mutation("POST",path(name)+"/config",Json.obj("config",Json.obj("experimental",!cfg.optBoolean("experimental",true))))));
            if(tool.optBoolean("requires_config")||name.equals("web_search")||Json.array(tool.opt("config_fields")).length()>0)action(c,"配置",()->configure(tool,""));
        }if(section>0){list.addView(ui.heading((active?"已启用":"可用工具")+" · "+section,17));ui.gap(list,10);list.addView(group);}}if(count==0)text(list,tools.length()==0?"暂无工具":"没有匹配的工具");
    }
    private void toggle(JSONObject tool){mutation("PATCH",path(tool.optString("name"))+"/toggle",null);}
    private void mutation(String method,String endpoint,JSONObject payload){task("正在保存工具设置…",api->api.call(method,endpoint,payload),r->{toast("工具设置已保存");load();},e->{showError(e);load();});}
    private void batch(boolean enabled){if(busy)return;task(enabled?"正在启用工具…":"正在禁用工具…",api->{JSONArray latest=array(api.call("GET","/tools",null));StringBuilder report=new StringBuilder();int changed=0;for(int i=0;i<latest.length();i++){JSONObject tool=Json.object(latest.opt(i));if(tool.optBoolean("enabled")==enabled)continue;String name=tool.optString("name");try{api.call("PATCH",path(name)+"/toggle",null);changed++;}catch(ApiClient.ApiException e){if(e.status==401)throw e;report.append(name).append("：").append(e.getMessage()).append('\n');}catch(Exception e){report.append(name).append("：结果未确认，请刷新后检查\n");}}return "已处理 "+changed+" 项"+(report.length()==0?"":"\n\n未完成的工具：\n"+report);},report->{notice("批量操作结果",report);load();});}
    private void configure(JSONObject tool,String selectedProvider){String endpoint=path(tool.optString("name"))+"/config"+(selectedProvider.isEmpty()?"":"?provider="+ApiClient.enc(selectedProvider));task("正在读取工具配置…",api->object(api.call("GET",endpoint,null)),cfg->editor(tool,cfg));}
    private void editor(JSONObject tool,JSONObject cfg){
        editing=true;editingTool=tool;initial=Json.copy(cfg);fields.clear();schemas.clear();body.removeAllViews();agentSelector.setEnabled(false);scroll.scrollTo(0,0);body.addView(ui.heading("配置 · "+tool.optString("name"),20));ui.gap(body,12);
        if(tool.optString("name").equals("web_search")){
            provider=cfg.optString("provider","tavily");ArrayList<String> providers=new ArrayList<>(List.of("tavily","anysearch"));if(!providers.contains(provider))providers.add(provider);Spinner choice=select(body,"搜索服务商",providers,providers.indexOf(provider));fields.put("provider",choice);
            if(!provider.equals("tavily")){EditText key=field(body,"API 密钥（可选）",cfg.optString("api_key",""),false);secret(key);fields.put("api_key",key);text(body,"未注册每日限 100 次，前往 anysearch.com 注册可享每日 1000 次免费额度。");}
            choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String selected=providers.get(pos);if(selected.equals(provider))return;choice.setSelection(providers.indexOf(provider));Runnable next=()->configure(tool,selected);if(dirty())confirm("切换搜索服务商","当前未保存的密钥修改将丢弃，加载所选服务商独立保存的密钥。",next);else next.run();}});
        }else{
            JSONArray definitions=Json.array(tool.opt("config_fields"));if(definitions.length()==0)text(body,"此工具没有可编辑的配置项。");
            for(int i=0;i<definitions.length();i++){JSONObject f=Json.object(definitions.opt(i));String key=f.optString("name"),type=f.optString("type"),label=f.optString("label",key)+(f.optBoolean("required")?" *":"");Object value=cfg.has(key)?cfg.opt(key):f.opt("default");View control;
                if(type.equals("boolean"))control=check(body,label,Boolean.TRUE.equals(value));
                else if(type.equals("select")){JSONArray raw=Json.array(f.opt("options"));ArrayList<String> options=new ArrayList<>();options.add("");for(int j=0;j<raw.length();j++)options.add(raw.optString(j));String existing=Json.text(value);if(!existing.isEmpty()&&!options.contains(existing))options.add(existing);control=select(body,label,options,options.indexOf(existing));}
                else{EditText input=field(body,label,Json.text(value),type.equals("textarea"));input.setHint(f.optString("placeholder",label));if(type.equals("password"))secret(input);if(type.equals("number"))input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);control=input;}
                fields.put(key,control);schemas.put(key,f);if(!f.isNull("help")&&!f.optString("help").isBlank())text(body,f.optString("help"));
            }
        }
        action(body,"保存配置",this::saveConfig);action(body,"返回工具列表",()->leave(this::load));
    }
    private void secret(EditText field){field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);field.setTransformationMethod(PasswordTransformationMethod.getInstance());field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);field.setSaveEnabled(false);}
    private JSONObject payload(boolean validate){
        if(editingTool.optString("name").equals("web_search")){EditText key=(EditText)fields.get("api_key");return ToolLogic.webSearch(provider,key==null?"":key.getText().toString());}
        JSONObject result=Json.copy(initial);for(Map.Entry<String,View> e:fields.entrySet()){View v=e.getValue();Object value;if(v instanceof CheckBox b)value=b.isChecked();else{String raw=v instanceof Spinner s?String.valueOf(s.getSelectedItem()):((EditText)v).getText().toString();value=validate?ToolLogic.value(schemas.get(e.getKey()),raw):raw;}Json.put(result,e.getKey(),value);}return result;
    }
    private boolean dirty(){if(!editing||initial==null)return false;JSONObject now;try{now=payload(true);}catch(IllegalArgumentException e){return true;}Iterator<String> keys=now.keys();while(keys.hasNext()){String k=keys.next();Object a=now.opt(k),b=initial.opt(k);if(a instanceof Number an&&b instanceof Number bn&&an.doubleValue()==bn.doubleValue())continue;if(!Json.text(a).equals(Json.text(b)))return true;}return false;}
    private void saveConfig(){JSONObject values;try{values=payload(true);}catch(IllegalArgumentException e){notice("请检查配置",e.getMessage());return;}String key=editingTool.optString("name");task("正在保存工具配置…",api->api.call("POST",path(key)+"/config",Json.obj("config",values)),r->{toast("配置已保存");editing=false;load();},e->{
        // Validation errors may echo secrets: keep the draft but never show raw config responses.
        status.setText("保存失败，草稿已保留");notice("配置未保存",e instanceof ApiClient.ApiException a?"服务器未接受配置（"+a.status+"），请核对字段后重试。":"网络中断，结果未确认。草稿已保留，请重新读取确认后再提交。");
    });}
    private void leave(Runnable next){if(busy)return;if(dirty())confirm("放弃未保存的配置？","工具配置尚未保存。",next);else next.run();}
    @Override public void onBackPressed(){if(busy){super.onBackPressed();return;}if(editing)leave(this::load);else super.onBackPressed();}
}
