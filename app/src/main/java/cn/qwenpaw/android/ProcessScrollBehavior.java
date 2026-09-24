package cn.qwenpaw.android;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.ScrollView;
import java.util.function.BooleanSupplier;

/** Lets process details consume vertical drags until they reach an edge, then hands the gesture to chat. */
final class ProcessScrollBehavior extends ScrollView {
    private final int slop;
    private final Runnable onReadingDrag;
    private float downY,lastY;
    private boolean dragging;
    private final BooleanSupplier following;
    private final int restoredY;
    private boolean laidOut;

    ProcessScrollBehavior(Context context,int scrollBarSize,Runnable onReadingDrag,BooleanSupplier following,int restoredY) {
        super(context);
        this.onReadingDrag=onReadingDrag;
        this.following=following;this.restoredY=restoredY;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setVerticalScrollBarEnabled(true);
        setScrollbarFadingEnabled(false);
        setScrollBarSize(scrollBarSize);
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        super.onLayout(changed,left,top,right,bottom);
        if(following.getAsBoolean())followLatest();
        else if(!laidOut)scrollTo(0,restoredY);
        laidOut=true;
    }

    void followLatest() {
        if(following.getAsBoolean()&&getChildCount()>0)
            scrollTo(0,Math.max(0,getChildAt(0).getBottom()-getHeight()+getPaddingBottom()));
    }

    static boolean keepInner(float fingerDelta, boolean canScrollUp, boolean canScrollDown) {
        if(fingerDelta>0)return canScrollUp;
        if(fingerDelta<0)return canScrollDown;
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        ViewParent parent=getParent();
        int action=event.getActionMasked();
        float step=event.getY()-lastY;
        if(action==MotionEvent.ACTION_DOWN) {
            downY=lastY=event.getY();dragging=false;
            if(parent!=null)parent.requestDisallowInterceptTouchEvent(canScrollVertically(-1)||canScrollVertically(1));
        } else if(action==MotionEvent.ACTION_MOVE) {
            if(!dragging&&Math.abs(event.getY()-downY)>slop){dragging=true;onReadingDrag.run();}
            lastY=event.getY();
        }
        boolean handled=super.dispatchTouchEvent(event);
        // Run after children, which may also request interception during selection.
        if(parent!=null) {
            if(action==MotionEvent.ACTION_MOVE&&dragging)
                parent.requestDisallowInterceptTouchEvent(keepInner(step,canScrollVertically(-1),canScrollVertically(1)));
            else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL) {
                dragging=false;parent.requestDisallowInterceptTouchEvent(false);
            }
        }
        return handled;
    }
}
