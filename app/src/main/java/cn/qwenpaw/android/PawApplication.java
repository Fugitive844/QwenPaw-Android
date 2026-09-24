package cn.qwenpaw.android;
import android.app.Application;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
public final class PawApplication extends Application {
    public volatile ChatController chat;
    // In-memory only: a new process must check even if the previous one just checked.
    final AtomicBoolean startupUpdateCheckStarted=new AtomicBoolean();
    volatile UpdateRelease requiredUpdate;
    @Override public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            if(chat!=null)try{markCrash();}catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
    synchronized ChatController ensureChat() { if(chat==null)chat=new ChatController(this);return chat; }
    private File crashMarker(){return new File(getFilesDir(),"chat-crashed.flag");}
    boolean needsRecovery(){return crashMarker().exists();}
    void markCrash(){try{crashMarker().createNewFile();}catch(IOException ignored){}}
    void clearCrash(){crashMarker().delete();}
}
