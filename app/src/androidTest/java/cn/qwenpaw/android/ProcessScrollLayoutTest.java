package cn.qwenpaw.android;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProcessScrollLayoutTest {
    private ProcessScrollBehavior box(AtomicBoolean following,int saved) {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProcessScrollBehavior box=new ProcessScrollBehavior(context,6,()->following.set(false),following::get,saved);
        box.addView(new View(context),new FrameLayout.LayoutParams(300,1000));
        return box;
    }
    private void layout(ProcessScrollBehavior box,int contentHeight) {
        box.getChildAt(0).getLayoutParams().height=contentHeight;
        box.getChildAt(0).requestLayout();
        box.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY));
        box.layout(0,0,300,200);
    }
    @Test public void growingOutputFollowsThenPausesAndResumes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            AtomicBoolean following=new AtomicBoolean(true);ProcessScrollBehavior box=box(following,0);
            layout(box,1000);assertEquals(800,box.getScrollY());
            layout(box,1400);assertEquals(1200,box.getScrollY());
            following.set(false);box.scrollTo(0,180);
            layout(box,1600);assertEquals(180,box.getScrollY());
            following.set(true);box.followLatest();assertEquals(1400,box.getScrollY());
            layout(box,1800);assertEquals(1600,box.getScrollY());
        });
    }
    @Test public void recycledOutputRestoresPausedReadingPosition() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            ProcessScrollBehavior box=box(new AtomicBoolean(false),240);
            layout(box,1400);assertEquals(240,box.getScrollY());
        });
    }
}
