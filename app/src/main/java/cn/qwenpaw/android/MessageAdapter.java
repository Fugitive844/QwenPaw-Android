package cn.qwenpaw.android;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import com.google.android.material.button.MaterialButton;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import io.noties.markwon.*;
import java.util.*;
import java.util.regex.*;

public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
    private final MainActivity activity;
    private final ChatController c;
    private final Markwon markdown;
    private List<Transcript.Row> rows=new ArrayList<>();
    private final Map<String,Integer> processScrollPositions=new HashMap<>();
    private boolean following=true;
    private static final Pattern MERMAID=Pattern.compile("(?m)^\\s*```mermaid[^\\n]*\\n([\\s\\S]*?)^\\s*```\\s*$");
    public MessageAdapter(MainActivity activity,ChatController controller) {
        this.activity=activity;c=controller;
        markdown=ChatMarkdown.create(activity);
    }
    public void update(List<Transcript.Row> source) {
        List<Transcript.Row> next=ProcessGroups.project(source,c.running,c.expanded);
        next.add(new Transcript.Row("list:tail","tail","system"));
        List<Transcript.Row> previous=rows;
        boolean followChanged=following!=c.autoFollow;following=c.autoFollow;
        Set<String> liveIds=new HashSet<>();for(Transcript.Row row:source)liveIds.add(row.id);
        processScrollPositions.keySet().retainAll(liveIds);
        DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
            public int getOldListSize(){return previous.size();}public int getNewListSize(){return next.size();}
            public boolean areItemsTheSame(int o,int n){return previous.get(o).id.equals(next.get(n).id);}
            public boolean areContentsTheSame(int o,int n){return !(followChanged&&ProcessGroups.process(next.get(n)))&&previous.get(o).signature().equals(next.get(n).signature());}
        });rows=next;diff.dispatchUpdatesTo(this);
    }
    public List<Transcript.Row> rows(){return rows;}
    void followProcessDetails(ViewGroup group){
        if(!c.autoFollow)return;
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);
            if(child instanceof ProcessScrollBehavior scroll)scroll.followLatest();
            else if(child instanceof ViewGroup nested)followProcessDetails(nested);
        }
    }
    @Override public int getItemCount(){return rows.size();}
    @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder(activity.column());}
    @Override public void onViewRecycled(Holder holder){destroyDiagrams(holder.box);holder.box.removeAllViews();}
    private void destroyDiagrams(ViewGroup group){for(int i=0;i<group.getChildCount();i++){View v=group.getChildAt(i);if(v instanceof DiagramView d)d.destroy();else if(v instanceof ViewGroup g)destroyDiagrams(g);}}
    @Override public void onBindViewHolder(Holder h,int position) {
        destroyDiagrams(h.box);h.box.removeAllViews();Transcript.Row r=rows.get(position);
        if(r.kind.equals("tail")){
            h.box.setPadding(0,0,0,0);h.box.setBackground(null);h.box.setOnLongClickListener(null);h.box.setOnClickListener(null);
            h.box.setLayoutParams(new RecyclerView.LayoutParams(-1,1));return;
        }
        Ui ui=activity.design;boolean user=r.role.equals("user");
        h.box.setPadding(activity.dp(user?16:4),activity.dp(12),activity.dp(user?16:4),activity.dp(12));
        RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(activity.dp(user?40:0),activity.dp(6),0,activity.dp(10));h.box.setLayoutParams(lp);
        h.box.setBackground(user?ui.shape(Ui.TINT,22,0):null);h.box.setOnLongClickListener(null);h.box.setOnClickListener(null);h.box.setClickable(false);
        if(r.kind.equals("process")){
            h.box.setPadding(0,0,0,0);boolean open=Boolean.parseBoolean(r.params);
            MaterialButton toggle=ui.button(r.title+"  ·  "+r.text,open?"chevron":"right",false,v->{c.pauseFollow();c.expanded.put(r.id,!open);update(c.transcript.rows());activity.followChanged();});
            ui.tone(toggle,Color.TRANSPARENT,Ui.MUTED);toggle.setTextSize(12);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setIconGravity(MaterialButton.ICON_GRAVITY_START);toggle.setSingleLine(true);toggle.setEllipsize(TextUtils.TruncateAt.END);toggle.setContentDescription((open?"收起本轮过程":"展开本轮过程"));h.box.addView(toggle,new LinearLayout.LayoutParams(-1,activity.dp(48)));return;
        }
        if(r.kind.equals("file")) {
            h.box.setPadding(activity.dp(14),activity.dp(12),activity.dp(8),activity.dp(12));h.box.setBackground(ui.shape(Color.WHITE,20,Ui.LINE));
            LinearLayout row=ui.row();ImageView file=ui.icon("file",Ui.GREEN,44);file.setPadding(activity.dp(10),activity.dp(10),activity.dp(10),activity.dp(10));file.setBackground(ui.shape(Ui.TINT,12,0));row.addView(file);
            LinearLayout copy=ui.column();TextView title=ui.heading(r.title,14);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);copy.addView(title);ui.gap(copy,6);copy.addView(ui.text(user?"你发送的文件":"智能体发来的文件",11,Ui.MUTED));LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.leftMargin=activity.dp(12);row.addView(copy,copyParams);
            if(AttachmentPreview.kind(r.title)!=AttachmentPreview.Kind.UNSUPPORTED)
                row.addView(ui.button("预览",null,false,v->activity.previewFile(r.url,r.title)));
            row.addView(ui.iconButton("保存文件："+r.title,"download",v->activity.saveFile(r.url,r.title)));h.box.addView(row);return;
        }
        if(r.kind.equals("tool") || r.kind.equals("thinking")) {
            h.box.setPadding(activity.dp(8),0,activity.dp(8),0);h.box.setBackground(ui.shape(Ui.SOFT,16,0));
            boolean open=c.expanded.getOrDefault(r.id,!r.done);
            MaterialButton toggle=ui.button((r.kind.equals("thinking")?"思考过程":r.title)+(r.interrupted?" · 已中断":r.failed?" · 失败":r.done?"":" · 进行中"),r.kind.equals("thinking")?"spark":"settings",false,v->{
                c.pauseFollow();c.expanded.put(r.id,!open);if(h.getBindingAdapterPosition()!=RecyclerView.NO_POSITION)notifyItemChanged(h.getBindingAdapterPosition());activity.followChanged();
            });ui.tone(toggle,Color.TRANSPARENT,Ui.MUTED);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setIconGravity(MaterialButton.ICON_GRAVITY_START);toggle.setTextSize(12);toggle.setSingleLine(true);toggle.setEllipsize(TextUtils.TruncateAt.END);toggle.setContentDescription((open?"收起":"展开")+toggle.getText());
            LinearLayout header=ui.row();header.addView(toggle,new LinearLayout.LayoutParams(0,activity.dp(48),1));ImageView arrow=ui.icon(open?"chevron":"right",Ui.MUTED,16);header.addView(arrow);h.box.addView(header);
            if(!open)return;
            ProcessScrollBehavior scroll=new ProcessScrollBehavior(activity,activity.dp(6),this::pauseReading,()->c.autoFollow,processScrollPositions.getOrDefault(r.id,0));scroll.setFillViewport(false);scroll.setContentDescription("思考或工具详情，可在内容区上下滑动");
            scroll.setOnScrollChangeListener((view,x,y,oldX,oldY)->processScrollPositions.put(r.id,y));
            TextView content=activity.text((r.params.isEmpty()?"":r.params+"\n\n")+r.text,12,MainActivity.MUTED);content.setTypeface(Typeface.MONOSPACE);content.setPadding(activity.dp(12),activity.dp(4),activity.dp(12),activity.dp(12));content.setLineSpacing(activity.dp(4),1);
            content.setTextIsSelectable(true);scroll.addView(content);
            watchSelection(content);
            h.box.addView(scroll,new LinearLayout.LayoutParams(-1,activity.dp(220)));return;
        }
        boolean intermediate=r.outputPhase.equals("intermediate");
        if(intermediate){
            h.box.setPadding(activity.dp(14),activity.dp(10),activity.dp(14),activity.dp(10));h.box.setBackground(ui.shape(Ui.SOFT,14,Ui.LINE));
            LinearLayout heading=ui.row();heading.addView(ui.icon("chat",Ui.MUTED,16));TextView label=ui.text("中间输出",11,Ui.MUTED);label.setPadding(activity.dp(6),0,0,0);heading.addView(label);h.box.addView(heading);ui.gap(h.box,6);
        }else if(!user){
            LinearLayout author=ui.row();ImageView mark=ui.icon("paw",Ui.GREEN,24);author.addView(mark);TextView name=ui.heading("QwenPaw",12);name.setPadding(activity.dp(8),0,0,0);author.addView(name);
            if(!r.outputPhase.isEmpty()){TextView phase=ui.text(r.outputPhase.equals("final")?"最终回复":"正在回复",11,Ui.GREEN);phase.setPadding(activity.dp(8),activity.dp(4),activity.dp(8),activity.dp(4));phase.setBackground(ui.shape(Ui.TINT,8,0));LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(-2,-2);badge.leftMargin=activity.dp(8);author.addView(phase,badge);}
            h.box.addView(author);ui.gap(h.box,12);
        }
        Matcher matcher=MERMAID.matcher(r.text);int end=0;
        while(matcher.find()) {
            markdown(h.box,r.text.substring(end,matcher.start()),intermediate);String source=matcher.group(1);
            if(r.done && source.length()<=50000) {
                LinearLayout diagramCard=ui.column();diagramCard.setBackground(ui.shape(Color.WHITE,18,Ui.LINE));diagramCard.setClipToOutline(true);ui.gap(diagramCard,8);DiagramView diagram=new DiagramView(activity,source);diagramCard.addView(diagram,new LinearLayout.LayoutParams(-1,activity.dp(250)));
                MaterialButton zoom=ui.button("放大图表 / 查看源码","search",false,v->activity.diagram(source));ui.tone(zoom,Color.TRANSPARENT,Ui.GREEN);diagramCard.addView(zoom);h.box.addView(diagramCard);ui.gap(h.box,8);
            } else markdown(h.box,"```mermaid\n"+source+"\n```",intermediate);
            end=matcher.end();
        }
        markdown(h.box,r.text.substring(end),intermediate);
        if(!user && r.done&&!intermediate){LinearLayout actions=ui.row();MaterialButton copy=ui.iconButton("复制回复","copy",v->activity.copy(r.text));ui.tone(copy,Color.TRANSPARENT,Ui.MUTED);actions.addView(copy);h.box.addView(actions);}
        h.box.setOnLongClickListener(v->{activity.copy(r.text);return true;});
    }
    private void markdown(LinearLayout parent,String source,boolean intermediate){
        if(source.isBlank())return;TextView text=activity.text("",intermediate?13:15,intermediate?0xff52615a:MainActivity.INK);text.setTextIsSelectable(true);text.setLinkTextColor(Ui.GREEN);text.setLineSpacing(activity.dp(intermediate?3:5),1);text.setPadding(0,activity.dp(2),0,activity.dp(2));markdown.setMarkdown(text,source);
        watchSelection(text);parent.addView(text);
    }
    private void pauseReading(){c.pauseFollow();activity.followChanged();}
    private void watchSelection(TextView text){text.setCustomSelectionActionModeCallback(new ActionMode.Callback(){
        public boolean onCreateActionMode(ActionMode mode,Menu menu){pauseReading();return true;}
        public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}
        public boolean onActionItemClicked(ActionMode mode,MenuItem item){return false;}
        public void onDestroyActionMode(ActionMode mode){}
    });}
    static final class Holder extends RecyclerView.ViewHolder {final LinearLayout box;Holder(LinearLayout box){super(box);this.box=box;}}
}
