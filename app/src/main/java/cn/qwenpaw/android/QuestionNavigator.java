package cn.qwenpaw.android;

import java.util.*;

/** One navigation entry per user turn, independent of process expansion. */
final class QuestionNavigator {
    record Question(int turn,String messageId,String text) {}

    static List<Question> questions(List<Transcript.Row> rows) {
        Map<Integer,List<Transcript.Row>> turns=new LinkedHashMap<>();
        for(Transcript.Row row:rows)if(row.role.equals("user"))
            turns.computeIfAbsent(row.turn,k->new ArrayList<>()).add(row);
        ArrayList<Question> result=new ArrayList<>();
        for(List<Transcript.Row> turn:turns.values()) {
            ArrayList<String> parts=new ArrayList<>();
            for(Transcript.Row row:turn) {
                if(row.kind.equals("text")&&!row.text.isBlank())parts.add(row.text);
                else if(row.kind.equals("file"))parts.add("[附件] "+row.title);
            }
            Transcript.Row first=turn.get(0);
            result.add(new Question(first.turn,first.messageId,parts.isEmpty()?"[无文字提问]":String.join("\n\n",parts)));
        }
        return result;
    }

    static int position(List<Transcript.Row> visible,Question question) {
        // Resolve against the current projection: stream updates and folding change positions.
        for(int i=0;i<visible.size();i++) {
            Transcript.Row row=visible.get(i);
            if(row.role.equals("user")&&!question.messageId().isEmpty()&&row.messageId.equals(question.messageId()))return i;
        }
        // Canonical history can replace an optimistic message ID within the same conversation.
        boolean sameQuestion=questions(visible).stream().anyMatch(q->q.turn()==question.turn()&&q.text().equals(question.text()));
        if(!sameQuestion)return -1;
        for(int i=0;i<visible.size();i++)if(visible.get(i).role.equals("user")&&visible.get(i).turn==question.turn())return i;
        return -1;
    }
}
