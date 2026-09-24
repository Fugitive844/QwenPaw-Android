package cn.qwenpaw.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.PathParser;
import com.google.android.material.button.MaterialButton;

/** Shared visual tokens and small native components. All dimensions below are dp. */
final class Ui {
    static final int INK=0xff202a26, MUTED=0xff727c76, GREEN=0xff216653, BG=0xfffafbf8,
        LINE=0xffe3e8e1, SOFT=0xfff0f3ee, TINT=0xffe6f0ea, WHITE=Color.WHITE, DANGER=0xffb14940;
    final Context context;
    Ui(Context context){this.context=context;}
    int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String s,int size,int color){TextView t=new TextView(context);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setFontFeatureSettings("kern");return t;}
    TextView heading(String s,int size){TextView t=text(s,size,INK);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    GradientDrawable shape(int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;}
    void gap(LinearLayout parent,int height){parent.addView(new View(context),new LinearLayout.LayoutParams(1,dp(height)));}
    void padding(View v,int h,int vertical){v.setPadding(dp(h),dp(vertical),dp(h),dp(vertical));}
    ImageView icon(String name,int color,int size){ImageView v=new ImageView(context);v.setImageDrawable(new Glyph(name,color));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return v;}
    MaterialButton button(String label,String icon,boolean filled,View.OnClickListener click){
        MaterialButton b=new MaterialButton(context,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setLetterSpacing(0);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setInsetTop(dp(4));b.setInsetBottom(dp(4));b.setCornerRadius(dp(20));b.setStrokeWidth(0);b.setElevation(0);b.setStateListAnimator(null);
        b.setPadding(dp(14),0,dp(14),0);tone(b,filled?GREEN:SOFT,filled?WHITE:INK);
        if(icon!=null){b.setIcon(new Glyph(icon,filled?WHITE:INK));b.setIconSize(dp(18));b.setIconPadding(dp(6));b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);}
        b.setOnClickListener(click);return b;
    }
    void tone(MaterialButton b,int background,int foreground){
        b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{SOFT,background}));
        ColorStateList fg=new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xffa6afa8,foreground});
        b.setTextColor(fg);b.setIconTint(fg);b.setRippleColor(ColorStateList.valueOf(0x18326450));
    }
    MaterialButton iconButton(String label,String icon,View.OnClickListener click){
        MaterialButton b=button("",icon,false,click);tone(b,Color.TRANSPARENT,INK);b.setContentDescription(label);
        b.setIconSize(dp(22));b.setIconPadding(0);b.setPadding(dp(12),0,dp(12),0);b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return b;
    }
    MaterialButton chip(String label,String icon,View.OnClickListener click){MaterialButton b=button(label,icon,false,click);b.setTextSize(12);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setPadding(dp(12),0,dp(12),0);return b;}
    Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x15216653),shape(color,radius,0),shape(WHITE,radius,0));}

    static final class UsageRing extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final double percent;private final int color;
        UsageRing(double percent,int color){this.percent=percent;this.color=color;}
        @Override public void draw(Canvas canvas){Rect b=getBounds();float width=Math.min(b.width(),b.height())/10f;RectF arc=new RectF(b.left+width,b.top+width,b.right-width,b.bottom-width);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(LINE);canvas.drawOval(arc,paint);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);canvas.drawArc(arc,-90,percent<0?40:(float)(percent*3.6),false,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
    }

    /** Consistent 24-unit outline icons, independent of system fonts or emoji glyphs. */
    static final class Glyph extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path;private int color;
        Glyph(String name,int color){this.color=color;path=PathParser.createPathFromPathData(switch(name){
            case "menu"->"M4,7H20 M4,12H15 M4,17H20";
            case "command"->"M8,3Q3,3 3,8Q3,11 8,11H16Q21,11 21,8Q21,3 16,3V16Q16,21 19,21Q24,21 21,16Q21,13 16,13H8Q3,13 3,16Q3,21 8,21Z";
            case "compress"->"M4,4L10,10 M4,10H10V4 M20,20L14,14 M14,20V14H20";
            case "ban"->"M12,3a9,9 0,1 0,0.01,0 M6,6L18,18";
            case "warning"->"M12,3L22,21H2Z M12,9V14 M12,17H12.1";
            case "new"->"M12,4H6Q4,4 4,6V18Q4,20 6,20H18Q20,20 20,18V12 M16,3V11 M12,7H20";
            case "more"->"M5,12h0.1 M12,12h0.1 M19,12h0.1";
            case "arrow"->"M12,19V5 M6,11L12,5L18,11";
            case "down"->"M12,5V19 M6,13L12,19L18,13";
            case "chevron"->"M8,10L12,14L16,10";
            case "right"->"M9,5L16,12L9,19";
            case "plus"->"M12,5V19 M5,12H19";
            case "close"->"M6,6L18,18 M18,6L6,18";
            case "stop"->"M7,7H17V17H7Z";
            case "attach"->"M9,15L16,8Q18,6 16,4Q14,2 12,4L5,11Q1,15 5,19Q9,23 13,19L21,11 M8,12L14,6";
            case "eye"->"M2,12Q12,0 22,12Q12,24 2,12Z M12,9a3,3 0,1 0,0.01,0";
            case "chat"->"M5,4H19Q21,4 21,6V16Q21,18 19,18H9L4,21V18Q2,18 2,16V7Q2,4 5,4Z M7,9H16 M7,13H13";
            case "spark"->"M12,3L14.5,9.5L21,12L14.5,14.5L12,21L9.5,14.5L3,12L9.5,9.5Z";
            case "paw"->"M7,15Q12,9 17,15Q23,23 15,20Q12,19 9,20Q1,23 7,15Z M5,8a1.8,2.5 0,1 0,0.1,0 M10,4a1.7,2.5 0,1 0,0.1,0 M16,4a1.7,2.5 0,1 0,0.1,0 M21,8a1.8,2.5 0,1 0,0.1,0";
            case "shield"->"M12,3L20,6V12Q20,18 12,22Q4,18 4,12V6Z M8,12L11,15L16,9";
            case "check"->"M5,12L10,17L20,6";
            case "loop"->"M4,10Q5,4 12,4Q17,4 20,8 M20,3V8H15 M20,14Q19,20 12,20Q7,20 4,16 M4,21V16H9";
            case "file"->"M13,3H5V21H19V9Z M13,3V9H19 M8,13H16 M8,17H13";
            case "copy"->"M9,8H20V21H9Z M5,16H3V3H14V5";
            case "download"->"M12,3V16 M7,11L12,16L17,11 M4,17V21H20V17";
            case "search"->"M10,3a7,7 0,1 0,0.01,0 M15,15L21,21";
            case "folder"->"M3,6H9L11,8H21V20H3Z";
            case "clock"->"M12,3a9,9 0,1 0,0.01,0 M12,7V12L16,14";
            case "server"->"M4,3H20V10H4Z M4,14H20V21H4Z M7,6.5H7.1 M7,17.5H7.1 M15,6.5H17 M15,17.5H17";
            case "settings"->"M4,6H20 M4,12H20 M4,18H20 M8,3V9 M16,9V15 M10,15V21";
            default->"M12,3L21,12L12,21L3,12Z";
        });paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);}
        @Override public void draw(Canvas canvas){canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);paint.setColor(color);canvas.drawPath(path,paint);canvas.restore();}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}@Override public int getIntrinsicHeight(){return 24;}
    }
}
