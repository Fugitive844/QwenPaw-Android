package cn.qwenpaw.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProcessScrollBehaviorTest {
    @Test public void upwardFingerDragStaysInsideWhileMoreContentExistsBelow() {
        assertTrue(ProcessScrollBehavior.keepInner(-10,false,true));
        assertFalse(ProcessScrollBehavior.keepInner(-10,true,false));
    }

    @Test public void downwardFingerDragStaysInsideWhileMoreContentExistsAbove() {
        assertTrue(ProcessScrollBehavior.keepInner(10,true,false));
        assertFalse(ProcessScrollBehavior.keepInner(10,false,true));
    }

    @Test public void stationaryMotionDoesNotPrematurelyHandOffGesture() {
        assertTrue(ProcessScrollBehavior.keepInner(0,false,false));
    }
}
