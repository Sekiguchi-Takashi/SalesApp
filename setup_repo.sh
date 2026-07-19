#!/usr/bin/env bash
# ZIP を展開したフォルダの中で実行する。
#   使い方:  GITHUB_TOKEN=ghp_xxx ./setup_repo.sh [リポジトリ名] [public|private]
set -e

# ★重要: どこから叩かれてもスクリプトのある場所で git を初期化する
#   （ホームディレクトリで git init してしまう事故を防ぐ）
cd "$(dirname "$0")"
echo "作業ディレクトリ: $(pwd)"

REPO="${1:-SalesApp}"
VIS="${2:-private}"
OWNER="Sekiguchi-Takashi"

if [ -z "$GITHUB_TOKEN" ]; then
  echo "GITHUB_TOKEN が未設定です。 GITHUB_TOKEN=ghp_xxx ./setup_repo.sh で実行してください。"
  exit 1
fi

if [ ! -f app/build.gradle ]; then
  echo "app/build.gradle が見つかりません。展開したフォルダの中で実行してください。"
  exit 1
fi

# 1. リモートリポジトリを作成（既にあれば警告のみで続行）
echo "リポジトリを作成中: $OWNER/$REPO ($VIS)"
PRIVATE=true
[ "$VIS" = "public" ] && PRIVATE=false
curl -s -o /tmp/repo_res.json -w "HTTP %{http_code}\n" \
  -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO\",\"private\":$PRIVATE,\"auto_init\":false}"
grep -q '"full_name"' /tmp/repo_res.json && echo "作成OK" || echo "既存または作成失敗（続行します）"

# 2. ローカル初期化
git config --global --get user.name  >/dev/null 2>&1 || git config --global user.name  "$OWNER"
git config --global --get user.email >/dev/null 2>&1 || git config --global user.email "$OWNER@users.noreply.github.com"

if [ ! -d .git ]; then
  git init -b main
else
  git checkout -B main
fi

git add -A
git commit -m "SalesApp: 緊急時マニュアル(機能6) と 営業プロンプト(機能2)" || echo "コミット対象なし"

# 3. push
git remote remove origin 2>/dev/null || true
git remote add origin "https://${OWNER}:${GITHUB_TOKEN}@github.com/${OWNER}/${REPO}.git"
git push -u origin main

# トークンが .git/config に残らないよう URL を差し替える
git remote set-url origin "https://github.com/${OWNER}/${REPO}.git"

echo ""
echo "完了。Actions のビルド状況:"
echo "  https://github.com/${OWNER}/${REPO}/actions"
