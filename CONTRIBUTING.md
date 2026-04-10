# Contributing

KULMS Android への貢献を歓迎します。

## 開発環境のセットアップ

1. リポジトリをフォーク & クローン
   ```bash
   git clone https://github.com/<your-username>/kulms-android.git
   ```
2. Android Studio でプロジェクトを開く
3. 実機またはエミュレータでビルド・実行（API 26+）
4. 初回起動時に SSO でログインして動作確認

## プロジェクト構成

```
data/model/        # Room エンティティ (Assignment)
data/local/        # Room Database, DAO
data/remote/       # WebViewFetcher, SakaiApiClient
store/             # AssignmentViewModel (状態管理)
ui/                # Jetpack Compose UI
notification/      # 通知ヘルパー + BroadcastReceiver
worker/            # WorkManager バックグラウンド更新
```

### アーキテクチャのポイント

- API 呼び出しは WebView の `evaluateJavascript` + `@JavascriptInterface` で実行（OkHttp等は Sakai セッション cookie が認証されないため不使用）
- SSO ログインと API 呼び出しに同一の WebView インスタンスを使用
- `ConcurrentHashMap` + リクエストID で並行リクエストを管理

## コーディング規約

- 外部依存は最小限に（Gson のみ）
- Kotlin + Jetpack Compose + Room を使用
- コルーチンで非同期処理
- ViewModel + StateFlow で状態管理

## Pull Request の流れ

1. `main` から作業ブランチを作成
2. 変更を実装し、Android Studio でビルドが通ることを確認
3. コミットメッセージは変更内容を日本語で簡潔に記述
4. Pull Request を作成し、変更内容を説明

## Issue

- バグ報告・機能リクエストは [Issue テンプレート](https://github.com/Radian0523/kulms-android/issues/new/choose) を使用してください
- フィードバックフォーム: [Google Forms](https://docs.google.com/forms/d/e/1FAIpQLScLn4G2IF1w0-QOWPKZ7R1LXjOq7OocYUmGJLoNA6JBuA20EA/viewform)
