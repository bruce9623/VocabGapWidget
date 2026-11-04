package tw.study.vocabgap;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

/** Native Android home-screen AppWidget (not a lock-screen widget). */
public final class WordWidget extends AppWidgetProvider {
    private static final String PREFIX = "tw.study.vocabgap.";
    private static final String REVEAL = PREFIX + "REVEAL";
    private static final String RATE = PREFIX + "RATE_";
    private static final String PREFS = "widget_state";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static PendingIntent broadcast(Context ctx, String action, int requestCode) {
        Intent intent = new Intent(ctx, WordWidget.class).setAction(action);
        return PendingIntent.getBroadcast(ctx,requestCode,intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,WordWidget.class));
        if (ids.length == 0) return;
        RemoteViews views=makeViews(context);
        manager.updateAppWidget(ids,views);
    }
    public static void resetAfterImport(Context context) {
        prefs(context).edit().remove("current_word_id").putBoolean("revealed",false).apply();
        updateAll(context);
    }
    private static RemoteViews makeViews(Context context) {
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget);
        WordDatabase db=WordDatabase.open(context);
        try {
            SharedPreferences p=prefs(context);
            long id=p.getLong("current_word_id",-1);
            WordDatabase.Word word=id==-1?null:db.word(id);
            if (word==null || word.level>5) {
                word=db.nextWidget();
                if (word!=null) {
                    p.edit().putLong("current_word_id",word.id).putBoolean("revealed",false).apply();
                }
            }
            if (word==null) {
                views.setTextViewText(R.id.widget_label,"VOCAB · 空字庫");
                views.setTextViewText(R.id.widget_word,"尚無單字");
                views.setTextViewText(R.id.widget_meaning,"打開 App 匯入 CSV");
                views.setViewVisibility(R.id.widget_reveal,View.GONE);
                views.setViewVisibility(R.id.widget_actions,View.GONE);
            } else {
                boolean revealed=p.getBoolean("revealed",false);
                boolean due = db.nextReview(WordDatabase.EN,5)!=null;
                views.setTextViewText(R.id.widget_label,"VOCAB · Lv."+word.level+
                        (due ? " · 到期複習" : " · 隨手練習"));
                views.setTextViewText(R.id.widget_word,word.word);
                views.setTextViewText(R.id.widget_meaning,
                        revealed ? word.meanings : "先在心中想出意思");
                views.setViewVisibility(R.id.widget_reveal,revealed?View.GONE:View.VISIBLE);
                views.setViewVisibility(R.id.widget_actions,revealed?View.VISIBLE:View.GONE);
            }
            views.setOnClickPendingIntent(R.id.widget_reveal,broadcast(context,REVEAL,1));
            views.setOnClickPendingIntent(R.id.widget_again,broadcast(context,RATE+1,21));
            views.setOnClickPendingIntent(R.id.widget_hard,broadcast(context,RATE+2,22));
            views.setOnClickPendingIntent(R.id.widget_good,broadcast(context,RATE+3,23));
            views.setOnClickPendingIntent(R.id.widget_easy,broadcast(context,RATE+4,24));
            Intent open=new Intent(context,MainActivity.class).putExtra("from","widget");
            views.setOnClickPendingIntent(R.id.widget_word,PendingIntent.getActivity(context,44,open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return views;
        } finally { db.close(); }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context,intent);
        String action=intent==null?null:intent.getAction();
        if (REVEAL.equals(action)) {
            prefs(context).edit().putBoolean("revealed",true).apply();
            updateAll(context);
        } else if (action!=null && action.startsWith(RATE)) {
            if (!prefs(context).getBoolean("revealed",false)) return;
            int grade;
            try { grade=Integer.parseInt(action.substring(RATE.length())); }
            catch (NumberFormatException e) { return; }
            if (grade<1 || grade>4) return;
            long id=prefs(context).getLong("current_word_id",-1);
            WordDatabase db=WordDatabase.open(context);
            try {
                WordDatabase.Word word=db.word(id);
                if (word!=null) db.rate(word,WordDatabase.EN,grade);
            } finally { db.close(); }
            resetAfterImport(context);
        }
    }
}
