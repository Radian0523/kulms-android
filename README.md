# KULMS Android

京都大学LMS (KULMS/Sakai) の課題一覧をAndroidネイティブで確認できるアプリ。

[Chrome拡張版](https://github.com/Radian0523/kulms-extension) / [iOS版](https://github.com/Radian0523/kulms-ios) のAndroid移植。

## 機能

- **SSO認証**: WebViewで京大のシングルサインオンに対応（ECS-ID/パスワードによるネイティブログインにも対応、2段階認証時はWebViewにフォールバック）
- **課題一覧**: Sakai Direct APIから全科目の課題を取得し、緊急度別に表示
- **テスト/クイズ対応**: Sakai sam_pub APIからテスト・クイズも取得し課題と統合表示（未公開クイズは `startDate` で自動除外）
- **提出状態の正確な判定**: 個別課題APIで提出済み・評定済みを正確に判定
- **セッション切れ保護**: fetch 中にセッションが切れた場合もキャッシュを保護し、部分データで上書きしない
- **締切リマインド**: ローカル通知でリマインド（タイミングは設定画面で自由にカスタマイズ可能、10分前〜3日前・最大5個）
- **バックグラウンド更新**: WorkManagerで定期的に課題を再取得し通知をスケジュール
- **オフラインキャッシュ**: Roomで課題をローカル保存。起動時はキャッシュを即座に表示し、更新ボタンで最新データを取得

## 緊急度の分類

| 色 | 分類 | 条件 |
|---|---|---|
| 赤 | 緊急 | 24時間以内 / 期限切れ |
| 黄 | 5日以内 | 5日以内 |
| 緑 | 14日以内 | 14日以内 |
| 灰 | その他 | 14日以上先 / 期限なし |

## アーキテクチャ

API呼び出しはすべてWebViewの`evaluateJavascript`経由のJavaScript `fetch()`で実行。標準HTTPクライアント（OkHttp等）ではSakaiのセッションcookieが認証されないため、SSOログインと同一のWebViewインスタンスを使用する設計。

JavaScriptインターフェース（`@JavascriptInterface`）でKotlinコルーチンとJavaScriptを橋渡しし、リクエストIDで並行リクエストを管理。

## 技術スタック

- Kotlin / Jetpack Compose / Room
- Android 8.0+ (API 26)
- WebView (SSO認証 + API呼び出し)
- WorkManager (バックグラウンド更新)
- AlarmManager + BroadcastReceiver (通知スケジュール)

外部依存: Gson のみ。

## ビルド

Android Studio でプロジェクトを開き、ビルド・実行。初回起動時にSSOでログインすると課題一覧が表示される。

```bash
# Android Studio で開く
open -a "Android Studio" .
```

## フィードバック

ご意見・要望は [こちらのフォーム](https://docs.google.com/forms/d/e/1FAIpQLScLn4G2IF1w0-QOWPKZ7R1LXjOq7OocYUmGJLoNA6JBuA20EA/viewform) からお送りください。アプリの設定画面からもアクセスできます。
