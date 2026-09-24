package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ChatStopTest {
    @Test public void stopDeduplicatesClicksAndInvalidatesScheduledReconnect() {
        ChatTurn turn=new ChatTurn();long id=turn.begin();assertTrue(turn.canReconnect(id));
        assertTrue(turn.requestStop());assertTrue(turn.stopping());assertFalse(turn.requestStop());
        assertFalse(turn.canReconnect(id));
        assertFalse(turn.canFinishStop(id,"idle",false,true)); // SSE ending before HTTP ack cannot release send.
        turn.acceptStop(id,true);
        assertFalse(turn.requestStop());assertFalse(turn.canReconnect(id));
        assertTrue(turn.canFinishStop(id,"idle",false,false));
    }
    @Test public void idleConfirmationAndDrainAreRequiredBeforeFinishing() {
        ChatTurn turn=new ChatTurn();long id=turn.begin();turn.requestStop();turn.acceptStop(id,true);
        assertFalse(turn.canFinishStop(id,"running",false,true));
        assertFalse(turn.canFinishStop(id,"",false,true));
        assertFalse(turn.canFinishStop(id,"idle",true,false));
        assertTrue(turn.canFinishStop(id,"idle",true,true));
    }
    @Test public void falseStopResultDoesNotDetachPotentiallyUnacceptedSubmission() {
        ChatTurn turn=new ChatTurn();long id=turn.begin();turn.requestStop();turn.acceptStop(id,false);
        assertFalse(turn.serverStopped());assertFalse(turn.canFinishStop(id,"idle",true,true));
        assertTrue(turn.canFinishStop(id,"idle",false,true));
    }
    @Test public void stopFailureAllowsRetryWithoutAutomaticReconnect() {
        ChatTurn turn=new ChatTurn();long id=turn.begin();turn.requestStop();turn.failStop(id);
        assertFalse(turn.stopping());assertTrue(turn.active());assertFalse(turn.canReconnect(id));
        assertTrue(turn.requestStop());turn.acceptStop(id,true);assertTrue(turn.serverStopped());
    }
    @Test public void lateStopAndHistoryCallbacksCannotAffectNextTurnOrWorkspace() {
        ChatTurn turn=new ChatTurn();long old=turn.begin();turn.requestStop();turn.acceptStop(old,true);turn.finish(old);
        assertFalse(turn.current(old));long next=turn.begin();
        turn.acceptStop(old,true);turn.failStop(old);turn.finish(old);
        assertTrue(turn.current(next));assertFalse(turn.stopRequested());assertFalse(turn.serverStopped());
        assertFalse(turn.canReconnect(old));assertTrue(turn.canReconnect(next));
        turn.reset();assertFalse(turn.current(next));assertFalse(turn.canReconnect(next));
    }
    private JSONObject call(String id) {return Json.obj("object","message","id","call-"+id,"type","plugin_call","role","assistant","status","completed","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id",id,"name","execute_shell_command","arguments","{}"))));}
    private JSONObject output(String id,String state,String status,String text) {return Json.obj("object","message","id","output-"+id,"type","plugin_call_output","role","assistant","status",status,"content",Json.arr(Json.obj("type","data","data",Json.obj("call_id",id,"state",state,"output",text))));}
    private Transcript.Row tool(Transcript t,String id) {return t.rows().stream().filter(r->r.id.equals("call:"+id)).findFirst().orElseThrow();}
    @Test public void stoppedTurnPreservesPartialReplyAndMarksOnlyUnfinishedTools() {
        Transcript t=new Transcript();t.addUser("u",Json.arr(Json.obj("type","text","text","开始")));
        t.accept(call("finished"));t.accept(output("finished","success","completed","完整结果"));
        t.accept(call("active"));assertFalse(tool(t,"active").done);
        t.accept(Json.obj("object","message","id","reply","role","assistant","status","in_progress","content",Json.arr(Json.obj("type","text","text","已经输出的一半"))));
        t.finish(true);
        assertTrue(tool(t,"active").interrupted);assertTrue(tool(t,"active").done);
        assertFalse(tool(t,"finished").interrupted);assertEquals("完整结果",tool(t,"finished").text);
        assertTrue(t.rows().stream().anyMatch(r->r.text.equals("已经输出的一半")));
        assertTrue(ProcessGroups.project(t.rows(),false,new HashMap<>()).stream().anyMatch(r->r.kind.equals("process") && r.title.contains("已中断")));
    }
    @Test public void interruptedRowsStayClosedWhenNextTurnStarts() {
        Transcript t=new Transcript();t.addUser("u1",new JSONArray());t.accept(call("old"));t.finish(true);
        t.beginTurn();t.addUser("u2",new JSONArray());t.accept(call("new"));
        assertTrue(tool(t,"old").interrupted);assertTrue(tool(t,"old").done);
        assertFalse(tool(t,"new").interrupted);assertFalse(tool(t,"new").done);
    }
    @Test public void streamedToolOutputIsNotFinishedUntilTerminalResult() {
        Transcript t=new Transcript();t.accept(call("a"));t.accept(output("a","running","in_progress","部分日志"));
        assertFalse(tool(t,"a").done);t.finish(true);
        assertTrue(tool(t,"a").interrupted);assertEquals("部分日志",tool(t,"a").text);
    }
    @Test public void cancellationWireVariantsAndExplicitToolErrorsArePreserved() {
        for(String status:List.of("cancelled","canceled")) {
            Transcript t=new Transcript();t.accept(call("a"));t.accept(Json.obj("object","response","status",status));
            assertTrue(t.cancelled);assertTrue(tool(t,"a").interrupted);
        }
        Transcript t=new Transcript();t.accept(call("a"));t.accept(output("a","interrupted","completed","用户终止"));
        assertTrue(tool(t,"a").interrupted);assertTrue(tool(t,"a").done);
        t.accept(call("b"));t.accept(output("b","error","completed","工具异常"));t.finish(true);
        assertTrue(tool(t,"b").failed);assertFalse(tool(t,"b").interrupted);
    }
    @Test public void userCancellationDoesNotShowGenericGenerationFailure() {
        Transcript t=new Transcript();t.accept(Json.obj("type","error","error","Task cancelled"));t.finish(true);
        assertTrue(t.rows().stream().anyMatch(r->r.text.contains("执行已中断") && r.text.contains("Task cancelled")));
        assertFalse(t.rows().stream().anyMatch(r->r.text.contains("生成失败")));
        t.clear();t.accept(Json.obj("type","error","error","Model failed"));t.finish(false);
        assertTrue(t.rows().stream().anyMatch(r->r.text.contains("生成失败")));
    }
}
