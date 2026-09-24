package cn.qwenpaw.android;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ProtocolTest {
    @Test public void sseCommentsCrlfMultilineAndDone() throws Exception {
        List<String> events=new ArrayList<>();SseReader.read(new StringReader("\uFEFF: heartbeat\r\nevent: message\r\ndata: first\r\ndata: second\r\n\r\ndata: [DONE]\n\ndata: ignored\n\n"),events::add);
        assertEquals(List.of("first\nsecond"),events);
    }
    @Test public void sseFlushesFinalUnterminatedFrame() throws Exception {
        List<String> events=new ArrayList<>();SseReader.read(new StringReader("data: 最后一帧"),events::add);assertEquals(List.of("最后一帧"),events);
    }
    @Test public void sseIgnoresHeartbeatOnly() throws Exception {List<String> e=new ArrayList<>();SseReader.read(new StringReader(": ping\n\n"),e::add);assertTrue(e.isEmpty());}
    @Test public void acceptsDomainAndPortWithPrefix(){assertEquals("https://example.com:8443/paw",ServerAddress.create("https","example.com","8443","/paw/"));}
    @Test public void acceptsIpv6(){assertEquals("http://[::1]:8088",ServerAddress.create("http","::1","8088",""));}
    @Test(expected=IllegalArgumentException.class) public void rejectsEmbeddedCredentials(){ServerAddress.create("https","user@evil.example","","");}
    @Test(expected=IllegalArgumentException.class) public void rejectsHostPath(){ServerAddress.create("https","example.com/api","","");}
    @Test(expected=IllegalArgumentException.class) public void rejectsZeroPort(){ServerAddress.create("http","localhost","0","");}
    @Test(expected=IllegalArgumentException.class) public void rejectsOutOfRangePort(){ServerAddress.create("http","localhost","65536","");}
    @Test(expected=IllegalArgumentException.class) public void rejectsTraversalPrefix(){ServerAddress.create("https","example.com","","../api");}
    @Test public void originRequiresSchemeHostAndPort(){
        okhttp3.HttpUrl base=okhttp3.HttpUrl.get("https://example.com");
        assertTrue(ApiClient.sameOrigin(base,okhttp3.HttpUrl.get("https://example.com/a")));
        assertFalse(ApiClient.sameOrigin(base,okhttp3.HttpUrl.get("http://example.com/a")));
        assertFalse(ApiClient.sameOrigin(base,okhttp3.HttpUrl.get("https://example.com:8443/a")));
        assertFalse(ApiClient.sameOrigin(base,okhttp3.HttpUrl.get("https://example.com.evil/a")));
    }
    private JSONObject delta(long sequence,String value){return Json.obj("object","content","type","text","msg_id","a","index",0,"delta",true,"text",value,"sequence_number",sequence);}
    @Test public void replayDeltasAreDeduplicated(){Transcript t=new Transcript();t.accept(delta(1,"你"));t.accept(delta(2,"好"));t.accept(delta(1,"你"));assertEquals("你好",t.rows().get(0).text);}
    @Test public void snapshotReplacesAccumulatedDelta(){Transcript t=new Transcript();t.accept(delta(1,"hello"));t.accept(Json.obj("object","message","id","a","role","assistant","content",Json.arr(Json.obj("type","text","text","hello world")),"status","completed"));assertEquals(1,t.rows().size());assertEquals("hello world",t.rows().get(0).text);}
    @Test public void nestedToolArgumentsAreAccumulated(){
        Transcript t=new Transcript();t.accept(Json.obj("object","message","id","m","type","plugin_call","role","assistant","content",new JSONArray()));
        t.accept(Json.obj("object","content","msg_id","m","index",0,"type","data","delta",true,"data",Json.obj("call_id","c","name","write_file","arguments","{\"x\":")));
        t.accept(Json.obj("object","content","msg_id","m","index",0,"type","data","delta",true,"data",Json.obj("arguments","1}")));
        assertEquals("{\"x\":1}",t.rows().get(0).params);assertEquals("write_file",t.rows().get(0).title);
    }
    @Test public void toolOutputPairsAcrossMessagesAndFilesStaySeparate(){
        Transcript t=new Transcript();t.history(Json.arr(
            Json.obj("id","call","type","plugin_call","role","assistant","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","shared","name","send_file_to_user","arguments","{}")))),
            Json.obj("id","output","type","plugin_call_output","role","assistant","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","shared","output","[{\"type\":\"data\",\"source\":{\"type\":\"url\",\"url\":\"file:///tmp/a.txt\"},\"name\":\"a.txt\"}]"))))
        ));assertEquals(2,t.rows().size());assertEquals("tool",t.rows().get(0).kind);assertTrue(t.rows().get(0).done);assertEquals("file",t.rows().get(1).kind);assertEquals("a.txt",t.rows().get(1).title);
    }
    @Test public void reasoningEnvelopeCreatesCollapsibleRow(){Transcript t=new Transcript();t.history(Json.arr(Json.obj("id","a","type","reasoning","role","assistant","status","completed","content",Json.arr(Json.obj("type","text","text","thinking")))));assertEquals("thinking",t.rows().get(0).kind);assertTrue(t.rows().get(0).done);}
    @Test public void indicesAreBounded(){Transcript t=new Transcript();JSONObject d=delta(1,"x");Json.put(d,"index",Integer.MAX_VALUE);t.accept(d);assertFalse(t.error.isEmpty());}
    @Test public void sequenceNumbersResetBetweenTurns(){Transcript t=new Transcript();t.accept(delta(1,"a"));t.beginTurn();t.accept(delta(1,"b"));assertEquals("ab",t.rows().get(0).text);}
    private JSONArray syntheticMessages(){return Json.arr(
        Json.obj("id","question","role","user","status","completed","content",Json.arr(Json.obj("type","text","text","Draw a flowchart"))),
        Json.obj("id","call","type","plugin_call","role","assistant","status","completed","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","one","name","example_tool","arguments","{}")))),
        Json.obj("id","output","type","plugin_call_output","role","assistant","status","completed","content",Json.arr(Json.obj("type","data","data",Json.obj("call_id","one","output","[{\"type\":\"text\",\"text\":\"done\"}]")))),
        Json.obj("id","answer","role","assistant","status","completed","content",Json.arr(Json.obj("type","text","text","```mermaid\\ngraph TD; A-->B\\n```")))
    );}
    @Test public void syntheticStreamRendersMermaidAndTool(){
        Transcript t=new Transcript();t.accept(Json.obj("object","response","status","completed","output",syntheticMessages()));
        assertTrue(t.terminal);assertEquals(1,t.rows().stream().filter(r->r.kind.equals("tool")).count());assertTrue(t.rows().stream().anyMatch(r->r.text.contains("```mermaid")));
    }
    @Test public void syntheticHistoryAndStreamHaveEquivalentVisibleContent(){
        Transcript stream=new Transcript(),history=new Transcript();JSONArray messages=syntheticMessages();stream.accept(Json.obj("object","response","status","completed","output",messages));history.history(messages);
        List<String> left=new ArrayList<>(),right=new ArrayList<>();for(Transcript.Row r:stream.rows())if(!r.role.equals("user"))left.add(r.kind+":"+r.text+":"+r.url);for(Transcript.Row r:history.rows())if(!r.role.equals("user"))right.add(r.kind+":"+r.text+":"+r.url);assertEquals(right,left);
    }
}
