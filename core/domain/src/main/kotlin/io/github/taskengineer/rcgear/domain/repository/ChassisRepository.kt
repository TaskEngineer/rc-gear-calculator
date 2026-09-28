package io.github.taskengineer.rcgear.domain.repository

import io.github.taskengineer.rcgear.domain.model.Chassis
import io.github.taskengineer.rcgear.domain.model.ChassisOverride
import io.github.taskengineer.rcgear.domain.model.Maker
import io.github.taskengineer.rcgear.domain.model.UserChassis
import kotlinx.coroutines.flow.Flow

/**
 * シャーシDBの窓口（REF-2 / S-5）。
 *
 * 「同梱 JSON（読み取り専用）＋ Room の上書き差分 ＋ ユーザー定義シャーシ」という合成は
 * 実装側の都合なので、この interface には現れない。呼び出し側から見えるのは
 * 「合成済みの値（getChassisById）」と「標準値（getStandardChassisById）」の 2 種類だけ。
 *
 * ユーザー定義シャーシ（F-5）だけは CRUD が要るので専用のメソッドを持つ。
 * 一覧・単発取得の結果には同梱エントリと区別なく混ざる
 * （id の `user_` 接頭辞で見分けられる。[UserChassis.isUserDefined]）。
 */
interface ChassisRepository {

    /** 全メーカーのシャーシ一覧（上書き合成済み）を監視する。並び順は定義順を保持する */
    fun getAllMakers(): Flow<List<Maker>>

    /** ID 指定で1台分（上書き合成済み）を取得する。存在しなければ null */
    suspend fun getChassisById(chassisId: String): Chassis?

    /** ID 指定で標準値（上書き適用前）を取得する。存在しなければ null */
    suspend fun getStandardChassisById(chassisId: String): Chassis?

    /**
     * シャーシの上書きを登録・更新する。
     * すべて null（= 標準値と同じ）の場合はリセットとして扱う。
     */
    suspend fun overrideChassis(
        chassisId: String,
        internalRatio: Double?,
        defaultTireMm: Int?,
        note: String?
    )

    /** 全上書きの単発取得（エクスポート用） */
    suspend fun getAllOverridesOnce(): List<ChassisOverride>

    /**
     * インポートした上書きの一括復元（BUG-3）。updatedAt は元データのまま保持する。
     * 標準DBに存在しない chassisId のチェックは呼び出し側が行う。
     */
    suspend fun restoreAllOverrides(overrides: List<ChassisOverride>)

    /** 上書きをリセットし、標準値に戻す */
    suspend fun resetOverride(chassisId: String)

    /** 全上書きをリセットする（CONFIG 画面の「全データ削除」用） */
    suspend fun resetAllOverrides()

    // ----- ユーザー定義シャーシ（F-5） -----

    /** ユーザー定義シャーシの一覧を監視する */
    fun observeUserChassis(): Flow<List<UserChassis>>

    suspend fun getUserChassis(id: String): UserChassis?

    /**
     * 追加する。id（`user_<uuid>`）は実装が採番する。
     * @return 採番された id
     */
    suspend fun addUserChassis(chassis: UserChassis): String

    /** 上書き保存。`createdAt` は維持し `updatedAt` だけ進める */
    suspend fun updateUserChassis(chassis: UserChassis)

    /**
     * 削除する。**このシャーシを使っている車は残る**（外部キーを張っていない）。
     * 車の側は「不明なシャーシ」として id をそのまま出す — 消えたように見せないため。
     */
    suspend fun deleteUserChassis(id: String)

    /** エクスポート用の単発取得 */
    suspend fun getAllUserChassisOnce(): List<UserChassis>

    /** インポートの一括復元。id による upsert で冪等 */
    suspend fun restoreAllUserChassis(chassis: List<UserChassis>)

    /** 全件削除（CONFIG 画面の「全データ削除」用） */
    suspend fun deleteAllUserChassis()
}
