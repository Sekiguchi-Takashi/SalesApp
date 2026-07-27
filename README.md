# SalesApp — 機能6（緊急時マニュアル）／機能2（営業プロンプト）

Termux + GitHub Actions ビルド前提。Gradle wrapper なし、外部ライブラリ依存ゼロ、XMLレイアウトなし。

## 構成

```
SalesApp/
  settings.gradle / build.gradle / gradle.properties
  setup_repo.sh                    ← リポジトリ作成〜pushを一括実行
  .github/workflows/build.yml      ← Gradle 8.9 を Actions 側で固定
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
cd ~
mkdir -p SalesApp
cd SalesApp

# ★ -o を付ける（上書き確認で止まらないように）
unzip -o ~/storage/downloads/SalesApp.zip -d ~/SalesApp

# 展開先の確認。ここで一段深いフォルダになっていないかを必ず見る
ls
# → settings.gradle が見えていればOK
#   SalesApp/SalesApp/ のように入れ子になっていたら
#   cd SalesApp して以降を実行する

# 権限付与
chmod +x setup_repo.sh

# リポジトリ作成 → commit → push まで一括
GITHUB_TOKEN=ghp_xxxxxxxxxxxx ./setup_repo.sh SalesApp private
```

`setup_repo.sh` は冒頭で `cd "$(dirname "$0")"` を実行するので、
ホームディレクトリで `git init` してしまう事故は起きません。

push が済むと Actions が自動で走ります。

```
https://github.com/Sekiguchi-Takashi/SalesApp/actions
```

Artifacts から `SalesApp-debug-apk` をダウンロードして端末にインストール。

## 2回目以降の更新

```bash
cd ~/SalesApp
git add -A
git commit -m "prompt_library: 相手タイプの調整"
git push
```

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

## 画面構成（v3）

トップページは3分類。

- **営業中** … 切り返し / 概算計算 / 商談タイマー / 本日のルート / クイックメモ / 緊急時マニュアル
- **営業準備中** … 営業プロンプト / トーク集・反省事例 / 走行距離・経費
- **情報ツール** … 参考情報（法令・業界地図・単位換算） / 会社由来データ削除

## 次の着手

残るのは機能1（業界ごとのオープン情報）と機能4（公開情報に絞った情報蓄積）。
どちらも実装より先に「何を集めて、どう更新し続けるか」を決める作業になる。
