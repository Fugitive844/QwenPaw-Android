package cn.qwenpaw.android;

import android.content.*;
import android.os.*;
import okhttp3.Call;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** All visible state is confined to the main thread. Background work captures an immutable scope. */
public final class ChatController {
    public interface Listener { void changed(); void notice(String message); }
    public interface Work<T> { T run(ApiClient api) throws Exception; }
    public final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private final Context context;
    private final android.content.SharedPreferences prefs;
    private final TokenVault vault;
    private Listener listener;
    private Call stream;
    private final ChatTurn turn=new ChatTurn();
    private Runnable reconnectTask, stopCheckTask;
    private long settleRevision, stopDrainUntil;
    private int stopChecks;
    private long generation=0;
    private int thinkingRevision;
    private int chatsRevision;
    private boolean chatsLoading=false;
    private ChatListRequest activeChatList;
    private static final class ChatListRequest {
        final boolean archived;
        final List<Runnable[]> callbacks=new ArrayList<>();
        ChatListRequest(boolean archived){this.archived=archived;}
        void add(Runnable completed,Runnable failed){if(completed!=null||failed!=null)callbacks.add(new Runnable[]{completed,failed});}
        void finish(boolean success){for(Runnable[] pair:callbacks){Runnable callback=pair[success?0:1];if(callback!=null)callback.run();}callbacks.clear();}
    }
    public boolean thinkingLoading=false;
    private boolean approvalBusy=false, foreground=false;
    private int reconnectAttempts;
    private String pendingReceipt="";
    private boolean compacting=false;
    public long activeContextLimit=0;
    public int commandsLoading=0;
    public String commandsError="";
    public String base,token,agent="default",username="",status="未连接",draft="",approvalLevel="AUTO";
    public JSONObject chat,thinking=new JSONObject(),selectedLoop=new JSONObject();
    public JSONArray agents=new JSONArray(),chats=new JSONArray(),loops=new JSONArray(),skills=new JSONArray(),providers=new JSONArray(),approvals=new JSONArray();
    public final ArrayList<JSONObject> attachments=new ArrayList<>();
    public final Transcript transcript=new Transcript();
    public boolean loggedIn=false,busy=false,running=false,autoFollow=true,archived=false,loopActive=false,sessionModels=true;
    public int uploads=0;
    public final Map<String,Boolean> expanded=new HashMap<>();
    private boolean renderQueued=false;
    final List<WelcomePrompts.Prompt> promptCatalog=new ArrayList<>();
    List<WelcomePrompts.Prompt> welcomePrompts=new ArrayList<>();
    private final Random promptRandom=new Random();
    public ChatController(Context context) {
        this.context=context; prefs=context.getSharedPreferences("connection",0); vault=new TokenVault(context);
        String savedBase=prefs.getString("base","");
        base=savedBase.isEmpty()?BuildConfig.DEFAULT_SERVER:savedBase; token=vault.read(); username=prefs.getString("username","");
        loggedIn=!savedBase.isEmpty() && (!token.isEmpty() || prefs.getBoolean("anonymous",false));
        agent=prefs.getString("agent","default");
        try(InputStream in=context.getAssets().open("welcome-prompts.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            promptCatalog.addAll(WelcomePrompts.parse(out.toString("UTF-8")));
        }catch(IOException ignored){}
        refreshWelcomePrompts();
    }
    void refreshWelcomePrompts(){welcomePrompts=WelcomePrompts.choose(promptCatalog,welcomePrompts,promptRandom);}
    void usePrompt(WelcomePrompts.Prompt prompt){draft+=(draft.isEmpty()?"":"\n")+prompt.text();saveDraft();emit();}
    public ApiClient api() {return new ApiClient(base,token,agent);}
    public long scope() {return generation;}
    public void attach(Listener l) {listener=l;foreground=true;emit(); main.removeCallbacks(poll);main.post(poll);}
    public void detach(Listener l) {if(listener==l) {listener=null;foreground=false;main.removeCallbacks(poll);}}
    public void emit() {if(listener!=null)listener.changed();}
    public void notice(String text) {if(listener!=null)listener.notice(text);else status=text;}
    private void throttledEmit() {if(!renderQueued){renderQueued=true;main.postDelayed(()->{renderQueued=false;emit();},100);}}
    public <T> void task(Work<T> work,Consumer<T> success) {task(work,success,this::failure);}
    public <T> void task(Work<T> work,Consumer<T> success,Consumer<Exception> failed) {
        long target=generation;ApiClient client=api();
        io.execute(()->{try {T result=work.run(client);main.post(()->{if(target==generation)success.accept(result);});}
            catch(Exception e){main.post(()->{if(target==generation)failed.accept(e);});}});
    }
    public void failure(Exception e) {
        if(e instanceof ApiClient.ApiException a && a.status==401) {logout();notice(a.getMessage());return;}
        status=e.getMessage()==null?"连接失败":e.getMessage(); notice(status);emit();
    }
    public void login(String address,String user,String password,String manualToken) {
        invalidate(); loggedIn=false;base=address;token="";busy=true;status="正在连接…";emit();
        task(a->{
            JSONObject s=Json.object(a.call("GET","/auth/status",null));
            if(!manualToken.isEmpty()) { new ApiClient(address,manualToken,"default").call("GET","/auth/verify",null);return Json.obj("token",manualToken,"username",user); }
            if(!s.optBoolean("enabled",true)) return Json.obj("token","","username","default","anonymous",true);
            return Json.object(a.call("POST","/auth/login",Json.obj("username",user,"password",password)));
        },r->{
            token=r.optString("token","");username=r.optString("username",user);
            try {if(!token.isEmpty())vault.save(token);else vault.clear();}
            catch(Exception e){busy=false;failure(new IOException("无法安全保存登录状态",e));return;}
            prefs.edit().putString("base",base).putString("username",username).putBoolean("anonymous",r.optBoolean("anonymous",false)).remove("lastChat").apply();
            loggedIn=true;busy=false;agent="default";chat=null;start();
        },e->{busy=false;failure(e);});
    }
    public void start() {
        if(!loggedIn)return;status="正在加载工作区…";emit();
        task(a->Json.object(a.call("GET","/agents",null)),r->{
            agents=Json.array(r.opt("agents"));String chosen="";
            for(int i=0;i<agents.length();i++) {JSONObject x=agents.optJSONObject(i); if(x==null || !x.optBoolean("enabled",true) || !x.optBoolean("available_in_chat",true))continue;
                if(chosen.isEmpty() || x.optString("id").equals(agent))chosen=x.optString("id"); }
            if(chosen.isEmpty()){status="没有可用于聊天的智能体";emit();return;}
            selectAgent(chosen,true);
        });
    }
    private void invalidate() {generation++;turn.reset();cancelRecovery();settleRevision++;chatsRevision++;chatsLoading=false;activeChatList=null;thinkingRevision++;thinkingLoading=false;thinking=new JSONObject();if(stream!=null)stream.cancel();stream=null;running=false;busy=false;approvalBusy=false;uploads=0;reconnectAttempts=0;pendingReceipt="";compacting=false;activeContextLimit=0;commandsLoading=0;commandsError="";}
    public void logout() {
        invalidate();vault.clear();prefs.edit().remove("anonymous").remove("lastChat").apply();token="";loggedIn=false;
        chat=null;draft="";attachments.clear();approvals=new JSONArray();transcript.clear();agents=new JSONArray();chats=new JSONArray();status="未连接";emit();
    }
    public void selectAgent(String id,boolean restore) {
        saveDraft();invalidate();agent=id;prefs.edit().putString("agent",id).apply();chat=null;draft="";attachments.clear();transcript.clear();approvals=new JSONArray();expanded.clear();
        thinking=new JSONObject();selectedLoop=new JSONObject();loopActive=false;autoFollow=true;skills=new JSONArray();loops=new JSONArray();providers=new JSONArray();
        refreshCatalog();refreshChats(restore);emit();
    }
    public void refreshChats(boolean restore) {
        refreshChats(restore,false,null,null);
    }
    public void refreshChats(Runnable completed) {refreshChats(completed,null);}
    public void refreshChats(Runnable completed,Runnable failed) {
        // Opening history can share an existing request for the same agent/filter.
        // Mutation-triggered refreshes still start a fresh request to get new data.
        if(chatsLoading && activeChatList!=null && activeChatList.archived==archived){activeChatList.add(completed,failed);return;}
        refreshChats(false,false,completed,failed);
    }
    private void refreshChats(boolean restore,boolean quiet,Runnable completed,Runnable failed) {
        if(quiet && chatsLoading)return;
        int revision=++chatsRevision;boolean requestedArchived=archived;chatsLoading=true;
        ChatListRequest request=new ChatListRequest(requestedArchived);request.add(completed,failed);activeChatList=request;
        String path="/chats?channel=console&archived="+requestedArchived;
        task(a->{Object result=quiet?a.optionalStatus(path):a.call("GET",path,null);
            if(!(result instanceof JSONArray))throw new IOException("服务器返回了无效的对话列表");return (JSONArray)result;
        },r->{
            if(revision!=chatsRevision){request.finish(false);return;}chatsLoading=false;activeChatList=null;
            if(requestedArchived!=archived){request.finish(false);return;}
            boolean changed=!chats.toString().equals(r.toString());if(changed)chats=r;
            changed=ChatHistory.syncName(chat,r)||changed;
            // Metadata refresh must not replace stream/error status or reset the active conversation.
            if(restore && !running && status.equals("正在加载工作区…"))status="已连接";
            if(changed || !quiet)emit();
            if(restore) {String last=prefs.getString("lastChat","");for(int i=0;i<r.length();i++){JSONObject item=r.optJSONObject(i);if(item!=null && item.optString("id").equals(last)){open(item);break;}}}
            request.finish(true);
        },e->{
            if(revision!=chatsRevision){request.finish(false);return;}chatsLoading=false;activeChatList=null;
            if(!quiet || !request.callbacks.isEmpty() || e instanceof ApiClient.ApiException a && a.status==401)failure(e);
            request.finish(false);
        });
    }
    public void refreshCatalog() {
        task(a->Json.object(a.call("GET","/workspace/running-config",null)),r->{approvalLevel=setting("level",r.optString("approval_level","AUTO"));emit();});
        refreshCommands();
        task(a->Json.array(a.call("GET","/models",null)),r->{providers=r;emit();});
        refreshThinking();
    }
    public void refreshCommands() {
        if(commandsLoading>0)return;commandsLoading=2;commandsError="";emit();
        task(a->Json.array(a.call("GET","/loops",null)),r->{loops=r;commandsLoading--;emit();},e->commandLoadFailed("Loop / 插件",e));
        task(a->Json.array(a.call("GET","/skills",null)),r->{skills=r;commandsLoading--;emit();},e->commandLoadFailed("技能",e));
    }
    private void commandLoadFailed(String name,Exception e){commandsLoading=Math.max(0,commandsLoading-1);commandsError+=(commandsError.isEmpty()?"":"、")+name;if(e instanceof ApiClient.ApiException a&&a.status==401){failure(e);return;}emit();}
    private String settingKey(String kind) {return base+"|"+username+"|"+agent+"|"+(chat==null?"draft":chat.optString("id"))+"|"+kind;}
    private String setting(String kind,String fallback) {return prefs.getString(settingKey(kind),fallback);}
    public void setApprovalLevel(String level) {approvalLevel=level;prefs.edit().putString(settingKey("level"),level).apply();emit();}
    public void saveDraft() {prefs.edit().putString(settingKey("draft"),draft).apply();}
    public void newChat() {
        refreshWelcomePrompts();
        saveDraft();invalidate();chat=null;transcript.clear();attachments.clear();approvals=new JSONArray();expanded.clear();autoFollow=true;loopActive=false;selectedLoop=new JSONObject();
        draft=setting("draft","");prefs.edit().remove("lastChat").apply();status="新建任务";refreshCatalog();emit();
    }
    public void open(JSONObject spec) {
        saveDraft();invalidate();chat=Json.copy(spec);transcript.clear();attachments.clear();approvals=new JSONArray();expanded.clear();autoFollow=true;loopActive=false;selectedLoop=new JSONObject();
        draft=setting("draft","");prefs.edit().putString("lastChat",chat.optString("id")).apply();busy=true;status="正在读取对话…";emit();
        String path="/chats/"+ApiClient.enc(chat.optString("id"));
        task(a->Json.object(a.call("GET",path,null)),r->{
            busy=false;boolean active="running".equals(r.optString("status")) || "running".equals(spec.optString("status"));
            JSONArray history=Json.array(r.opt("messages"));
            // Persisted and runtime message IDs differ. Keep completed turns, replay the active response once.
            if(active){int lastUser=-1;for(int i=0;i<history.length();i++)if(Json.object(history.opt(i)).optString("role").equals("user"))lastUser=i;
                if(lastUser>=0){JSONArray committed=new JSONArray();for(int i=0;i<=lastUser;i++)committed.put(history.opt(i));history=committed;}}
            transcript.history(history);if(!active)transcript.finish(false);status="已连接";emit();if(active)reconnect();
        },e->{busy=false;failure(e);});refreshCatalog();refreshLoop();pollApprovals();
    }
    public void refreshThinking() {
        String path=chat==null?"/chats/thinking-default":"/chats/"+ApiClient.enc(chat.optString("id"))+"/thinking";
        int revision=++thinkingRevision;thinkingLoading=true;thinking=new JSONObject();activeContextLimit=0;emit();
        task(a->Json.object(a.call("GET",path,null)),r->{if(revision!=thinkingRevision)return;thinkingLoading=false;sessionModels=true;thinking=r;refreshContextLimit();emit();},e->{
            if(revision!=thinkingRevision)return;
            if(e instanceof ApiClient.ApiException x && (x.status==404 || x.status==405)) {
                sessionModels=false;String activePath="/models/active?scope=effective&agent_id="+ApiClient.enc(agent);
                task(a->Json.object(a.call("GET",activePath,null)),r->{if(revision!=thinkingRevision)return;thinkingLoading=false;thinking=Json.copy(Json.object(r.opt("active_llm")));activeContextLimit=Math.max(0,r.optLong("effective_max_input_length",0));emit();},error->{if(revision!=thinkingRevision)return;thinkingLoading=false;failure(error);});
            } else {thinkingLoading=false;failure(e);}
        });
    }
    private void refreshContextLimit(){
        int revision=thinkingRevision;
        activeContextLimit=Math.max(0,thinking.optLong("effective_max_input_length",0));
        if(activeContextLimit>0)return;
        task(a->Json.object(a.call("GET","/models/active?scope=effective&agent_id="+ApiClient.enc(a.agent),null)),r->{
            if(revision!=thinkingRevision)return;
            JSONObject slot=Json.object(r.opt("active_llm"));
            if(slot.optString("model").equals(thinking.optString("model"))&&(thinking.optString("provider_id").isEmpty()||slot.optString("provider_id").equals(thinking.optString("provider_id"))))activeContextLimit=Math.max(0,r.optLong("effective_max_input_length",0));emit();
        },e->{if(e instanceof ApiClient.ApiException a&&a.status==401)failure(e);});
    }
    public void chooseModel(JSONObject model) {
        if(running || busy || thinkingLoading){notice("请等待当前任务或模型加载结束后切换模型");return;}
        int revision=++thinkingRevision;
        if(!sessionModels) {
            JSONObject body=Json.copy(model);Json.put(body,"scope","agent");Json.put(body,"agent_id",agent);busy=true;emit();
            task(a->a.call("PUT","/models/active",body),r->{busy=false;refreshThinking();},e->{busy=false;failure(e);});return;
        }
        if(chat==null) {ensureChat(()->chooseModel(model));return;}
        String path="/chats/"+ApiClient.enc(chat.optString("id"))+"/model";busy=true;emit();
        task(a->Json.object(a.call("PUT",path,model)),r->{busy=false;if(revision!=thinkingRevision){refreshThinking();return;}thinking=r;refreshContextLimit();emit();},e->{busy=false;refreshThinking();failure(e);});
    }
    public void chooseThinking(JSONObject value) {
        if(running || busy || thinkingLoading)return;
        if(!ThinkingOptions.accepts(sessionModels,thinking,value)){notice("当前模型不支持此思考选项，请重新选择");return;}
        if(chat==null){ensureChat(()->chooseThinking(value));return;}
        int revision=++thinkingRevision;busy=true;emit();
        String path="/chats/"+ApiClient.enc(chat.optString("id"))+"/thinking?model_key="+ApiClient.enc(thinking.optString("model_key"));
        task(a->Json.object(a.call("PUT",path,value)),r->{busy=false;if(revision!=thinkingRevision){refreshThinking();return;}thinking=r;emit();},e->{busy=false;refreshThinking();failure(e);});
    }
    private void ensureChat(Runnable next) {
        if(chat!=null){next.run();return;}busy=true;emit();String sid="console:android-"+UUID.randomUUID();
        task(a->Json.object(a.call("POST","/chats",ChatHistory.newConsoleChat(sid))),r->{
            prefs.edit().remove(settingKey("draft")).apply();chat=r;busy=false;prefs.edit().putString("lastChat",r.optString("id")).apply();setApprovalLevel(approvalLevel);saveDraft();next.run();
        },e->{busy=false;failure(e);});
    }
    public void send() {
        if(busy || running || uploads>0)return;
        if(draft.trim().isEmpty() && attachments.isEmpty())return;
        ensureChat(this::sendExisting);
    }
    private JSONObject identity() {return Json.obj("session_id",chat.optString("session_id"),"user_id",chat.optString("user_id","default"),"channel",chat.optString("channel","console"),"stream",true);}
    /** Direct context actions do not consume the composer's draft or attachments. */
    public void contextCommand(String command){
        if(!List.of("/compact","/new","/clear","/skills").contains(command))return;
        if(running||busy){notice("请等待当前任务结束");return;}
        if(chat==null){if(command.equals("/skills")){ensureChat(()->contextCommand(command));}else notice("先开始一段对话，再管理上下文");return;}
        String id=UUID.randomUUID().toString();pendingReceipt=id;JSONObject body=identity();JSONArray content=Json.arr(Json.obj("type","text","text",command));
        Json.put(body,"input",Json.arr(Json.obj("role","user","type","message","content",content,"metadata",Json.obj("qwenpaw_client_message_id",id))));
        Json.put(body,"request_context",Json.obj("approval_level",approvalLevel));
        transcript.beginTurn();transcript.addUser(id,content);autoFollow=true;expanded.clear();reconnectAttempts=0;compacting=command.equals("/compact");startStream(body,false);
    }
    private void sendExisting() {
        String text=draft;String slash=selectedLoop.optString("slash_command","");
        if(!loopActive && !slash.isEmpty() && !text.stripLeading().startsWith("/"))text="/"+slash+" "+text;
        JSONArray content=new JSONArray();if(!text.isEmpty())content.put(Json.obj("type","text","text",text));
        for(JSONObject attachment:attachments)content.put(Json.copy(attachment));
        String id=UUID.randomUUID().toString();pendingReceipt=id;JSONObject body=identity();
        Json.put(body,"input",Json.arr(Json.obj("role","user","type","message","content",content,"metadata",Json.obj("qwenpaw_client_message_id",id))));
        Json.put(body,"request_context",Json.obj("approval_level",approvalLevel));
        transcript.beginTurn();transcript.addUser(id,content);draft="";saveDraft();attachments.clear();autoFollow=true;expanded.clear();reconnectAttempts=0;
        startStream(body,false);refreshChats(false);
    }
    private void startStream(JSONObject body,boolean reconnecting) {
        cancelRecovery();settleRevision++;
        if(!reconnecting || !turn.active())turn.begin();
        long turnId=turn.id();
        long target=generation;ApiClient client=api();Call call=client.stream(body);stream=call;running=true;status=reconnecting?"正在恢复连接…":compacting?"正在压缩上下文…":"正在回复…";emit();
        io.execute(()->{Exception error=null;try{client.consume(call,data->{Object parsed=Json.parse(data);if(!(parsed instanceof JSONObject event))throw new IOException("无法解析服务器流事件");
            main.post(()->{if(target!=generation || !turn.current(turnId) || stream!=call)return;transcript.accept(event);if(!turn.stopRequested())status=compacting?"正在压缩上下文…":"正在回复…";throttledEmit();});});}
            catch(Exception e){error=e;}
            Exception endError=error;main.post(()->{if(target!=generation || !turn.current(turnId) || stream!=call)return;stream=null;
                if(endError instanceof ApiClient.ApiException a && (a.status==401 || a.status==403)){running=false;failure(endError);return;}
                settle(turnId,endError);
            });
        });
    }
    private void settle(long turnId,Exception error) {
        if(chat==null || !turn.current(turnId) || turn.stopPending())return;
        String path="/chats/"+ApiClient.enc(chat.optString("id"));
        long revision=++settleRevision;boolean stopping=turn.stopRequested();
        task(a->Json.object(stopping?a.optionalStatus(path):a.call("GET",path,null)),r->{
            if(!turn.current(turnId) || revision!=settleRevision)return;
            String remoteStatus=r.optString("status");
            if(turn.stopRequested()) {
                if(turn.canFinishStop(turnId,remoteStatus,stream!=null && !transcript.cancelled,SystemClock.elapsedRealtime()>=stopDrainUntil))finishStopped(turnId);
                else scheduleStopCheck(turnId);
                return;
            }
            if(!remoteStatus.equals("idle")) {scheduleReconnect(turnId);return;}
            if(transcript.cancelled){finishStopped(turnId);return;}
            JSONArray history=Json.array(r.opt("messages"));
            boolean receiptFound=pendingReceipt.isEmpty();
            for(int i=0;i<history.length();i++){JSONObject meta=Json.object(Json.object(history.opt(i)).opt("metadata"));JSONObject original=meta.optJSONObject("metadata");if(original==null)original=meta;if(pendingReceipt.equals(original.optString("qwenpaw_client_message_id")))receiptFound=true;}
            if(error!=null&&!receiptFound){turn.finish(turnId);cancelRecovery();running=false;transcript.finish(false);status="发送状态未确认；已保留本地消息。请重连核对，避免重复发送";emit();return;}
            String streamError=transcript.error;
            // Preserve optimistic content if persistence has not caught up; never auto-resubmit a prompt.
            // System-command replies may never be persisted by older servers. A history
            // lacking this request's receipt must not erase its streamed result or prompt.
            if(history.length()>0 && receiptFound) {
                Map<String,Boolean> preserved=new HashMap<>();
                if(!autoFollow)for(Transcript.Row row:transcript.rows())preserved.put(row.kind+row.text+row.title,expanded.getOrDefault(row.id,!row.done));
                UsageSnapshot liveUsage=transcript.metrics;boolean live=transcript.liveMetrics;
                transcript.history(history);
                if(liveUsage!=null&&(live||transcript.metrics==null)){transcript.metrics=liveUsage;transcript.usage=liveUsage.usage.toString();}
                if(!autoFollow)for(Transcript.Row row:transcript.rows()){Boolean value=preserved.get(row.kind+row.text+row.title);if(value!=null)expanded.put(row.id,value);}
            }
            turn.finish(turnId);cancelRecovery();pendingReceipt="";compacting=false;transcript.error=streamError;transcript.finish(false);running=false;status=!streamError.isEmpty()?"生成失败，请查看错误信息":error==null?"已完成":"连接中断，已读取服务器历史；请核对发送结果";
            if(error!=null)notice(status);emit();refreshLoop();refreshChats(false);pollApprovals();
        },e->{if(!turn.current(turnId) || revision!=settleRevision)return;if(e instanceof ApiClient.ApiException a && a.status==401){running=false;failure(e);}else if(turn.stopRequested())scheduleStopCheck(turnId);else scheduleReconnect(turnId);});
    }
    private void cancelRecovery() {
        if(reconnectTask!=null)main.removeCallbacks(reconnectTask);reconnectTask=null;
        if(stopCheckTask!=null)main.removeCallbacks(stopCheckTask);stopCheckTask=null;
    }
    private void scheduleReconnect(long turnId) {
        if(!turn.canReconnect(turnId))return;
        if(reconnectAttempts>=5){status="连接中断，可点击停止或手动重连核对任务状态";emit();return;}
        int delay=Math.min(8000,1000<<reconnectAttempts++);long target=generation;status="网络中断，正在重连…";emit();
        if(reconnectTask!=null)main.removeCallbacks(reconnectTask);
        reconnectTask=()->{reconnectTask=null;if(target==generation && turn.canReconnect(turnId))reconnect();};main.postDelayed(reconnectTask,delay);
    }
    public void reconnect() {
        if(chat==null)return;
        if(turn.stopRequested()){if(!turn.stopPending()){stopChecks=0;settle(turn.id(),null);}return;}
        if(stream!=null)return;JSONObject body=identity();Json.put(body,"reconnect",true);Json.put(body,"input",new JSONArray());startStream(body,true);
    }
    public boolean stopping() {return turn.stopping();}
    public void stop() {
        if(chat==null || !running || !turn.requestStop())return;
        long turnId=turn.id();String chatId=chat.optString("id");cancelRecovery();settleRevision++;stopChecks=0;
        status="正在停止…";emit();
        task(a->a.stopChat(chatId),r->{
            if(!turn.current(turnId))return;
            turn.acceptStop(turnId,r.optBoolean("stopped"));stopDrainUntil=SystemClock.elapsedRealtime()+5000;
            // Keep the original SSE alive to consume its final tool outputs/terminal event.
            if(stream==null)settle(turnId,null);else scheduleStopCheck(turnId);
        },e->{
            if(!turn.current(turnId))return;turn.failStop(turnId);
            if(e instanceof ApiClient.ApiException a && a.status==401){failure(e);return;}
            status="停止未确认，可再次点击停止；正在核对服务端状态";notice(status);emit();
            settle(turnId,null);
        });
    }
    private void scheduleStopCheck(long turnId) {
        if(!turn.current(turnId) || !turn.stopRequested() || turn.stopPending())return;
        if(stopCheckTask!=null)main.removeCallbacks(stopCheckTask);
        if(++stopChecks>10){turn.failStop(turnId);status="停止未确认，可再次点击停止或手动重连核对";emit();return;}
        long target=generation;
        stopCheckTask=()->{stopCheckTask=null;if(target==generation && turn.current(turnId))settle(turnId,null);};
        main.postDelayed(stopCheckTask,1000);
    }
    private void finishStopped(long turnId) {
        if(!turn.current(turnId) || turn.stopPending())return;
        boolean confirmed=turn.serverStopped() || transcript.cancelled;
        turn.finish(turnId);cancelRecovery();settleRevision++;
        // Only detach a stalled subscription after idle was confirmed and the drain grace elapsed.
        Call old=stream;stream=null;if(old!=null)old.cancel();
        transcript.finish(true);running=false;pendingReceipt="";compacting=false;
        status=confirmed?"已停止":"任务已结束";emit();refreshLoop();refreshChats(false);pollApprovals();
    }
    public void refreshLoop() {
        if(chat==null)return;String path="/loops/status?chat_id="+ApiClient.enc(chat.optString("id"));
        task(a->Json.object(a.call("GET",path,null)),r->{loopActive=!r.optString("state","idle").equals("idle");selectedLoop=loopActive?Json.object(r.opt("mode")):new JSONObject();emit();},e->{});
    }
    private final Runnable poll=new Runnable(){public void run(){if(foreground && loggedIn){pollApprovals();refreshChats(false,true,null,null);}if(foreground)main.postDelayed(this,3000);}};
    public void pollApprovals() {
        if(chat==null || approvalBusy || !loggedIn)return;approvalBusy=true;
        String session=chat.optString("session_id"),owner=agent;
        task(a->Json.object(a.call("GET","/approval/list?session_id="+ApiClient.enc(session),null)),r->{
            approvalBusy=false;JSONArray filtered=new JSONArray(),all=Json.array(r.opt("pending_approvals"));
            for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null && item.optString("owner_agent_id",item.optString("agent_id")).equals(owner) && item.optString("root_session_id").equals(session))filtered.put(item);}
            if(!filtered.toString().equals(approvals.toString())){approvals=filtered;emit();}
        },e->{approvalBusy=false;if(e instanceof ApiClient.ApiException a && a.status==401)failure(e);});
    }
    public void decide(JSONObject approval,boolean allow,String reason,String scope) {
        if(chat==null || !approval.optString("root_session_id").equals(chat.optString("session_id")))return;
        String owner=approval.optString("owner_agent_id",approval.optString("agent_id"));if(!owner.equals(agent))return;
        JSONObject payload=Json.obj("request_id",approval.optString("request_id"),"session_id",approval.optString("root_session_id"),"reason",reason);
        if(allow)Json.put(payload,"scope",scope);
        task(a->Json.object(a.call("POST","/approval/"+(allow?"approve":"deny"),payload)),r->{notice(r.optString("message",allow?"已批准":"已拒绝"));pollApprovals();});
    }
    public void upload(File file,String name,String mime) {
        uploads++;emit();long target=generation;ApiClient client=api();
        io.execute(()->{JSONObject result=null;Exception failure=null;try{result=client.upload("/console/upload",file,name,mime);}catch(Exception e){failure=e;}finally{file.delete();}
            JSONObject value=result;Exception error=failure;main.post(()->{if(target!=generation)return;uploads=Math.max(0,uploads-1);
                if(error!=null){failure(error);return;}
                String type=mime!=null && mime.startsWith("image/")?"image":"file";JSONObject item=Json.obj("type",type,"file_name",value.optString("file_name",name));Json.put(item,type.equals("image")?"image_url":"file_url",value.optString("url"));attachments.add(item);emit();});
        });
    }
    public List<String> slashCommands() {
        ArrayList<String> result=new ArrayList<>();for(CommandCatalog.Entry item:CommandCatalog.build(loops,skills))result.add(item.command());return result;
    }

    public void pauseFollow() {
        if(autoFollow)ProcessGroups.freeze(transcript.rows(),running,expanded);
        if(autoFollow)for(Transcript.Row row:transcript.rows())if(row.kind.equals("tool") || row.kind.equals("thinking"))expanded.putIfAbsent(row.id,!row.done);
        autoFollow=false;
    }
}
