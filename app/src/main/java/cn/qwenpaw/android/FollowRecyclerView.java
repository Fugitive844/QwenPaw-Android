package cn.qwenpaw.android;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;

/** Content can grow without changing the RecyclerView's own bounds. */
final class FollowRecyclerView extends RecyclerView {
    Runnable afterLayout;
    FollowRecyclerView(Context context){super(context);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(afterLayout!=null)afterLayout.run();}
}
