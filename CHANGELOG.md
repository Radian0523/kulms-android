# Changelog

## v1.1.2

- **完了判定のバグ修正**
  - 期限切れ+チェック済み課題が完全に非表示になる問題を修正（チェック済み課題は常に完了済みセクションに表示）

## v1.1.1

- **ECS-ID / パスワードログイン**
  - ネイティブログインフォーム（ECS-ID・パスワード入力）を追加
  - Android Keystore で認証情報を暗号化保存（AES/GCM）
  - 保存済み認証情報による自動ログイン
  - Android Autofill フレームワーク対応（パスワードマネージャー連携）
  - 2段階認証（OTP）検出時は従来の WebView ログインにフォールバック
- **SAML SSO リダイレクトの安定化**
  - WebViewFetcher に `waitForStableNavigation()` を追加（500ms 安定待機で SAML リダイレクトチェーンに対応）
  - `ensureOnLMS()` で WebView が認証ドメインに留まっている場合に自動的に LMS ポータルへ復帰
- **再提出受付期間の表示**
  - 締切後でも再提出受付終了時刻（closeTime）までは「再提出受付期間」と残り時間を表示
  - 残り時間の分表示を修正（「残り1日2時間30分」のように正確に表示）
- **セクション折りたたみ機能**
  - 課題一覧のセクションヘッダーをタップで折りたたみ/展開
  - 「完了済み」セクションはデフォルトで折りたたみ
  - 折りたたみ状態は SharedPreferences に永続化

## v1.1.0

- **通知タイミングのカスタマイズ機能**
  - 締切リマインドの通知タイミングをユーザーが自由に設定可能に（10分前〜3日前、最大5個）
  - デフォルトは従来通り24時間前・1時間前の2段階
  - 設定画面の通知セクションに Chip UI + ピッカーダイアログを追加
  - タイミング追加・削除時に即座に通知を再スケジュール
  - 設定は SharedPreferences に永続化（アプリ再起動後も保持）

## v1.0.3

- **提出済み課題のチェックボックスロックを廃止**
  - API誤判定時にユーザーが手動で修正できない問題を解消
  - 提出済み・再提出不可の課題でもチェックを操作可能に

## v1.0.2

- **クイズ・テストの自動提出判定を無効化**
  - Sakai `sam_pub` API は学生個人の提出データを返さないため、`submitted` フラグの信頼性が不明
  - クイズの `status` を常に空文字列に変更（手動チェックのみで完了管理）
  - 設定画面の説明にクイズ・テストは手動チェックのみである旨を追記
- **未公開クイズが課題一覧に表示される問題を修正**
  - Sakai の `sam_pub_collection` API は `startDate`（公開開始時刻）が未来のクイズも返すが、フィルタしていなかったため未公開のテスト・クイズが表示されていた
  - `RawQuiz` に `startDate` フィールドを追加し、`startDate > 現在時刻` のクイズを除外（ブラウザ拡張 v1.11.2 / Comfortable Sakai 準拠）
- **fetch 中のセッション切れ検知 + キャッシュ保護**
  - `checkSession()` 後に `fetchAllAssignments()` の途中でセッションが切れた場合、各 fetch が空リストを返し `dao.replaceAll()` で既存キャッシュが部分データに上書きされていた
  - WebViewFetcher の JS fetch 内でリダイレクト URL / Content-Type を検知し `SessionExpiredException` を送出
  - `SakaiApiClient` の各 fetch メソッドで `SessionExpiredException` を上位に伝播
  - `AssignmentViewModel` / `RefreshWorker` で catch し、`dao.replaceAll()` をスキップして既存キャッシュを保護
  - ブラウザ拡張 v1.11.1 の `LoggedOutError` 伝播方式と同等の挙動

## v1.0.1

- 「取組中」の課題が「完了済み」に誤分類されるバグを修正
  - Sakai API の `userSubmission` は下書き保存でも `true` になるため、`submitted && !draft` で判定するよう修正
  - サーバー計算の `status` フィールドを活用し「取組中」等を正しく表示

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
