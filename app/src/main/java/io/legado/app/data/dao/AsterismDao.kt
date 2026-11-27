package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AsterismEntity
import io.legado.app.data.entities.AsterismMemberEntity
import io.legado.app.data.entities.ConstellationLineEntity

/**
 * 星官 / 星座连线 DAO（表 `asterism`、`asterism_member`、`constellation_line`）。
 *
 * 星官体系与西方星座体系分表：成员查询走 [AsterismMemberEntity]，连线查询走
 * [ConstellationLineEntity]，上层按视图模式（默认星官，§七 第 4 条）各取各表。
 */
@Dao
interface AsterismDao {

    // ── asterism ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM asterism WHERE id = :id")
    fun byId(id: Long): AsterismEntity?

    @Query("SELECT * FROM asterism WHERE code = :code")
    fun byCode(code: String): AsterismEntity?

    @Query("SELECT * FROM asterism ORDER BY parentGroup ASC, code ASC")
    fun allAsterisms(): List<AsterismEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAsterism(vararg asterisms: AsterismEntity)

    @Query("DELETE FROM asterism")
    fun deleteAllAsterisms()

    // ── asterism_member ──────────────────────────────────────────────────────

    @Query("SELECT * FROM asterism_member WHERE asterismId = :asterismId ORDER BY memberIndex ASC")
    fun membersOf(asterismId: Long): List<AsterismMemberEntity>

    @Query("SELECT * FROM asterism_member WHERE starId = :starId")
    fun membersOfStar(starId: Long): List<AsterismMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMember(vararg members: AsterismMemberEntity)

    @Query("DELETE FROM asterism_member")
    fun deleteAllMembers()

    // ── constellation_line ───────────────────────────────────────────────────

    @Query("SELECT * FROM constellation_line WHERE constellationId = :constellationId")
    fun linesOf(constellationId: String): List<ConstellationLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLine(vararg lines: ConstellationLineEntity)

    @Query("DELETE FROM constellation_line")
    fun deleteAllLines()
}
