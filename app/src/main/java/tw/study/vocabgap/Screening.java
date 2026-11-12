package tw.study.vocabgap;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;

final class Screening {
    private final Activity activity;
    private final WordDatabase db;
    private final Runnable refresh;
    private int maxLevel = 3;
    private String direction = WordDatabase.EN;

    Screening(Activity activity, WordDatabase db, Runnable refresh) {
        this.activity = activity;
        this.db = db;
        this.refresh = refresh;
    }

    void menu() {
        new AlertDialog.Builder(activity).setTitle("選擇範圍")
            .setItems(new String[]{"Lv.1–3", "Lv.1–4", "常用語塊填空"}, (dialog, i) -> {
                if (i == 2) { chunk(); return; }
                maxLevel = i == 0 ? 3 : 4;
                new AlertDialog.Builder(activity).setTitle("練習方式")
                    .setItems(new String[]{"英→中批次篩選", "中→英批次篩選", "抽查英→中已會字", "抽查中→英已會字"}, (d, n) -> {
                        direction = n % 2 == 0 ? WordDatabase.EN : WordDatabase.ZH;
                        if (n < 2) batch(); else audit();
                    }).show();
            }).show();
    }

    private ArrayList<WordDatabase.Word> words(boolean known) {
        String sql = "SELECT w.id,w.word,w.level,w.meanings FROM words w LEFT JOIN cards c " +
            "ON c.word_id=w.id AND c.direction=? WHERE w.level<=? AND " +
            (known ? "c.last_grade>=3 ORDER BY RANDOM() LIMIT 1" : "c.word_id IS NULL ORDER BY w.level,w.id LIMIT 12");
        ArrayList<WordDatabase.Word> result = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{direction, String.valueOf(maxLevel)})) {
            while (c.moveToNext()) result.add(new WordDatabase.Word(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3)));
        }
        return result;
    }
    private String prompt(WordDatabase.Word w) { return direction.equals(WordDatabase.EN) ? w.word : w.meanings; }
    private void notice(String text) { new AlertDialog.Builder(activity).setMessage(text).setPositiveButton("完成",null).show(); }

    private void batch() {
        ArrayList<WordDatabase.Word> list = words(false);
        if (list.isEmpty()) { notice("這個方向已全部掃描"); return; }
        String[] labels = new String[list.size()];
        boolean[] marked = new boolean[list.size()];
        for (int i=0;i<labels.length;i++) labels[i]=prompt(list.get(i));
        new AlertDialog.Builder(activity).setTitle("勾選想不出或模糊的字")
            .setMultiChoiceItems(labels, marked, (d,i,on) -> marked[i]=on)
            .setNegativeButton("取消",null).setPositiveButton("儲存，其餘記為會",(d,n) -> {
                SQLiteDatabase sql=db.getWritableDatabase();
                sql.beginTransaction();
                try {
                    for (int i=0;i<labels.length;i++) db.rate(list.get(i),direction,marked[i]?1:3);
                    sql.setTransactionSuccessful();
                } finally { sql.endTransaction(); }
                refresh.run();
                batch();
            }).show();
    }

    private void audit() {
        ArrayList<WordDatabase.Word> list=words(true);
        if (list.isEmpty()) { notice("目前沒有可抽查的已會字"); return; }
        WordDatabase.Word w=list.get(0);
        quiz(prompt(w),direction.equals(WordDatabase.EN)?w.meanings:w.word,w,direction,this::audit);
    }

    private void quiz(String question,String answer,WordDatabase.Word w,String key,Runnable next) {
        new AlertDialog.Builder(activity).setMessage(question).setNegativeButton("離開",null)
            .setPositiveButton("揭曉",(d,n) -> new AlertDialog.Builder(activity).setTitle(answer)
                .setItems(new String[]{"不會","模糊","會"},(dialog,i) -> {
                    db.rate(w,key,i+1); refresh.run(); next.run();
                }).show()).show();
    }

    // Stable keys let each expression keep its own FSRS history.
    private static final String[][] CHUNKS = {
        {"result","result-from","The delays resulted ___ heavy rain.（起因於）","from"},
        {"result","result-in","Heavy rain resulted ___ delays.（造成）","in"},
        {"lack","lack-of","The project failed for lack ___ funds.","of"},
        {"face","faced-with","We were faced ___ a difficult choice.","with"},
        {"take","take-granted","Don't take clean water for ___.（視為理所當然）","granted"},
        {"take","responsibility-for","She took responsibility ___ the error.","for"},
        {"provide","provide-with","The school provides students ___ meals.","with"},
        {"access","access-to","All students have access ___ the library.","to"},
        {"make","make-difference","Even a small change can make a ___.（帶來影響）","difference"},
        {"pay","pay-attention","Pay attention ___ the instructions.","to"},
        {"keep","keep-from","The rain kept us ___ going outside.","from"},
        {"depend","depend-on","Success depends ___ regular practice.","on / upon"},
        {"look","look-forward","I look forward to ___ you.（見到，使用 see）","seeing"},
        {"spend","spend-time","I spent an hour ___ the essay.（修改，使用 revise）","revising"},
        {"prevent","prevent-from","The fence prevents children ___ entering.","from"},
        {"different","different-from","My answer is different ___ yours.（與……不同）","from / to / than"}
    };
    private void chunk() {
        for (String[] item: CHUNKS) {
            try (Cursor c=db.getReadableDatabase().rawQuery("SELECT w.id,w.word,w.level,w.meanings FROM words w LEFT JOIN cards c ON c.word_id=w.id AND c.direction=? WHERE w.word=? AND (c.due IS NULL OR c.due<=?)",new String[]{item[1],item[0],String.valueOf(System.currentTimeMillis())})) {
                if (!c.moveToFirst()) continue;
                WordDatabase.Word w=new WordDatabase.Word(c.getLong(0),c.getString(1),c.getInt(2),c.getString(3));
                quiz(item[2],item[3],w,item[1],this::chunk); return;
            }
        }
        notice("字庫中可練的新語塊與到期語塊已完成");
    }
}
