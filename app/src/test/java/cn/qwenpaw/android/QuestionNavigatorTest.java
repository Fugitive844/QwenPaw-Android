package cn.qwenpaw.android;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class QuestionNavigatorTest {
    private Transcript history(){
        org.json.JSONArray messages=Json.arr();
        for(int i=0;i<10;i++){
            messages.put(Json.obj("id","u"+i,"role","user","content","第 "+(i+1)+" 个问题"));
            messages.put(Json.obj("id","t"+i,"role","assistant","type","reasoning","content","思考"));
            messages.put(Json.obj("id","a"+i,"role","assistant","content","回答"));
        }
        Transcript transcript=new Transcript();transcript.history(messages);return transcript;
    }
    @Test public void fourthQuestionResolvesAfterProcessExpansionChangesPositions(){
        Transcript t=history();List<QuestionNavigator.Question> questions=QuestionNavigator.questions(t.rows());
        assertEquals(10,questions.size());assertEquals("第 4 个问题",questions.get(3).text());
        Map<String,Boolean> expanded=new HashMap<>();List<Transcript.Row> collapsed=ProcessGroups.project(t.rows(),false,expanded);
        int before=QuestionNavigator.position(collapsed,questions.get(3));
        expanded.put("process:0",true);expanded.put("process:1",true);
        List<Transcript.Row> visible=ProcessGroups.project(t.rows(),false,expanded);
        int after=QuestionNavigator.position(visible,questions.get(3));
        assertEquals(before+2,after);assertEquals("u3",visible.get(after).messageId);
    }
    @Test public void combinesUserBlocksAndAttachmentsButNotAssistantFiles(){
        Transcript t=new Transcript();t.history(Json.arr(
            Json.obj("id","u","role","user","content",Json.arr(Json.obj("type","text","text","第一段"),
                Json.obj("type","file","filename","input.md","file_url","/input.md"),Json.obj("type","text","text","第二段"))),
            Json.obj("id","a","role","assistant","content",Json.arr(Json.obj("type","file","filename","result.md","file_url","/result.md")))));
        List<QuestionNavigator.Question> questions=QuestionNavigator.questions(t.rows());
        assertEquals(1,questions.size());assertEquals("第一段\n\n[附件] input.md\n\n第二段",questions.get(0).text());
        assertEquals(0,QuestionNavigator.position(t.rows(),questions.get(0)));
    }
    @Test public void attachmentOnlyAndEmptyUserMessagesStillHaveAnchors(){
        Transcript t=new Transcript();t.history(Json.arr(
            Json.obj("id","u1","role","user","content",Json.arr(Json.obj("type","image","filename","图片.png","image_url","/image.png"))),
            Json.obj("id","u2","role","user","content",Json.arr())));
        List<QuestionNavigator.Question> questions=QuestionNavigator.questions(t.rows());
        assertEquals(2,questions.size());assertEquals("[附件] 图片.png",questions.get(0).text());
        assertEquals("[无文字提问]",questions.get(1).text());assertEquals(1,QuestionNavigator.position(t.rows(),questions.get(1)));
    }
    @Test public void longQuestionRetainsFullTextAndCanonicalIdCanChange(){
        String full="长问题\n".repeat(500);Transcript t=new Transcript();t.addUser("temporary",Json.arr(Json.obj("type","text","text",full)));
        QuestionNavigator.Question question=QuestionNavigator.questions(t.rows()).get(0);assertEquals(full,question.text());
        t.history(Json.arr(Json.obj("id","persisted","role","user","content",full)));
        assertEquals(0,QuestionNavigator.position(t.rows(),question));
        t.history(Json.arr(Json.obj("id","other","role","user","content","已替换的问题")));
        assertEquals(-1,QuestionNavigator.position(t.rows(),question));
        assertEquals(-1,QuestionNavigator.position(List.of(),question));
    }
}
