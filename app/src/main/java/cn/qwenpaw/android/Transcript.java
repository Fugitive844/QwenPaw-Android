package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

/** Reduces runtime snapshots/deltas by message id + content index; tool pairing uses call_id. */
public final class Transcript {
    private final LinkedHashMap<String,JSONObject> messages=new LinkedHashMap<>();
    private final Set<Long> sequences=new HashSet<>();
    private final Set<Integer> endedTurns=new HashSet<>(), stoppedTurns=new HashSet<>();
    public String error="", usage="";
    public UsageSnapshot metrics;
    public boolean liveMetrics;
    public boolean terminal, cancelled;
    public void clear() { messages.clear(); sequences.clear();endedTurns.clear();stoppedTurns.clear();error="";usage="";metrics=null;liveMetrics=false;terminal=false;cancelled=false; }
    private int lastTurn() {int turn=-1;for(JSONObject message:messages.values())if(message.optString("role").equals("user"))turn++;return turn;}
    public void beginTurn() {if(!messages.isEmpty())endedTurns.add(lastTurn());sequences.clear();error="";terminal=false;cancelled=false;liveMetrics=false; }
    public void finish(boolean stopped) {terminal=true;endedTurns.add(lastTurn());if(stopped){cancelled=true;stoppedTurns.add(lastTurn());}}
    public void history(JSONArray items) {
        clear(); for(int i=0;i<items.length();i++) snapshot(Json.object(items.opt(i)),"history-"+i);
    }
    public void addUser(String id,JSONArray content) { snapshot(Json.obj("id",id,"role","user","content",content),id); }
    private String id(JSONObject m,String fallback) { String id=m.optString("id",""); if(id.isEmpty()) id=m.optString("msg_id",fallback); return id; }
    private void snapshot(JSONObject message,String fallback) {
        String key=id(message,fallback); JSONObject existing=messages.get(key);
        JSONObject next=existing==null?new JSONObject():Json.copy(existing);
        for(Iterator<String> it=message.keys();it.hasNext();) { String k=it.next(); if(!message.isNull(k)) Json.put(next,k,message.opt(k)); }
        Json.put(next,"id",key); messages.put(key,next);
        UsageSnapshot found=UsageSnapshot.metadata(next);if(found!=null&&!liveMetrics){metrics=found;usage=found.usage.toString();}
    }
    public void accept(JSONObject e) {
        String object=e.optString("object"), type=e.optString("type"), status=e.optString("status");
        if(type.equals("turn_usage")) { UsageSnapshot found=UsageSnapshot.parse(e);if(found!=null){metrics=found;usage=found.usage.toString();liveMetrics=true;}return; }
        if(type.equals("error") || (!e.isNull("error") && e.has("error"))) error=Json.text(e.opt("error"));
        if(type.equals("replay_end") || type.equals("heartbeat")) return;
        if(e.has("sequence_number") && !sequences.add(e.optLong("sequence_number"))) return;
        if(object.equals("response")) {
            JSONArray out=e.optJSONArray("output");
            if(out!=null) for(int i=0;i<out.length();i++) snapshot(Json.object(out.opt(i)),"output-"+i);
            terminal=Arrays.asList("completed","failed","canceled","cancelled").contains(status);
            if(terminal)finish(status.equals("canceled") || status.equals("cancelled"));
            return;
        }
        if(object.equals("message")) { snapshot(e,"message-"+messages.size()); return; }
        if(!object.equals("content")) return;
        String key=e.optString("msg_id",""); if(key.isEmpty()) return;
        JSONObject m=messages.get(key);
        if(m==null) { m=Json.obj("id",key,"role","assistant","content",new JSONArray()); messages.put(key,m); }
        JSONArray content=m.optJSONArray("content"); if(content==null) {content=new JSONArray();Json.put(m,"content",content);}
        int index=e.optInt("index",0); if(index<0 || index>10000) { error="内容索引超出限制"; return; }
        while(content.length()<=index) content.put(new JSONObject());
        JSONObject block=Json.object(content.opt(index)); boolean delta=e.optBoolean("delta",false);
        for(Iterator<String> it=e.keys();it.hasNext();) {
            String k=it.next(); if(Arrays.asList("object","msg_id","index","delta","sequence_number").contains(k) || e.isNull(k)) continue;
            Object value=e.opt(k);
            if(delta && k.equals("data") && value instanceof JSONObject incoming) {
                JSONObject merged=Json.copy(Json.object(block.opt("data")));
                for(Iterator<String> fields=incoming.keys();fields.hasNext();) {
                    String field=fields.next();Object v=incoming.opt(field);if(v==JSONObject.NULL)continue;
                    if(v instanceof String && Arrays.asList("arguments","output").contains(field))v=merged.optString(field,"")+v;
                    Json.put(merged,field,v);
                }
                value=merged;
            } else if(delta && value instanceof String && Arrays.asList("text","thinking","arguments","partial_json","output").contains(k)) value=block.optString(k,"")+value;
            Json.put(block,k,value);
        }
        try { content.put(index,block); } catch(JSONException ex) { throw new IllegalArgumentException(ex); }
    }
    public List<Row> rows() {
        ArrayList<Row> result=new ArrayList<>(); Map<String,Row> calls=new HashMap<>();int turn=-1;
        for(Map.Entry<String,JSONObject> entry:messages.entrySet()) {
            JSONObject m=entry.getValue(); String role=m.optString("role","assistant"); Object raw=m.opt("content");
            if(role.equals("user"))turn++;int first=result.size();
            JSONArray blocks=raw instanceof JSONArray?(JSONArray)raw:Json.arr(Json.obj("type","text","text",Json.text(raw)));
            for(int i=0;i<blocks.length();i++) {
                JSONObject b=Json.object(blocks.opt(i)); String type=b.optString("type","text"); String key=entry.getKey()+":"+i;
                String envelope=m.optString("type","");
                String deliveryStatus=b.optString("status",m.optString("status"));
                if(type.equals("data") && b.optJSONObject("data")!=null && envelope.contains("call")) {b=b.optJSONObject("data");type=envelope;}
                String callId=b.optString("call_id",b.optString("id",m.optString("call_id","")));
                if(type.equals("tool_call") || type.equals("tool_use") || type.equals("function_call") || type.equals("plugin_call") || type.equals("mcp_tool_call")) {
                    JSONObject f=b.optJSONObject("function"); if(f==null) f=b;
                    Row r=calls.get(callId);
                    if(r==null) {r=new Row(callId.isEmpty()?key:"call:"+callId,"tool","assistant");result.add(r);if(!callId.isEmpty()) calls.put(callId,r);}
                    r.title=f.optString("name",b.optString("name","工具")); r.params=Json.text(f.opt("arguments"));
                    if(r.params.isEmpty()) r.params=Json.text(b.opt("input"));
                    // A completed call message only means its arguments arrived, not that the tool finished.
                    if(Arrays.asList("canceled","cancelled").contains(deliveryStatus)){r.interrupted=true;r.done=true;}
                    if(Arrays.asList("failed","rejected").contains(deliveryStatus)){r.failed=true;r.done=true;}
                    continue;
                }
                if(type.equals("tool_result") || type.equals("function_call_output") || type.equals("plugin_call_output") || type.equals("mcp_tool_call_output") || (role.equals("tool") && !callId.isEmpty())) {
                    Object output=b.has("output")?b.opt("output"):b.has("content")?b.opt("content"):b.opt("text");
                    Row r=calls.get(callId);
                    if(r==null) {r=new Row("call:"+callId,"tool","assistant");result.add(r);if(!callId.isEmpty())calls.put(callId,r);}
                    r.title=b.optString("name",r.title);r.text=toolText(output);
                    String state=b.optString("state"), resultStatus=b.optString("status",deliveryStatus);
                    r.interrupted=state.equals("interrupted") || resultStatus.equals("canceled") || resultStatus.equals("cancelled");
                    r.failed=state.equals("error") || state.equals("denied") || resultStatus.equals("failed") || resultStatus.equals("rejected");
                    r.done=r.interrupted || r.failed || (!Arrays.asList("created","in_progress","running").contains(resultStatus) && !Arrays.asList("pending","running","queued").contains(state));
                    extractFiles(output,key,result,0);
                    continue;
                }
                if(Arrays.asList("file","image","video","audio","data").contains(type)) { extractFiles(b,key,result,0,role); continue; }
                Row r=new Row(key,type.equals("thinking") || type.equals("reasoning") || envelope.equals("reasoning")?"thinking":"text",role);
                r.messageId=entry.getKey();
                r.text=b.optString("text",b.optString("thinking",b.optString("reasoning","")));
                r.done="completed".equals(m.optString("status"));
                r.interrupted=Arrays.asList("canceled","cancelled").contains(m.optString("status"));
                r.done|=r.interrupted;
                if(!r.text.isEmpty()) result.add(r);
            }
            if(role.equals("user")&&result.size()==first){Row placeholder=new Row(entry.getKey()+":question","text","user");placeholder.text="[无文字提问]";result.add(placeholder);}
            for(int i=first;i<result.size();i++){result.get(i).turn=turn;result.get(i).messageId=entry.getKey();}
        }
        for(Row row:result) {
            boolean ended=terminal || row.turn<turn || endedTurns.contains(row.turn);
            if(!row.done && ended){row.done=true;row.interrupted=row.kind.equals("tool") || (row.kind.equals("thinking") && stoppedTurns.contains(row.turn));}
        }
        // Attachments stay outside collapsible process rows, including send_file_to_user outputs.
        if(!error.isEmpty()){Row warning=new Row("stream-error","text","system");warning.text=(cancelled?"**执行已中断**":"**生成失败**")+"\n\n"+error;warning.done=true;result.add(warning);}
        return result;
    }
    private String toolText(Object value) {
        Object parsed=value instanceof String?Json.parse((String)value):value;
        if(parsed instanceof JSONArray blocks) {
            ArrayList<String> texts=new ArrayList<>();for(int i=0;i<blocks.length();i++){JSONObject block=blocks.optJSONObject(i);if(block!=null&&block.optString("type").equals("text"))texts.add(block.optString("text"));}
            if(!texts.isEmpty())return String.join("\n",texts);
        }
        return Json.text(value);
    }
    private void extractFiles(Object raw,String key,List<Row> out,int depth) {
        extractFiles(raw,key,out,depth,"assistant");
    }
    private void extractFiles(Object raw,String key,List<Row> out,int depth,String role) {
        if(depth>8 || raw==null) return;
        if(raw instanceof String s) { if(s.startsWith("[") || s.startsWith("{")) { Object p=Json.parse(s); if(!(p instanceof String)) extractFiles(p,key,out,depth+1,role); } return; }
        if(raw instanceof JSONArray a) {for(int i=0;i<a.length();i++)extractFiles(a.opt(i),key+":"+i,out,depth+1,role);return;}
        if(!(raw instanceof JSONObject b)) return;
        JSONObject source=b.optJSONObject("source");
        String url=source==null?"":source.optString("url","");
        if(url.isEmpty()) for(String k:List.of("file_url","image_url","video_url","audio_url","file_id")) {
            Object value=b.opt(k); if(value instanceof String) url=(String)value; else if(value instanceof JSONObject o) url=o.optString("url",""); if(!url.isEmpty()) break;
        }
        if(!url.isEmpty()) {
            Row r=new Row(key+":file","file",role);r.url=url;
            r.title=b.optString("filename",b.optString("file_name",b.optString("name",url.substring(url.lastIndexOf('/')+1))));
            r.text=b.optString("type","file"); r.done=true; out.add(r);
        }
        for(String k:List.of("content","output","result")) if(b.has(k)) extractFiles(b.opt(k),key+":"+k,out,depth+1,role);
    }
    public static final class Row {
        public final String id,kind,role;
        public String text="",title="工具",params="",url="",messageId="",outputPhase="";
        public boolean done, interrupted, failed;
        public int turn=-1;
        public Row(String id,String kind,String role) {this.id=id;this.kind=kind;this.role=role;}
        public String signature() {return kind+role+text+title+params+url+done+interrupted+failed+turn+messageId+outputPhase;}
    }
}
