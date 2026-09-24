package cn.qwenpaw.android;

/** Main-thread turn identity and stop intent, independent of Android/network timing. */
final class ChatTurn {
    private long revision;
    private boolean active, stopRequested, stopPending, stopAccepted, serverStopped;
    long begin() {revision++;active=true;stopRequested=false;stopPending=false;stopAccepted=false;serverStopped=false;return revision;}
    void reset() {revision++;active=false;stopRequested=false;stopPending=false;stopAccepted=false;serverStopped=false;}
    long id() {return revision;}
    boolean current(long id) {return active && revision==id;}
    boolean active() {return active;}
    boolean stopRequested() {return active && stopRequested;}
    boolean stopPending() {return active && stopPending;}
    boolean stopping() {return active && (stopPending || stopAccepted);}
    boolean serverStopped() {return serverStopped;}
    boolean requestStop() {
        if(!active || stopping())return false;
        stopRequested=true;stopPending=true;return true;
    }
    void acceptStop(long id,boolean stopped) {if(current(id)){stopPending=false;stopAccepted=true;serverStopped|=stopped;}}
    void failStop(long id) {if(current(id)){stopPending=false;stopAccepted=false;}}
    boolean canReconnect(long id) {return current(id) && !stopRequested;}
    boolean canFinishStop(long id,String status,boolean streamOpen,boolean graceExpired) {
        return current(id) && stopRequested && !stopPending && status.equals("idle")
                && (!streamOpen || (serverStopped && graceExpired));
    }
    void finish(long id) {if(current(id)){active=false;stopPending=false;stopAccepted=false;}}
}
