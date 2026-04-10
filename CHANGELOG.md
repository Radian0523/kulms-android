# Changelog

## v1.0.0

初回リリース。iOS版の全機能をAndroidネイティブに移植。

### 機能

- SSO認証: WebViewで京大シングルサインオンに対応
- 全科目課題一覧: Sakai Direct APIから取得し緊急度別に表示
- テスト/クイズ対応: `sam_pub` APIからクイズを取得し課題と統合表示
- 提出状態の正確な判定: 個別課題API (`/direct/assignment/item/`) で提出済み・評定済みを判定
- 締切リマインド: 24時間前・1時間前にローカル通知
- バックグラウンド更新: WorkManagerで定期的に課題を再取得
- オフラインキャッシュ: Roomでローカル保存、起動時はキャッシュを即座に表示
- 手動更新: ツールバーの更新ボタン / プルダウンで最新データを取得
- セッション管理: セッション切れ時は更新ボタンからログイン画面へ遷移

### アーキテクチャ

- WebView `evaluateJavascript` + `@JavascriptInterface` 経由の `fetch()` で全API呼び出し
- リクエストIDベースの並行リクエスト管理
- Kotlin コルーチン + Semaphore で並行数制御（最大4コース同時）
