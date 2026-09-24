package cn.qwenpaw.android;

import io.noties.markwon.*;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/** Shared renderer for chat messages and attachment previews. */
final class ChatMarkdown {
    private ChatMarkdown() {}
    static Markwon create(MainActivity activity) {
        return Markwon.builder(activity).usePlugin(TablePlugin.create(activity))
            .usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(activity))
            .usePlugin(new AbstractMarkwonPlugin(){
                @Override public void configureConfiguration(MarkwonConfiguration.Builder builder){
                    builder.linkResolver((view,link)->activity.openLink(link));
                }
            }).build();
    }
}
