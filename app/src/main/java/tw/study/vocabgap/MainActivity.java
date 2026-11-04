package tw.study.vocabgap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int PICK_CSV = 901;
    private WordDatabase db;
    private WordDatabase.Word current;
    private String mode = "";
    private TextView question, answer, info, stats;
    private Button reveal, easy;
    private View ratingRow;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        db = WordDatabase.open(this);
        question=findViewById(R.id.question);
        answer=findViewById(R.id.answer);
        info=findViewById(R.id.mode_info);
        stats=findViewById(R.id.stats);
        reveal=findViewById(R.id.reveal_button);
        easy=findViewById(R.id.easy_button);
        ratingRow=findViewById(R.id.rating_row);

        findViewById(R.id.scan_button).setOnClickListener(v->start("scan5"));
        findViewById(R.id.review_button).setOnClickListener(v->start("review"));
        findViewById(R.id.output_button).setOnClickListener(v->start("output"));
        findViewById(R.id.lv6_button).setOnClickListener(v->start("scan6"));
        findViewById(R.id.import_button).setOnClickListener(v->pickFile());
        reveal.setOnClickListener(v->reveal());
        findViewById(R.id.again_button).setOnClickListener(v->rate(1));
        findViewById(R.id.hard_button).setOnClickListener(v->rate(2));
        findViewById(R.id.good_button).setOnClickListener(v->rate(3));
        easy.setOnClickListener(v->rate(4));
        stats.setText(db.stats());
        if (getIntent() != null && "widget".equals(getIntent().getStringExtra("from"))) {
            start("review");
        }
    }
    private void start(String newMode) {
        mode=newMode;
        loadNext();
    }
    private void loadNext() {
        if (mode.equals("scan5")) current=db.nextScan(false);
        else if (mode.equals("scan6")) current=db.nextScan(true);
        else if (mode.equals("output")) current=db.nextOutput();
        else if (mode.equals("review")) current=db.nextReview(WordDatabase.EN,6);
        else current=null;
        stats.setText(db.stats());
        if (current == null) {
            question.setText(mode.equals("review") ? "到期已清空" : "這一輪完成");
            answer.setText(mode.equals("review") ? "可繼續掃描新字；或回到桌面小工具" :
                    "如果詞表不完整，可匯入自己的 UTF-8 CSV 字庫");
            answer.setVisibility(View.VISIBLE);
            info.setText("沒有更多符合條件的單字");
            ratingRow.setVisibility(View.GONE);
            reveal.setVisibility(View.GONE);
            easy.setVisibility(View.GONE);
            return;
        }
        boolean output = mode.equals("output");
        boolean scan = mode.startsWith("scan");
        String label = mode.equals("scan5") ? "Lv.1–5｜快速掃描" :
            mode.equals("scan6") ? "Lv.6｜主要意思辨識" :
            mode.equals("output") ? "Lv.1–4｜中→英主動輸出" : "到期複習｜英→中";
        info.setText(label + "  •  Lv." + current.level);
        question.setText(output ? current.meanings : current.word);
        answer.setText(output ? current.word : current.meanings);
        answer.setVisibility(View.INVISIBLE);
        reveal.setVisibility(View.VISIBLE);
        reveal.setText(output ? "揭曉目標英文（也可使用其他正確同義詞）" : "顯示中文意思");
        ratingRow.setVisibility(scan ? View.VISIBLE : View.GONE);
        easy.setVisibility(View.GONE);
    }
    private void reveal() {
        if (current == null) return;
        answer.setVisibility(View.VISIBLE);
        reveal.setVisibility(View.GONE);
        ratingRow.setVisibility(View.VISIBLE);
        easy.setVisibility(mode.startsWith("scan") ? View.GONE : View.VISIBLE);
    }
    private void rate(int grade) {
        if (current == null) return;
        if (!mode.startsWith("scan") && answer.getVisibility() != View.VISIBLE) return;
        String direction = mode.equals("output") ? WordDatabase.ZH : WordDatabase.EN;
        db.rate(current, direction, grade);
        WordWidget.resetAfterImport(this);
        loadNext();
    }
    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/csv", "text/plain", "application/vnd.ms-excel", "application/octet-stream"});
        startActivityForResult(intent,PICK_CSV);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=PICK_CSV || result!=RESULT_OK || data==null || data.getData()==null) return;
        try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
            if (stream == null) throw new IllegalStateException("無法開啟檔案");
            int count=db.importCsv(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Toast.makeText(this,"已匯入或更新 " + count + " 筆詞條",Toast.LENGTH_LONG).show();
            WordWidget.resetAfterImport(this);
            loadNext();
        } catch (Exception e) {
            Toast.makeText(this,"匯入失敗："+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (db!=null) stats.setText(db.stats());
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (db!=null) db.close();
    }
}
