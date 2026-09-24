package cn.qwenpaw.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/** Update screen in a separate process, reachable from the app shortcut or crash recovery. */
public final class UpdateRecoveryActivity extends AppCompatActivity {
    private AppUpdater updater;
    private boolean checked;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        updater=new AppUpdater(this);
        Ui ui=new Ui(this);
        LinearLayout page=ui.column();page.setGravity(Gravity.CENTER);page.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));
        page.setBackgroundColor(Ui.BG);
        TextView title=ui.heading("QwenPaw 检查更新",22);title.setGravity(Gravity.CENTER);page.addView(title);
        ui.gap(page,12);
        TextView hint=ui.text("聊天界面无法打开时，也可以在这里检查、下载并安装更新。",14,Ui.MUTED);
        hint.setGravity(Gravity.CENTER);page.addView(hint);
        ui.gap(page,24);
        Button check=ui.button("检查更新 · "+updater.version(),"down",true,v->updater.check(true));
        page.addView(check,new LinearLayout.LayoutParams(-1,ui.dp(56)));
        ui.gap(page,12);
        Button retry=ui.button("尝试打开聊天","right",false,v->{
            ((PawApplication)getApplication()).clearCrash();
            startActivity(new Intent(this,MainActivity.class));finish();
        });
        page.addView(retry,new LinearLayout.LayoutParams(-1,ui.dp(56)));
        setContentView(page);
    }

    @Override protected void onResume() {
        super.onResume();updater.resume();
        if(!checked){checked=true;getWindow().getDecorView().post(()->updater.check(true));}
    }

    @Override protected void onDestroy(){updater.close();super.onDestroy();}
}
