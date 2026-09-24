package cn.qwenpaw.android;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ProcessGroupsTest {
    private List<String> ids(List<Transcript.Row> rows){return rows.stream().map(r->r.id).toList();}
    private List<Transcript.Row> loopTurn(){return List.of(row("think1","thinking",0),row("explain1","text",0),row("call1","tool",0),row("think2","thinking",0),row("explain2","text",0),row("call2","tool",0),row("file","file",0),row("think3","thinking",0),row("answer","text",0));}
    @Test public void eachStreamPrefixKeepsInterleavedLoopOrder(){
        List<Transcript.Row> source=loopTurn();
        for(int end=1;end<=source.size();end++){
            List<Transcript.Row> prefix=source.subList(0,end);
            List<Transcript.Row> visible=ProcessGroups.project(prefix,true,new HashMap<>());
            assertEquals("stream prefix "+end,ids(prefix),ids(visible.stream().filter(r->!r.kind.equals("process")).toList()));
            assertEquals(1,visible.stream().filter(r->r.kind.equals("process")).count());
        }
    }
    @Test public void completionAndReopeningNeverMoveIntermediateTextOrFiles(){
        List<Transcript.Row> source=loopTurn();Map<String,Boolean> expanded=new HashMap<>();
        assertEquals(List.of("process:0","file","answer"),ids(ProcessGroups.project(source,false,expanded)));
        assertEquals("3 段思考 · 2 次工具调用 · 2 段中间输出",ProcessGroups.project(source,false,expanded).get(0).text);
        expanded.put("process:0",true);
        assertEquals(ids(source),ids(ProcessGroups.project(source,false,expanded).stream().filter(r->!r.kind.equals("process")).toList()));
    }
    @Test public void messageSnapshotsDeltasAndHistoryKeepLoopSequence(){
        Transcript transcript=new Transcript();JSONArrayBuilder fixture=new JSONArrayBuilder();
        for(int loop=0;loop<3;loop++){
            fixture.add(Json.obj("object","message","id","think"+loop,"type","reasoning","content","思考"+loop));
            fixture.add(Json.obj("object","message","id","text"+loop,"content","中间说明"+loop));
            fixture.add(Json.obj("object","message","id","tool"+loop,"content",Json.arr(Json.obj("type","tool_call","call_id","call"+loop,"name","query","arguments","{}"))));
            fixture.add(Json.obj("object","message","id","result"+loop,"content",Json.arr(Json.obj("type","tool_result","call_id","call"+loop,"output","结果"+loop))));
        }
        fixture.add(Json.obj("object","message","id","final","content","最终回复"));
        for(int i=0;i<fixture.items.length();i++)transcript.accept(fixture.items.optJSONObject(i));
        List<String> expected=List.of("think0:0","text0:0","call:call0","think1:0","text1:0","call:call1","think2:0","text2:0","call:call2","final:0");
        assertEquals(expected,ids(ProcessGroups.project(transcript.rows(),true,new HashMap<>()).stream().filter(r->!r.kind.equals("process")).toList()));
        transcript.accept(Json.obj("object","content","msg_id","final","index",0,"type","text","delta",true,"text","继续输出"));
        assertEquals(expected,ids(transcript.rows()));
        transcript.accept(Json.obj("object","response","status","completed","output",fixture.items));
        assertEquals(expected,ids(transcript.rows()));
        Transcript history=new Transcript();history.history(fixture.items);assertEquals(expected,ids(history.rows()));
    }
    private static class JSONArrayBuilder{final org.json.JSONArray items=new org.json.JSONArray();void add(org.json.JSONObject item){items.put(item);}}
    private Transcript.Row row(String id,String kind,int turn){Transcript.Row r=new Transcript.Row(id,kind,"assistant");r.turn=turn;r.text=id;return r;}
    private List<Transcript.Row> turn(){return List.of(row("thought","thinking",0),row("tool1","tool",0),row("file","file",0),row("thought2","thinking",0),row("tool2","tool",0),row("answer","text",0));}
    @Test public void completedTurnHasOneDisclosureAndKeepsFileAndAnswer(){List<Transcript.Row> r=ProcessGroups.project(turn(),false,new HashMap<>());assertEquals(List.of("process","file","text"),r.stream().map(x->x.kind).toList());assertEquals("2 段思考 · 2 次工具调用",r.get(0).text);}
    @Test public void runningTurnShowsEveryProcessStep(){List<Transcript.Row> r=ProcessGroups.project(turn(),true,new HashMap<>());assertEquals(7,r.size());assertEquals(2,r.stream().filter(x->x.kind.equals("tool")).count());}
    @Test public void pausedReaderKeepsOpenGroupAfterCompletion(){Map<String,Boolean> state=new HashMap<>();ProcessGroups.freeze(turn(),true,state);assertEquals(7,ProcessGroups.project(turn(),false,state).size());}
    @Test public void returningToLatestRestoresCombinedCollapse(){Map<String,Boolean> state=new HashMap<>();ProcessGroups.freeze(turn(),true,state);state.clear();assertEquals(3,ProcessGroups.project(turn(),false,state).size());}
    @Test public void lastTurnDoesNotReopenEarlierProcess(){List<Transcript.Row> two=new ArrayList<>(turn());two.add(row("next","thinking",1));List<Transcript.Row> r=ProcessGroups.project(two,true,new HashMap<>());assertEquals(2,r.stream().filter(x->x.kind.equals("process")).count());assertEquals(1,r.stream().filter(x->x.kind.equals("thinking")).count());assertEquals("next",r.get(r.size()-1).id);}
    @Test public void explicitCollapseSurvivesFurtherStreamUpdates(){Map<String,Boolean> state=new HashMap<>();state.put("process:0",false);ProcessGroups.freeze(turn(),true,state);assertEquals(3,ProcessGroups.project(turn(),true,state).size());}
    @Test public void historyMessagesShareTurnAcrossThinkingToolAndResult(){Transcript t=new Transcript();t.history(Json.arr(Json.obj("id","u","role","user","content","hello"),Json.obj("id","a","type","reasoning","content","thinking"),Json.obj("id","b","content","answer"),Json.obj("id","u2","role","user","content","next"),Json.obj("id","a2","content","reply")));assertEquals(List.of(0,0,0,1,1),t.rows().stream().map(x->x.turn).toList());}
    @Test public void finalMessageKeepsAllTextBlocksAndFiles(){
        Transcript t=new Transcript();t.history(Json.arr(
            Json.obj("id","u","role","user","content","报告"),
            Json.obj("id","progress","role","assistant","content","开始生成"),
            Json.obj("id","final","role","assistant","content",Json.arr(
                Json.obj("type","text","text","报告已完成"),
                Json.obj("type","file","filename","report.md","file_url","/files/report.md"),
                Json.obj("type","text","text","请查看附件")))));
        assertEquals(List.of("u:0","process:0","final:0","final:1:file","final:2"),ids(ProcessGroups.project(t.rows(),false,new HashMap<>())));
    }
    @Test public void completedTextOnlyTurnFoldsEarlierRepliesButLeavesSystemError(){
        Transcript.Row warning=new Transcript.Row("warning","text","system");warning.turn=0;
        List<Transcript.Row> source=List.of(row("progress","text",0),row("answer","text",0),warning);
        assertEquals(List.of("process:0","answer","warning"),ids(ProcessGroups.project(source,false,new HashMap<>())));
        assertEquals(ids(source),ids(ProcessGroups.project(source,true,new HashMap<>())));
    }
    @Test public void cancelledTurnFoldsIntermediateOutputAndCanReopenInOrder(){
        List<Transcript.Row> source=new ArrayList<>(loopTurn());source.get(5).interrupted=true;
        Map<String,Boolean> state=new HashMap<>();
        assertTrue(ProcessGroups.project(source,false,state).get(0).interrupted);
        state.put("process:0",true);
        assertEquals(ids(source),ids(ProcessGroups.project(source,false,state).stream().filter(r->!r.kind.equals("process")).toList()));
    }
}
