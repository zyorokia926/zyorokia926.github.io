# Shulker AutoFetch (Minecraft 1.19.4 / Fabric)

Litematica の **ホイールクリック(Pick Block)** と **Easy Place** が必要とするアイテムが、ホットバー・通常インベントリに無いとき、
インベントリ内の **シュルカーボックスの中身から実際に1スタック取り出し**、Litematica 本来の選択処理をそのまま続行させる Mod です。
取り出しは **サーバーが検証して実行** します(クライアントだけの書き換えはしません)。

## 必要な環境 / 依存 Mod

| 対象 | 必須 | 確認したバージョン |
|---|---|---|
| Minecraft | ○ | 1.19.4 |
| Fabric Loader | ○ | 0.15.11 |
| Fabric API | ○(クライアント・サーバー) | 0.87.2+1.19.4 |
| Litematica | ○(**クライアントのみ**) | 0.14.7 (許容: 0.14.2 以上 0.15 未満) |
| MaLiLib | ○(**クライアントのみ**) | 0.15.4 (許容: 0.15.3 以上 0.16 未満) |
| Java | ○ | 17 以上 |

Litematica / MaLiLib はクライアント専用 Mod のため、専用サーバーには不要です。クライアントで Litematica または MaLiLib が無い場合は、
分かりやすいエラーメッセージで起動を止めます。

## 導入

- **クライアント**: `shulker-autofetch-1.0.0.jar` を `mods/` へ(Litematica, MaLiLib, Fabric API も必要)。
- **サーバー**: 同じ JAR を `mods/` へ(Fabric API が必要。Litematica 不要)。
  **サーバー側にも導入しないと機能しません。** 未導入サーバーでは、クライアントは要求を送らず「サーバーに導入されていません」と表示します。
- シングルプレイは内蔵サーバーが同じ処理を行うので、クライアント導入のみで動作します。

サーバー設定: `config/shulkerfetch-server.json`(初回起動で生成)
`enabled`(全体の有効/無効), `cooldownTicks`(同一プレイヤーの最小要求間隔、既定 2)

## 設定(クライアント)

表示言語は日本語・英語(ゲームの言語設定に従う)。ホットキー **M + F**(変更可)で MaLiLib 設定画面が開きます。ファイルは `config/shulkerfetch.json`。
各ON/OFF設定には専用のトグルホットキーがあり、実行すると masa 系 Mod と同じように **ホットバーの上に通知** が出ます
(例: 「Shulker AutoFetch: Mod全体 を false にしました」/ "Shulker AutoFetch: Mod enabled is now false")。
失敗時(見つからない・満杯など)の画面通知は行いません(デバッグログのみ)。

| 設定(名前) | 既定 | トグルホットキー(既定) | 内容 |
|---|---|---|---|
| shulkerFetchEnabled | true | M + T | Mod 全体 |
| shulkerFetchProPlace | true | なし | Easy Place が精密設置プロトコル(V3 / V2 / Slabs only)で動作中の取得 |
| shulkerFetchEasyPlace | true | なし | Easy Place 設置時の取得 |
| shulkerFetchWheelClick | true | なし | ホイールクリック / Pick Block ホットキー時の取得 |
| shulkerFetchDebugLog | false | なし | デバッグログ |
| shulkerFetchOpenGui | — | M + F | 設定画面を開く |

(Litematica の既定ホットキー M+A / M+C / M+P / M+R / M+S とは重ならない組み合わせです。)

> **ProPlace について**: 本 Mod では ProPlace = Litematica の Easy Place の「精密設置プロトコル(Easy Place Protocol: V3/V2/Slabs only)」です。
> そのトグルが `shulkerFetchProPlace` です。取得処理自体は Easy Place・ホイールクリックが共通で通る1か所(下記)にあります。

## ビルド

```
./gradlew build          # build/libs/shulker-autofetch-1.0.0.jar とユニットテスト
./gradlew test           # ユニットテストのみ
```
動画の収録手順(開発用): `./gradlew runServer -Pdemo` の後に `xvfb-run ./gradlew runClient -Pdemo`(`src/selftest` の `DemoServer` / `DemoClient`)。

結合テストの再現(開発用・JAR には含まれません): `src/selftest` のドライバ Mod を使い、`./gradlew runServer`(`run/` に `eula.txt` と `server.properties`(`server-port=25599`, `online-mode=false`)を用意)の後、`xvfb-run ./gradlew runClient` で自動接続・シナリオ実行され、ログに `SELFTEST` が出ます。

JDK 17 以上 / Gradle 8.14.3(wrapper 同梱) / Fabric Loom 1.11.8。完成 JAR は `dist/` にも置いています。
Litematica / MaLiLib はビルド時に Modrinth Maven から取得します(`gradle.properties` の `*_version_id`)。

## 仕組み(調査結果に基づく)

Litematica 0.14.7 を逆コンパイルして確認した事実:

- ホイールクリック/Pick Block: `WorldUtils.doSchematicWorldPickBlock` → `InventoryUtils.schematicWorldPickBlock(ItemStack, BlockPos, World, MinecraftClient)`
- Easy Place(設置キー押下 / 押しっぱなし): `WorldUtils.doEasyPlaceAction`(private static)→ 同じ `InventoryUtils.schematicWorldPickBlock`。
  選択後に `EntityUtils.getUsedHandForItem` が null だと、設置位置のキャッシュ登録前に `FAIL` で戻る(=次回に再試行できる)。
- Litematica 標準の `pickBlockShulkers` は「シュルカーボックス自体を手に持つ」だけで、中身は取り出さない。

Mixin(`shulkerfetch.mixins.json`、`defaultRequire: 1` で適用失敗は起動時エラー):

- `MixinInventoryUtils` : `schematicWorldPickBlock` の HEAD。アイテムが既にインベントリにあれば **何もしない**(元の処理が続行)。
  無く、シュルカー内にある場合だけ、サーバーと同じ取り出し処理を **クライアントのインベントリへ即座に先行適用**(選択中の枠にその場で入る)し、要求を送る。元の処理は常にそのまま続行するので、ホイールクリックと同じtickで手持ちに入り、Easy Place は同じ押下で設置まで進む。
- `MixinWorldUtils` : 呼び出し元(ホイールクリック / Easy Place)を記録するだけで、挙動は変えない。

通信(Fabric API Networking, `shulkerfetch:hello / request / response`、プロトコル版数 2):

1. 参加時にサーバーが `hello`(版数)を送る。版数不一致・未受信ならクライアントは要求を送らない。
2. クライアントはローカルで事前確認(インベントリに有る / シュルカー内に無い / 空き無し)し、必要なときだけ `request`(選択中の枠番号を含む)を送る(未応答は最大4件、100tick で諦める)。ローカルの探索は1回(箱内エントリは先に生のアイテムIDで絞り込み、候補だけ解析)。
3. サーバーは 版数・生存・観戦でない・別画面を開いていない(カーソル/コンテナ操作中でない)・クールダウン を検証し、
   **実インベントリを再検索**して、シュルカー(コピー)から取り出し → 空きスロットへ設置 → 両方を同時に確定、`sendContentUpdates` で同期後に `response` を返す。
4. 取り出したアイテムは **必ず手に持っているホットバー枠(選択中の枠)** に入る。その枠に元からあったアイテムは **空きスロットへ退避して入れ替え**(通常27枠 → ホットバーの順)。破棄・上書きはしない。退避先が無ければ何も変更せず中断(画面表示はしない)。選択中の枠にいるシュルカー自身から取る場合は、中身を減らした箱を退避する。
5. 失敗(見つからない / 満杯 / 取り込み中 / タイムアウト)は画面に通知せず(デバッグログのみ)、同一アイテムは 40tick 再要求・再スキャンしない(毎tick 走査しない、無限ループなし)。

マッチ条件は `ItemStack.canCombine`(アイテム+NBT 一致)。LootTable 付き・Items が不正なシュルカーは「存在しない」扱いで、推測しません。
同じアイテムが複数箱・複数エントリにあるときは「**最大数の該当スタック**」から取り出します(同数なら若いスロット)。インベントリ(36枠)に既に同じアイテムがあるときは何もせず、Litematica 標準の動作(そちら優先)です。

## 動作確認結果

**実施済み(この環境で実行)**

| 項目 | 結果 |
|---|---|
| クリーンビルド / リマップ済み JAR | 成功。JAR 内ハンドラの descriptor(`class_1799` 等)が Litematica の対象メソッドと一致 |
| ユニットテスト 11 件(`FetchTransaction`) | 全成功: 取り出し量・残り・空箱のNBT除去、複数色の箱、既存時は無操作、未発見で無変更、満杯で消失なし、空きがホットバーのみでも上書きなし、データ無し/LootTable/不正NBTは推測しない、NBT付きアイテム非一致、連続要求で複製なし |
| 専用サーバー起動 | Fabric API のみで `Done`。Mixin エラーなし |
| 実クライアント(xvfb, Litematica+MaLiLib 実物)+専用サーバー(localhost) | Mixin 適用エラー 0。ホイールクリック経路・Easy Place 経路の双方で `schematicWorldPickBlock` を実行し、同tickで手持ち枠に最大スタックが入る(先行適用)→要求→サーバー検証→同期まで成功。同一tickの2要求ではサーバーが2件目を拒否し、クライアントの先行適用が巻き戻って最終状態がサーバーと一致 |
| 同期の一致 | 最終状態のサーバー側ダンプとクライアント側ダンプが完全一致(全スロットとシュルカー内訳) |
| 重複・ループ | 既に所持→要求0件。存在しないアイテムの連続呼び出し→スキャン1回のみ。呼び出し元不明→無視。3シナリオで要求ちょうど3件 |

| 実入力テスト(X11 の実イベント、xdotool) | 中ボタンクリックで手持ちのリンゴが石64(最大スタック)に変わる / 右クリック長押しの Easy Place でガラスが取得され実際に設置される / M+T でホットバー上に日本語・英語の通知 / M+F で設定画面(日本語・英語)が開く |
| 設計図(実物の Litematica 配置)を使ったデモ収録 | 設計図ブロックを視線で指し、Litematica 本来のルーチン(`doSchematicWorldPickBlock(true)` = 中ボタン既定の経路、`handleEasyPlace`)を呼んで、ホイールクリックの取得、Easy Place での石・ガラス・木材・ハーフブロック(Slabs only)の取得と **実際のブロック設置**、レンガ取得、満杯時の無動作、存在しない場合の無動作、設定画面を確認(`video/shulker-autofetch-feature-explained.mp4`) |

**未検証(実施できていません)**

- 物理ハードウェアのマウス/キーボード(X11 の実イベント(xdotool)では中ボタン・右クリック長押し・M+T / M+F を確認済み)。
- Easy Place は取得待ちの1回目の設置が `FAIL`(警告表示)になり、取得後の次の押下(押しっぱなしなら次tick)で設置されます(デモでは自動で繰り返し呼んでいます)。
- **インベントリ満杯 / 取り込み中(別画面)/ クールダウン** をサーバー上で実機確認すること(満杯・消失なしはユニットテストのみ)。
- **Mod 未導入サーバー / 版数不一致** の実機確認(コードは実装済み、実行していません)。
- 複数人同時接続、Carpet 等の他サーバー Mod、他の Litematica 派生/アドオンとの併用。
- 本番の intermediary 環境(通常の Fabric ランチャー)での起動。開発環境(yarn)では確認済み、リマップ結果は javap で確認したのみ。
- ビューの音声: 検証環境にオーディオデバイスが無く OpenAL 例外が出ていますが、Mod とは無関係です。

## 既知の制限

- シュルカーの探索対象はメインインベントリ36枠のみ(オフハンド、エンダーチェスト、設置済みの箱は対象外)。
- 手に持っている枠のアイテムは、空きスロットが無いと入れ替えられず中断します(アイテムは失われません)。
- 取り出しは1要求につき1スタック(アイテムの最大スタック数まで)。
- クリエイティブ / 観戦モードでは何もしません(Litematica 標準動作)。
- Easy Place で複数アイテムを連続して必要とする場合、アイテムごとにサーバー往復が1回(数tick)入ります。
- 対応は Litematica 0.14.x / MaLiLib 0.15.x のみ。内部メソッド(`schematicWorldPickBlock`, `doEasyPlaceAction`, `doSchematicWorldPickBlock`)が変わると、Mixin 適用エラーで起動時に判明します(黙って無効化はしません)。

## ライセンス

LGPL-3.0-or-later
