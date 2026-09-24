package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class ChatExperienceTest {
    @Test public void newChatMarksPlaceholderForServerTitleGeneration() {
        JSONObject request=ChatHistory.newConsoleChat("console:android-example");
        assertEquals("console:android-example",request.optString("session_id"));
        assertEquals("console",request.optString("channel"));
        assertEquals("default",request.optString("user_id"));
        assertEquals("New Chat",request.optString("name"));
        assertEquals(request.optString("name"),Json.object(request.opt("meta")).optString("console_placeholder_name"));
    }
    @Test public void generatedTitleRefreshPreservesSessionIdentityAndMetadata() {
        JSONObject current=Json.obj("id","active","name","New Chat","session_id","runtime-session","meta",Json.obj("model","selected"));
        JSONArray latest=Json.arr(Json.obj("id","other","name","另一个对话"),Json.obj("id","active","name","本周工作计划","session_id","must-not-replace","meta",Json.obj("model","stale")));
        assertTrue(ChatHistory.syncName(current,latest));
        assertEquals("本周工作计划",current.optString("name"));
        assertEquals("runtime-session",current.optString("session_id"));
        assertEquals("selected",Json.object(current.opt("meta")).optString("model"));
        assertFalse(ChatHistory.syncName(current,latest));
        assertTrue(ChatHistory.syncName(current,Json.arr(Json.obj("id","active","name","手动命名"))));
        assertEquals("手动命名",current.optString("name"));
    }
    @Test public void missingOrUnrelatedTitlesDoNotClearCurrentChat() {
        JSONObject current=Json.obj("id","active","name","已有标题");
        assertFalse(ChatHistory.syncName(null,new JSONArray()));
        assertFalse(ChatHistory.syncName(new JSONObject(),Json.arr(Json.obj("name","无标识"))));
        assertFalse(ChatHistory.syncName(current,Json.arr(JSONObject.NULL,Json.obj("id","other","name","其他标题"))));
        assertFalse(ChatHistory.syncName(current,Json.arr(Json.obj("id","active"))));
        assertFalse(ChatHistory.syncName(current,Json.arr(Json.obj("id","active","name"," "))));
        assertEquals("已有标题",current.optString("name"));
    }
    @Test public void historyUsesPinnedThenInstantWithFallbackAndStableTies(){
        JSONArray data=Json.arr(
            Json.obj("id","none","updated_at","bad"),
            Json.obj("id","older","updated_at","2026-09-22T12:00:00Z"),
            Json.obj("id","pinOld","pinned",true,"created_at","2026-09-20"),
            Json.obj("id","fallback","updated_at","invalid","created_at","2026-09-23T13:00:00+08:00"),
            Json.obj("id","new","updated_at","2026-09-23T06:00:00Z"),
            Json.obj("id","pinNew","pinned",true,"updated_at","2026-09-21"),
            Json.obj("id","tie","updated_at","2026-09-23T14:00:00+08:00"),Json.obj("id","none2"));
        assertEquals(List.of("pinNew","pinOld","new","tie","fallback","older","none","none2"),ChatHistory.sorted(data).stream().map(x->x.optString("id")).toList());
    }
    @Test public void bundledPromptsRefreshWithoutRepeatingPrevious()throws Exception{
        String bundled=new String(Files.readAllBytes(Path.of("src/main/assets/welcome-prompts.json")),java.nio.charset.StandardCharsets.UTF_8);
        List<WelcomePrompts.Prompt> all=WelcomePrompts.parse(bundled);assertTrue(all.size()>=6);
        List<WelcomePrompts.Prompt> previous=List.of();Random random=new Random(23);
        for(int i=0;i<100;i++){List<WelcomePrompts.Prompt> next=WelcomePrompts.choose(all,previous,random);assertEquals(3,next.size());assertEquals(3,new HashSet<>(next).size());assertTrue(Collections.disjoint(previous,next));assertTrue(all.containsAll(next));previous=next;}
    }
    @Test public void promptSelectionHandlesSmallOrEmptyCatalog(){
        List<WelcomePrompts.Prompt> all=WelcomePrompts.parse("{\"prompts\":[{\"title\":\"One\",\"prompt\":\"Full text\"}]}");
        assertEquals(all,WelcomePrompts.choose(all,all,new Random()));assertTrue(WelcomePrompts.choose(List.of(),List.of(),new Random()).isEmpty());
    }
    @Test public void loopDescriptionsMatchConsoleAndFallback(){
        assertEquals("标准的受控智能体 Loop。",LoopDescriptions.description(Json.obj("id","default")));
        assertEquals("持续推进一个具体且可验证的目标。",LoopDescriptions.description(Json.obj("id","goal","source","builtin")));
        assertEquals("运行结构化、可持续的多步骤任务。",LoopDescriptions.description(Json.obj("id","mission","source","builtin")));
        assertEquals("中文说明",LoopDescriptions.description(Json.obj("source","plugin","description_i18n",Json.obj("zh-CN","\n**中文说明**\n第二行"),"description","English")));
        assertEquals("回退说明",LoopDescriptions.description(Json.obj("source","custom","description_i18n",Json.obj("zh-CN"," ","zh",""),"description","\n回退说明\n更多内容")));
        assertEquals("暂无模式说明",LoopDescriptions.description(Json.obj("source","plugin")));
    }
}
