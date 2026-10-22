# Shulker AutoFetch 仕様書 (v1.0.0)

## 1. 概要
Minecraft Java 1.19.4 / Fabric 用の Litematica アドオン。
Litematica の **ホイールクリック(Pick Block)** と **Easy Place**(精密設置プロトコル V3/V2/Slabs only = 「ProPlace」を含む)が必要とするアイテムが
メインインベントリ(ホットバー+27枠)に無いとき、インベントリ内のシュルカーボックスから1スタックを取り出し、**今手に持っているホットバー枠**へ入れる。
その枠に元のアイテムがあれば **空きスロットへ退避して入れ替える**。その後 Litematica 本来の選択処理を続行する。

## 2. 動作環境・依存
| 項目 | 内容 |
|---|---|
| Minecraft / Loader / Java | 1.19.4 / Fabric Loader 0.15.11 以上 / 17 以上 |
| Fabric API | 0.87.2+1.19.4 以上(クライアント・サーバー両方) |
| Litematica | 0.14.2 以上 0.15 未満(確認: 0.14.7)。**クライアントのみ** |
| MaLiLib | 0.15.3 以上 0.16 未満(確認: 0.15.4)。**クライアントのみ** |
| 導入先 | クライアントとサーバーの**両方**(シングルプレイはクライアントのみ) |

## 3. 機能仕様
### 3.1 発動条件(すべて満たすとき)
1. Mod 有効、かつ呼び出し元の設定が有効(ホイールクリック=`wheelClickFetch`、Easy Place=`easyPlaceIntegration`、精密設置プロトコル使用中は加えて `proPlaceIntegration`)。
2. サバイバル/アドベンチャー(クリエイティブ・観戦では何もしない)。
3. 必要アイテム(アイテム+NBT 一致 `ItemStack.canCombine`)がメインインベントリ36枠に無い。所持していれば**何もせず**Litematica 標準動作。
4. メインインベントリのシュルカーボックス(全色)の中に該当アイテムがある。

### 3.2 取り出し・配置
- 取り出し量: 該当エントリの全量、ただしアイテムの最大スタック数まで(1要求=1スタック)。
- 複数の箱/エントリに該当がある場合: **最大数の該当エントリ**から(同数なら若いスロットの箱)。インベントリ36枠に既に該当アイテムがあるときはシュルカーを一切検索せず、Litematica 標準動作(インベントリ優先)。
- 配置先: **手に持っているホットバー枠**。元アイテムは空きスロットへ退避(通常27枠 → ホットバーの順)。破棄・上書きなし。
- 退避先が無い場合は何も変更せず中断(画面通知なし)。取り出し元の箱が手持ち枠にある場合は、中身を減らした箱を退避。
- 空になったエントリは削除し、全て空なら `BlockEntityTag` ごと除去。未知のエントリ(削除された Mod のアイテム等)は保持。
- 中身を推測しない: データ無し / `LootTable` 付き / `Items` が不正な箱は「該当なし」。

### 3.3 Litematica との連携
- 介入点は `InventoryUtils.schematicWorldPickBlock`(HEAD)の1か所。取得が必要なときだけ、サーバーと同じ取り出し処理をクライアントのインベントリへ**即座に先行適用**(選択中の枠にその場で入る)して要求を送り、元の処理は**常にそのまま続行**する(キャンセル・再実行はしない)。ホイールクリックと同じtickで手持ちに入り、Easy Place は同じ押下で設置まで進む。
- `WorldUtils.doEasyPlaceAction` / `doSchematicWorldPickBlock` への Mixin は呼び出し元の記録のみ(挙動不変)。

## 4. 通信仕様(Fabric API Networking, プロトコル版数 2)
| チャンネル | 向き | 内容 |
|---|---|---|
| `shulkerfetch:hello` | S→C | 参加時: 版数(varint)、Mod版数(string) |
| `shulkerfetch:request` | C→S | 版数, 要求ID, 選択中の枠番号, 要求アイテム(ItemStack, 個数1) |
| `shulkerfetch:response` | S→C | 要求ID, 状態, 取り出し数, 配置スロット |

状態: `OK / NOT_FOUND / INVENTORY_FULL / ALREADY_AVAILABLE / BUSY / DISABLED / BAD_REQUEST / PROTOCOL_MISMATCH`

### サーバー検証(権威)
版数一致、プレイヤー生存・非観戦、別画面(コンテナ/カーソル操作)を開いていない、クールダウン(既定2tick)、手持ち枠番号はサーバー側の値と一致すること(不一致は拒否)、
実インベントリで再検索・再検証、シュルカーのコピーを編集して全変更を同時確定、`sendContentUpdates` で同期後に応答。**拒否(OK以外)のときは `syncState()` で全スロットを再送**し、クライアントの先行適用を権威状態で上書きする。クライアントのローカル適用は予測であり、成功の根拠にしない。

### クライアントの保護
未応答は最大4件、100tick でタイムアウト、失敗した同一アイテムは40tick 再要求・再スキャンしない(毎tick走査しない)、古い応答(ID不一致)は無視、
`hello` 未受信・版数不一致・サーバー未導入時は要求を送らずログに警告のみ(画面通知なし)、切断/参加時に状態をリセット。

## 5. 設定
クライアント(MaLiLib、ホットキー **M+S** で画面、`config/shulkerfetch.json`):
`enabled`(true) / `proPlaceIntegration`(true) / `easyPlaceIntegration`(true) / `wheelClickFetch`(true) / `debugLog`(false)

サーバー(`config/shulkerfetch-server.json`): `enabled`(true) / `cooldownTicks`(5)

## 6. 構成
`common`(Protocol, ShulkerInventory, FetchTransaction, ShulkerFetchMod) / `server`(ServerFetchHandler, ServerConfig) /
`client`(FetchController, ShulkerFetchClient, config/*, mixin/*)。ビルド: `./gradlew build`(Loom 1.11.8, Gradle 8.14.3)。

## 7. 検証状況
**実施済み**: クリーンビルド、ユニットテスト17件、専用サーバー起動、実クライアント(Litematica/MaLiLib 実物, xvfb)+専用サーバーでの
Mixin 適用(エラー0)・先行適用(同tickで手持ち枠に入る)→要求→サーバー検証→同期、同一tickの2要求でサーバー拒否→巻き戻し→最終状態一致、サーバー/クライアント内容の一致、重複・ループなし。

**追加で実施済み**: 実物の Litematica 配置(設計図)に対し、Litematica 本来のルーチン(中ボタン既定経路 `doSchematicWorldPickBlock(true)`、`handleEasyPlace`)で、ホイールクリック取得・Easy Place 取得と実ブロック設置・Slabs only・満杯時通知・存在しない場合の無動作・設定画面を収録確認(video/shulker-autofetch-feature-explained.mp4)。

**未検証**: 物理的なマウス/キーボード入力イベント(中ボタン・使用キーの実押下)、満杯・別画面・クールダウンの実機確認、
Mod未導入サーバー/版数不一致の実機確認、複数人同時接続、他 Mod との併用、通常の Fabric ランチャー(intermediary)での起動。

## 8. 既知の制限
- 探索対象はメインインベントリ36枠のシュルカーのみ(オフハンド・エンダーチェスト・設置済みの箱は対象外)。
- 1要求1スタック。アイテムごとにサーバー往復が1回入る。
- 退避先の空きスロットが無いと動作しない(アイテムは失われない)。
- 対応は Litematica 0.14.x / MaLiLib 0.15.x。内部メソッドが変わると Mixin 適用エラーで起動時に判明する(黙って無効化しない)。

## 9. ライセンス
LGPL-3.0-or-later
