package cn.qwenpaw.android;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

/** Offline native chart; missing days are zero-filled, tapping exposes exact values. */
final class UsageChart extends View {
    static final int[] COLORS={0xff216653,0xff437bb4,0xffb37629,0xff986ab8,0xff3c9292,0xffb45e72};
    private final LocalDate start,end;
    private final Map<String,Map<String,Long>> series;
    private final Set<String> hidden;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int selected=-1;
    Consumer<String> selection;
    UsageChart(Context context,LocalDate start,LocalDate end,Map<String,Map<String,Long>> series,Set<String> hidden){super(context);this.start=start;this.end=end;this.series=series;this.hidden=hidden;density=getResources().getDisplayMetrics().density;setContentDescription("用量趋势，点击查看每日数值");setFocusable(true);setClickable(true);}
    private float dp(float v){return v*density;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int days=(int)ChronoUnit.DAYS.between(start,end)+1;float left=dp(46),right=getWidth()-dp(8),top=dp(20),bottom=getHeight()-dp(30);long max=1;
        for(var s:series.entrySet())if(!hidden.contains(s.getKey()))for(long value:s.getValue().values())max=Math.max(max,value);
        paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<=4;i++){float y=top+(bottom-top)*i/4;paint.setColor(Ui.LINE);canvas.drawLine(left,y,right,y,paint);paint.setColor(Ui.MUTED);canvas.drawText(shortNumber(max*(4-i)/4),0,y+dp(3),paint);}
        canvas.drawText(start.toString().substring(5),left,bottom+dp(20),paint);String last=end.toString().substring(5);canvas.drawText(last,right-paint.measureText(last),bottom+dp(20),paint);
        int color=0;for(var s:series.entrySet()){int c=COLORS[color++%COLORS.length];if(hidden.contains(s.getKey()))continue;Path path=new Path();for(int d=0;d<days;d++){float x=days==1?(left+right)/2:left+(right-left)*d/(days-1);long value=s.getValue().getOrDefault(start.plusDays(d).toString(),0L);float y=bottom-(bottom-top)*value/max;if(d==0)path.moveTo(x,y);else path.lineTo(x,y);if(days==1){paint.setColor(c);canvas.drawCircle(x,y,dp(3),paint);}}paint.setColor(c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
        if(selected>=0){float x=days==1?(left+right)/2:left+(right-left)*selected/(days-1);paint.setColor(Ui.MUTED);paint.setStrokeWidth(dp(1));canvas.drawLine(x,top,x,bottom,paint);}
    }
    private String shortNumber(long n){return n>=1000000?String.format(Locale.ROOT,"%.1fM",n/1000000.0):n>=1000?String.format(Locale.ROOT,"%.1fK",n/1000.0):Long.toString(n);}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()==MotionEvent.ACTION_UP){int days=(int)ChronoUnit.DAYS.between(start,end)+1;selected=Math.max(0,Math.min(days-1,Math.round((event.getX()-dp(46))/Math.max(1,getWidth()-dp(54))*(days-1))));performClick();return true;}return event.getAction()==MotionEvent.ACTION_DOWN;}
    @Override public boolean performClick(){super.performClick();if(selected<0)selected=0;String date=start.plusDays(selected).toString();StringBuilder text=new StringBuilder(date);for(var s:series.entrySet())if(!hidden.contains(s.getKey()))text.append('\n').append(s.getKey()).append("：").append(String.format(Locale.CHINA,"%,d",s.getValue().getOrDefault(date,0L)));setContentDescription(text);if(selection!=null)selection.accept(text.toString());invalidate();return true;}
}
