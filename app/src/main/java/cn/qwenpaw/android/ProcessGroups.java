package cn.qwenpaw.android;

import java.util.*;

/** One disclosure per turn; visible events always retain transcript order. */
final class ProcessGroups {
    static boolean process(Transcript.Row row){return row.kind.equals("thinking")||row.kind.equals("tool");}
    static String key(int turn){return "process:"+turn;}
    private static boolean assistantText(Transcript.Row row){return row.kind.equals("text")&&row.role.equals("assistant");}
    private static String messageKey(Transcript.Row row){return row.messageId.isEmpty()?row.id:row.messageId;}
    static List<Transcript.Row> project(List<Transcript.Row> source,boolean running,Map<String,Boolean> expanded){
        Map<Integer,List<Transcript.Row>> groups=new LinkedHashMap<>();int lastTurn=-1;
        Map<Integer,String> finalMessages=new HashMap<>();
        for(Transcript.Row r:source){lastTurn=Math.max(lastTurn,r.turn);if(assistantText(r))finalMessages.put(r.turn,messageKey(r));}
        Set<Integer> laterProcess=new HashSet<>();
        for(int i=source.size()-1;i>=0;i--){
            Transcript.Row r=source.get(i);r.outputPhase="";
            if(process(r))laterProcess.add(r.turn);
            else if(assistantText(r)){
                boolean latest=messageKey(r).equals(finalMessages.get(r.turn));
                boolean live=running&&r.turn==lastTurn;
                r.outputPhase=!latest||(live&&laterProcess.contains(r.turn))?"intermediate":live?"streaming":"final";
            }
        }
        Set<String> collapsed=new HashSet<>();
        for(Transcript.Row r:source){
            boolean finished=!(running&&r.turn==lastTurn);
            if(process(r)||(finished&&assistantText(r)&&!messageKey(r).equals(finalMessages.get(r.turn)))){
                groups.computeIfAbsent(r.turn,k->new ArrayList<>()).add(r);collapsed.add(r.id);
            }
        }
        ArrayList<Transcript.Row> output=new ArrayList<>();Set<Integer> inserted=new HashSet<>();
        for(Transcript.Row r:source){
            if(!collapsed.contains(r.id)){output.add(r);continue;}
            List<Transcript.Row> children=groups.get(r.turn);boolean done=!(running&&r.turn==lastTurn);
            boolean open=expanded.getOrDefault(key(r.turn),!done);
            if(inserted.add(r.turn)){
                Transcript.Row group=new Transcript.Row(key(r.turn),"process","assistant");group.turn=r.turn;group.done=done;
                long tools=children.stream().filter(x->x.kind.equals("tool")).count();long thoughts=children.stream().filter(x->x.kind.equals("thinking")).count();
                long texts=children.stream().filter(ProcessGroups::assistantText).count();
                group.interrupted=children.stream().anyMatch(x->x.interrupted);
                group.title=done?(group.interrupted?"查看过程 · 已中断":"查看过程"):"正在处理";
                ArrayList<String> counts=new ArrayList<>();
                if(thoughts>0)counts.add(thoughts+" 段思考");if(tools>0)counts.add(tools+" 次工具调用");if(texts>0)counts.add(texts+" 段中间输出");
                group.text=String.join(" · ",counts);
                group.params=Boolean.toString(open);output.add(group);
            }
            // The disclosure controls visibility, never the location of later Loop events.
            if(open)output.add(r);
        }
        return output;
    }
    static void freeze(List<Transcript.Row> rows,boolean running,Map<String,Boolean> expanded){
        for(Transcript.Row r:project(rows,running,expanded))if(r.kind.equals("process"))expanded.putIfAbsent(r.id,Boolean.parseBoolean(r.params));
    }
}
