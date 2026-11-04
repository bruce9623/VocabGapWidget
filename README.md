# 單字抓洞｜Android 桌面 Widget 來源專案（MVP）

這是**可編輯的 Android 原生專案原型**，不是 APK。需要 Android Studio、Android SDK 35、網路下載 Gradle 依賴後才能編譯安裝。此執行環境沒有 Android SDK／Gradle，因此**尚未完成實機編譯與 Android 裝置驗證**，不應視為已上架的成品。

## 現在具備的功能

- Android 首頁 Widget：看英文→按「顯示意思」→忘了／模糊／會／秒答→下一字。
- 主程式 Lv.1–5 英→中逐字掃描，一次按一字，不必先顯示中文。
- Lv.6 獨立速讀。
- Lv.1–4 中文→英文練習（自行回想及評分；任何正確同義詞都可能成立）。
- 到期英→中複習；Widget 優先選到期字，再選未掃描字，全部完成後可提前加練。
- FSRS-5 公開預設參數（19 個權重、目標記憶保持率 90%），Again 額外採 10 分鐘再學步驟。每個英→中／中→英方向的記憶狀態獨立儲存。
- 手機本機 SQLite；無帳號、無雲端，也不需網路權限。
- 從 Android 檔案選擇器匯入 UTF-8 CSV；重複詞彙合併更新內容而不清空學習紀錄。

## 沒有做的功能及限制

- **沒有內建正式的 7000 單表。** `demo_words.csv` 僅約 100 個自製示例詞；示例的 Lv 分級僅供操作演示，非大考中心官方分級的逐詞核定。需要你提供有使用權的完整字表並匯入。
- 沒有自動從網頁、圖片、PDF 擷取字詞；沒有真人語音或雲端同步。
- 目前是 FSRS-5 固定權重的基本排程，不包含個人化權重訓練、記憶保留率調校、亂數排程、完整 Anki 學習步驟等。不是 Anki 的等價替代。
- Lv.1–5 本版的「已掃描」指曾評分，不等於長期學會；已掌握字也會按排程再次出現。多義詞目前同一張卡顯示所有詞義，**尚未分義項獨立評分**。
- Widget 使用 Android 系統的定時更新機制，不是每次亮螢幕即強制更新；桌面常駐可見，無鎖定畫面／亮屏覆蓋功能。
- 沒有在 Android SDK、模擬器或手機上做過 APK 建置測試。

## 建置方式

1. 在電腦安裝最新版 Android Studio（需支援 Android Gradle Plugin 8.7.3）。
2. 用 Android Studio 開啟這個資料夾 `VocabGapWidget/`。
3. 安裝 SDK Platform 35，Gradle JDK 選 Java 17 或更新（建議 17）；同步 Gradle。專案設定使用 Gradle 8.11.1。
4. 專案包內含 `settings.gradle` 與 Gradle 設定檔，但**未附官方 Gradle Wrapper JAR**。若 Android Studio 提示缺少 Wrapper，請先使用本機 Gradle 8.11.1 執行 `gradle wrapper --gradle-version 8.11.1`，或讓 IDE 產生官方 Wrapper。
5. 選手機／模擬器執行 `app`。Android 8.0+（API 26+）可安裝；在 Android 15 上仍需實機驗證。
6. 桌面長按空白處 → 小工具 → 「單字抓洞」 → 新增至首頁。

## 另一種方式：GitHub Actions 產生 APK

專案附有 `.github/workflows/build-apk.yml`。將資料夾內容上傳你自己的 GitHub repository，從 **Actions → Build debug Android APK → Run workflow** 觸發；建置完成後，在該次 workflow 的 Artifacts 取得 debug APK。這是可選的雲端建置範例，目前未在 GitHub 實際執行，結果仍須依該次 CI 日誌確認。請只安裝自己確認來源可信的 APK。

## 匯入完整詞表

先在 Excel / Google Sheets / 其他文字編輯器準備三欄：

```csv
word,level,meanings
account,3,"帳戶；敘述；解釋；占（account for）"
contemplate,5,"沉思；仔細考慮"
obfuscate,6,"使模糊；混淆"
```

- 欄序固定 `word,level,meanings`，第一列標題可保留。
- `level` 為 `1`～`6`（也接受 `Lv.3`）；逗號包在雙引號內；檔案編碼 UTF-8。
- 同一個英文單字只保留一列，中文多義用 `；` 分隔；可用 CSV 正確引用英文逗號。
- 目前對單字只接受 A–Z、空格、連字號、單引號；專有名詞、衍生詞可視情況補充。
- 第一次匯入有效資料時，會**移除未在匯入檔中的示範詞**；與匯入檔重疊的示範詞改為正式匯入詞，保留既有評分。
- 匯入會保留既有正式詞條的評分紀錄，**再次匯入同一個英文單字會更新該列內容及等級**。
- 詳見 `import_template.csv`。

## 演算法及平台參考

- FSRS-5 公開公式及預設參數： https://open-spaced-repetition.github.io/ts-fsrs/guide/model/fsrs-5
- Android AppWidgetProvider API： https://developer.android.com/reference/android/appwidget/AppWidgetProvider

本專案為學習用途的獨立實作，未直接複製第三方字表；詞條與示範分級需自行核對。
