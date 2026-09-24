package cn.qwenpaw.android;

import org.json.*;
import java.util.*;

final class WelcomePrompts {
    record Prompt(String title,String text) {}
    static List<Prompt> parse(String json) {
        List<Prompt> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        JSONArray entries=Json.array(Json.object(Json.parse(json)).opt("prompts"));
        for(int i=0;i<entries.length();i++) {
            JSONObject entry=Json.object(entries.opt(i));String title=entry.optString("title"),text=entry.optString("prompt");
            if(!title.isBlank()&&!text.isBlank()&&seen.add(text))result.add(new Prompt(title,text));
        }
        return result;
    }
    static List<Prompt> choose(List<Prompt> all,List<Prompt> previous,Random random) {
        List<Prompt> pool=new ArrayList<>(all);pool.removeAll(previous);Collections.shuffle(pool,random);
        if(pool.size()<Math.min(3,all.size())){List<Prompt> rest=new ArrayList<>(previous);rest.retainAll(all);Collections.shuffle(rest,random);pool.addAll(rest);}
        return new ArrayList<>(pool.subList(0,Math.min(3,pool.size())));
    }
}
