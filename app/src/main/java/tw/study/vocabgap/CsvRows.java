package tw.study.vocabgap;

import java.io.*;
import java.util.*;

/** UTF-8 CSV reader. Supports escaped quotes, embedded newlines and BOM. */
public final class CsvRows {
    private CsvRows() {}
    public static List<List<String>> parse(Reader input) throws IOException {
        PushbackReader in = new PushbackReader(new BufferedReader(input), 1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int c;
        while ((c=in.read()) != -1) {
            if (rows.isEmpty() && row.isEmpty() && cell.length() == 0 && c == '\uFEFF') continue;
            if (c == '"') {
                if (quoted) {
                    int next = in.read();
                    if (next == '"') cell.append('"');
                    else { quoted = false; if (next != -1) in.unread(next); }
                } else if (cell.length() == 0) quoted = true;
                else cell.append('"');
            } else if (c == ',' && !quoted) {
                row.add(cell.toString().trim()); cell.setLength(0);
            } else if ((c == '\r' || c == '\n') && !quoted) {
                if (c == '\r') { int next = in.read(); if (next != '\n' && next != -1) in.unread(next); }
                row.add(cell.toString().trim()); cell.setLength(0);
                if (row.size() > 1 || !row.get(0).isEmpty()) rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append((char)c);
            }
        }
        if (quoted) throw new IOException("CSV 引號未關閉");
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim()); rows.add(row);
        }
        return rows;
    }
}
