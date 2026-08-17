# SalesApp — 機能6（緊急時マニュアル）／機能2（営業プロンプト）

Termux + GitHub Actions ビルド前提。Gradle wrapper なし、外部ライブラリ依存ゼロ、XMLレイアウトなし。

## 構成

```
SalesApp/
  settings.gradle / build.gradle / gradle.properties
  deploy.sh                        ← push → pull --rebase → タグ発行を1コマンドで完結
  .github/workflows/release.yml    ← カタログ管理システムが配置（タグ起動の配布ビルド）
  ci/appathy.keystore              ← カタログ管理システムが配置（配布署名）
  app/
    build.gradle
    debug.keystore                 ← 署名固定用（コミット対象）
    src/main/AndroidManifest.xml
    src/main/assets/
      emergency_manual.json        ← 機能6の中身
      prompt_library.json          ← 機能2の中身（アプリの中核資産）
    src/main/java/com/sekiguchi/salesapp/
      Ui.kt                 プログラマティックUIヘルパー
      Store.kt              assets読込 + SharedPreferences + 出所タグ削除
      MainActivity.kt       入口 / 退職時データ削除
      EmergencyActivity.kt  機能6
      PromptActivity.kt     機能2
```

## 展開からpushまで

```bash
cp /sdcard/Download/SalesApp_v7.zip ~
cd ~
unzip -o SalesApp_v7.zip
~/SalesApp/deploy.sh "コミットメッセージ"
```

`deploy.sh` が push → `git pull --rebase origin main` → タグ発行 まで1コマンドで完結させます。

次タグは `git tag --list 'v*' | sort -V` の最大値から算出し、`git tag` / `git push origin タグ名`
でローカル発行します。GitHub API の heads/releases 参照は反映遅延で一つ前のタグに付くため使いません。

タグを打たずに push だけしたい場合は第2引数に `notag` を渡します。

```bash
~/SalesApp/deploy.sh "作業中" notag
```

pull --rebase が必須なのは、カタログ管理システムが API 経由で
`.github/workflows/release.yml` と `ci/appathy.keystore` を直接コミットしているためです。
**この2ファイルと `ci/` ディレクトリは配布ビルドに必要なので削除・追跡解除しないこと。**
ZIP には含まれていませんが、`unzip -o` は既存ファイルを消さないため残ります。

トークンは事前に一度だけ登録します。

```bash
git config --global github.token ghp_xxxxxxxxxxxx
```

タグが打たれると Actions がビルドして Release を作り、自作アプリストアに更新として現れます。

## 更新運用の考え方

- `prompt_library.json` の `profile_layer.types[].modifier` は現在すべて空文字。
  運用の気づきをここに2〜3行ずつ書き足していく。**Kotlinを一切触らずに改良できる**
- `emergency_manual.json` の連絡先も同様。番号だけはアプリ側の編集値が優先される
  （assets を更新してもユーザー登録番号は消えない2層構造）

## 設計上の決めごと

| 項目 | 判断 |
|---|---|
| AI API | アプリからは叩かない。プロンプト文字列を組み立ててコピーするだけ |
| 通信 | 一切なし。INTERNET権限も宣言していない |
| 権限 | ゼロ。電話は `ACTION_DIAL` で権限不要 |
| 出所タグ | 連絡先と現場記録に付与。`company-derived` のみ一括削除可能 |
| 混入チェック | 「株式会社」「型番らしき英数字」を検出して警告（機能2の入力時） |
| CI | release.yml（タグ起動）のみ。build.yml は作らない |
| Artifacts | 出力しない。無料枠 0.5GB を消費し全ビルドが落ちるため。APK は Release から配布 |

## 画面構成（v4）

上部にクイックアクション（メモ / 切り返し / 計算 / タイマー）。以下3分類。

- **営業中** … 切り返し / 概算計算 / 商談タイマー / 案件 / 本日のルート / クイックメモ / 緊急時マニュアル
- **営業準備中** … 営業プロンプト（場面別10・推論10）/ トーク集・反省事例 / 分析 / 走行距離・経費
- **情報ツール** … オントロジー / 参考情報（法令・業界地図・単位換算）/ 会社由来データ削除

## 設計書「営業2.0」からの反映

採用したもの：オントロジー（概念・課題と解決策・段階）、AI推論ルール10カテゴリ（推論プロンプト）、
商談管理（案件トラッカー）、分析ダッシュボード（自分の行動分析）、クイックアクション。

採用しなかったもの：顧客管理・企業管理・名刺撮影・人脈管理。
これらは顧客名・社名・担当者名を保存する機能であり、
本アプリの前提（社内情報を一切持たない）と両立しない。

案件トラッカーは識別情報を持たない設計にしたうえで、
なお会社の営業活動に由来するため `company-derived` タグを付け、退職時削除の対象にしている。

## 次の着手

残るのは機能1（業界ごとのオープン情報）と機能4（公開情報に絞った情報蓄積）。
どちらも実装より先に「何を集めて、どう更新し続けるか」を決める作業になる。
