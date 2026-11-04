package tw.study.vocabgap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class WordDatabase extends SQLiteOpenHelper {
    public static final String EN = "en", ZH = "zh";

    public static final class Word {
        public final long id;
        public final String word, meanings;
        public final int level;
        Word(long id, String word, int level, String meanings) {
            this.id = id; this.word = word; this.level = level; this.meanings = meanings;
        }
    }

    public WordDatabase(Context context) {
        super(context, "vocab_gap.db", null, 1);
        this.appContext = context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE words (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word TEXT NOT NULL UNIQUE COLLATE NOCASE, level INTEGER NOT NULL, meanings TEXT NOT NULL, is_demo INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE cards (word_id INTEGER NOT NULL, direction TEXT NOT NULL, " +
                "stability REAL NOT NULL, difficulty REAL NOT NULL, reviews INTEGER NOT NULL, " +
                "last_review INTEGER NOT NULL, due INTEGER NOT NULL, last_grade INTEGER NOT NULL, " +
                "PRIMARY KEY(word_id,direction))");
        db.execSQL("CREATE INDEX idx_words_level ON words(level,id)");
        db.execSQL("CREATE INDEX idx_cards_due ON cards(direction,due)");
        // This bundled list is demonstrative, not the full 7,000 words.
        try (Reader r = new InputStreamReader(
                appContext.getAssets().open("demo_words.csv"), StandardCharsets.UTF_8)) {
            addCsv(db, CsvRows.parse(r), true);
        } catch (Exception e) { throw new IllegalStateException("無法載入示範單字", e); }
    }

    private Context appContext;
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    // The Android SQLiteOpenHelper constructor does not invoke onCreate until first open.
    // Keep an application context for the bundled CSV.
    public static WordDatabase open(Context c) {
        WordDatabase helper = new WordDatabase(c);
        helper.appContext = c.getApplicationContext();
        return helper;
    }

    private static int parseLevel(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("lv.", "")
                .replace("lv", "").replace("level", "").trim();
        try { int v = Integer.parseInt(s); return v >= 1 && v <= 6 ? v : -1; }
        catch (NumberFormatException e) { return -1; }
    }
    private static int addCsv(SQLiteDatabase db, List<List<String>> rows, boolean demo) {
        int count = 0;
        for (List<String> row : rows) {
            if (row.size() < 3) continue;
            // CSV header: word,level,meanings
            String spelling = row.get(0).trim();
            int level = parseLevel(row.get(1));
            String meanings = row.get(2).trim();
            if (spelling.isEmpty() || spelling.length() > 70 || level < 1 || meanings.isEmpty()) continue;
            if (!spelling.matches("[A-Za-z][A-Za-z '-]*")) continue;
            ContentValues values = new ContentValues();
            values.put("word", spelling);
            values.put("level", level);
            values.put("meanings", meanings);
            values.put("is_demo", demo ? 1 : 0);
            long inserted = db.insertWithOnConflict("words", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            if (inserted == -1) {
                ContentValues update = new ContentValues();
                update.put("level", level); update.put("meanings", meanings);
                update.put("is_demo", demo ? 1 : 0);
                db.update("words", update, "word=? COLLATE NOCASE", new String[]{spelling});
            }
            count++;
        }
        return count;
    }
    public int importCsv(Reader reader) throws Exception {
        List<List<String>> rows = CsvRows.parse(reader);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int count = addCsv(db, rows, false);
            if (count > 0) {
                db.execSQL("DELETE FROM cards WHERE word_id IN " +
                        "(SELECT id FROM words WHERE is_demo=1)");
                db.delete("words", "is_demo=1", null);
            }
            db.setTransactionSuccessful();
            return count;
        } finally { db.endTransaction(); }
    }
    private static Word getOne(SQLiteDatabase db, String sql, String... args) {
        try (Cursor c = db.rawQuery(sql, args)) {
            return c.moveToFirst() ? new Word(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3)) : null;
        }
    }
    private static String BASE = "SELECT w.id,w.word,w.level,w.meanings FROM words w ";

    public Word word(long id) {
        return getOne(getReadableDatabase(), BASE + "WHERE w.id=?", String.valueOf(id));
    }
    public Word nextScan(boolean level6) {
        return getOne(getReadableDatabase(), BASE +
            "LEFT JOIN cards c ON c.word_id=w.id AND c.direction='en' " +
            "WHERE c.word_id IS NULL AND " + (level6 ? "w.level=6 " : "w.level<=5 ") +
            "ORDER BY w.level,w.id LIMIT 1");
    }
    public Word nextReview(String direction, int maxLevel) {
        return getOne(getReadableDatabase(), BASE +
            "JOIN cards c ON c.word_id=w.id " +
            "WHERE c.direction=? AND c.due<=? AND w.level<=? " +
            "ORDER BY c.due, c.last_grade LIMIT 1", direction,
            String.valueOf(System.currentTimeMillis()), String.valueOf(maxLevel));
    }
    public Word nextOutput() {
        Word due = nextReview(ZH, 4);
        if (due != null) return due;
        return getOne(getReadableDatabase(), BASE +
            "LEFT JOIN cards c ON c.word_id=w.id AND c.direction='zh' " +
            "WHERE w.level<=4 AND c.word_id IS NULL ORDER BY w.level,w.id LIMIT 1");
    }
    public Word nextWidget() {
        Word w = nextReview(EN,5);
        if (w != null) return w;
        w = nextScan(false);
        if (w != null) return w;
        return getOne(getReadableDatabase(), BASE +
            "JOIN cards c ON c.word_id=w.id WHERE c.direction='en' AND w.level<=5 " +
            "ORDER BY c.due LIMIT 1");
    }
    public void rate(Word word, String direction, int grade) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int reviews = 0;
            double s=0,d=0;
            long last=0;
            try (Cursor c = db.rawQuery("SELECT stability,difficulty,reviews,last_review " +
                    "FROM cards WHERE word_id=? AND direction=?",
                    new String[]{String.valueOf(word.id), direction})) {
                if (c.moveToFirst()) {
                    s=c.getDouble(0); d=c.getDouble(1); reviews=c.getInt(2); last=c.getLong(3);
                }
            }
            long now = System.currentTimeMillis();
            Fsrs5.State state = Fsrs5.review(reviews>0,s,d,last,grade,now);
            ContentValues v = new ContentValues();
            v.put("word_id", word.id); v.put("direction", direction);
            v.put("stability", state.stability); v.put("difficulty", state.difficulty);
            v.put("reviews", reviews+1); v.put("last_review", now);
            v.put("due", state.due); v.put("last_grade", grade);
            db.insertWithOnConflict("cards", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private int scalar(String sql, String... args) {
        try (Cursor c=getReadableDatabase().rawQuery(sql,args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    public String stats() {
        int total = scalar("SELECT COUNT(*) FROM words WHERE level<=5");
        int scanned = scalar("SELECT COUNT(*) FROM cards c JOIN words w ON w.id=c.word_id " +
                "WHERE c.direction='en' AND w.level<=5");
        int unknown = scalar("SELECT COUNT(*) FROM cards c JOIN words w ON w.id=c.word_id " +
                "WHERE c.direction='en' AND w.level<=5 AND c.last_grade=1");
        int shaky = scalar("SELECT COUNT(*) FROM cards c JOIN words w ON w.id=c.word_id " +
                "WHERE c.direction='en' AND w.level<=5 AND c.last_grade=2");
        int due = scalar("SELECT COUNT(*) FROM cards c JOIN words w ON w.id=c.word_id " +
                "WHERE c.direction='en' AND w.level<=5 AND c.due<=?",
                String.valueOf(System.currentTimeMillis()));
        int lv6=scalar("SELECT COUNT(*) FROM words WHERE level=6");
        return "Lv.1–5 已掃描 " + scanned + " / " + total + "（" +
                (total == 0 ? 0 : scanned*100/total) + "%）\n" +
                "目前標記：不會 " + unknown + " · 模糊 " + shaky +
                " · 到期 " + due + "\n" +
                "Lv.6 詞條 " + lv6 + "（另行速讀）";
    }
}
