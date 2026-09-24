package cn.qwenpaw.android;

import org.junit.Test;
import org.json.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class AgentDocumentTest {
    @Test public void frontmatterIsRemovedOnlyFromStartAndSourceRemainsUnchanged(){
        String raw="\uFEFF---\r\nsummary: Example\r\nread_when:\r\n - setup\r\n---\r\n# 正文\r\n\r\n---\r\n第二段";
        assertEquals("# 正文\r\n\r\n---\r\n第二段",AgentDocument.markdown(raw));
        assertTrue(raw.startsWith("\uFEFF---"));assertEquals("# 标题\n---\n正文",AgentDocument.markdown("# 标题\n---\n正文"));
        assertEquals("---\n未闭合内容",AgentDocument.markdown("---\n未闭合内容"));
    }
    @Test public void absentPromptConfigIsUnknownAndEmptyConfigMeansNotSelected(){
        var kind=AgentDocument.find("AGENTS.md");assertEquals("对话指引状态未获取",AgentDocument.status(kind,null,null));
        assertEquals("未选为对话指引",AgentDocument.status(kind,new JSONArray(),null));
        assertEquals("已选为对话指引",AgentDocument.status(kind,Json.arr("AGENTS.md"),null));
    }
    @Test public void heartbeatExistenceDoesNotImplyEnabledAndMayAlsoBeInPrompt(){
        var kind=AgentDocument.find("HEARTBEAT.md");assertEquals("心跳状态未获取",AgentDocument.status(kind,null,new JSONObject()));
        assertEquals("心跳未开启",AgentDocument.status(kind,null,Json.obj("enabled",false)));
        assertEquals("心跳已开启 · 间隔 30m · 08:00–22:00 · 同时作为对话指引",AgentDocument.status(kind,Json.arr("HEARTBEAT.md"),Json.obj("enabled",true,"every","30m","activeHours",Json.obj("start","08:00","end","22:00"))));
    }
    @Test public void fileLookupCannotResolveAnotherWorkspacePath(){
        JSONArray files=Json.arr(Json.obj("filename","nested/PROFILE.md"),Json.obj("filename","PROFILE.md","size",4));
        assertEquals(4,AgentDocument.metadata(files,"PROFILE.md").optInt("size"));assertNull(AgentDocument.metadata(files,"SOUL.md"));assertNull(AgentDocument.metadata(null,"PROFILE.md"));
        assertThrows(IllegalArgumentException.class,()->AgentDocument.find("../AGENTS.md"));
    }
    @Test public void invalidContentIsNotSilentlyPresentedAsEmpty()throws Exception{
        assertEquals("",AgentDocument.content(Json.obj("content","")));assertThrows(IOException.class,()->AgentDocument.content(Json.obj("content",JSONObject.NULL)));assertThrows(IOException.class,()->AgentDocument.content(new JSONObject()));
    }
}
