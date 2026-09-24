package cn.qwenpaw.android;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class OutputPresentationTest {
    private List<Transcript.Row> visible(Transcript t,boolean running){
        return ProcessGroups.project(t.rows(),running,new HashMap<>(Map.of("process:0",true)));
    }
    private String phase(List<Transcript.Row> rows,String id){return rows.stream().filter(r->r.id.equals(id)).findFirst().orElseThrow().outputPhase;}
    @Test public void liveReplyBecomesIntermediateWhenToolFollowsAndFinalOnlyAtCompletion(){
        Transcript t=new Transcript();t.addUser("u",Json.arr(Json.obj("type","text","text","问题")));
        t.accept(Json.obj("object","message","id","progress","role","assistant","content","我来查询"));
        assertEquals("streaming",phase(visible(t,true),"progress:0"));
        t.accept(Json.obj("object","message","id","call","role","assistant","content",Json.arr(Json.obj("type","tool_call","call_id","one","name","query"))));
        assertEquals("intermediate",phase(visible(t,true),"progress:0"));
        t.accept(Json.obj("object","message","id","answer","role","assistant","content","最终内容"));
        assertEquals("streaming",phase(visible(t,true),"answer:0"));
        t.finish(false);
        assertEquals("final",phase(visible(t,false),"answer:0"));
        assertEquals("intermediate",phase(visible(t,false),"progress:0"));
        assertEquals("",phase(visible(t,false),"u:0"));
    }
    @Test public void allFinalMessageTextBlocksKeepSameStyleWhenNextTurnStarts(){
        Transcript t=new Transcript();t.history(Json.arr(
            Json.obj("id","u","role","user","content","问题"),
            Json.obj("id","answer","role","assistant","content",Json.arr(Json.obj("type","text","text","第一部分"),Json.obj("type","text","text","第二部分"))),
            Json.obj("id","u2","role","user","content","下一轮")));
        List<Transcript.Row> projected=visible(t,true);
        assertEquals("final",phase(projected,"answer:0"));assertEquals("final",phase(projected,"answer:1"));
    }
}
