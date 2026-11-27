package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.StarEntity

/**
 * 恒星表 DAO（表 `star`）。
 *
 * 查询口径：矩形拾取按赤经/赤纬区间；赤经跨 0°/360° 包裹时由上层拆成两个区间调用，
 * SQL 层只做纯区间扫描，不写包裹逻辑（保持查询可索引）。
 */
@Dao
interface StarDao {

    @Query("SELECT * FROM star WHERE id = :id")
    fun byId(id: Long): StarEntity?

    @Query(
        """SELECT * FROM star
            WHERE raDeg BETWEEN :raMin AND :raMax
              AND decDeg BETWEEN :decMin AND :decMax
            ORDER BY magVt ASC"""
    )
    fun queryRect(raMin: Double, raMax: Double, decMin: Double, decMax: Double): List<StarEntity>

    @Query("SELECT * FROM star WHERE constellation = :constellation ORDER BY magVt ASC")
    fun byConstellation(constellation: String): List<StarEntity>

    @Query("SELECT COUNT(*) FROM star")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg stars: StarEntity)

    @Query("DELETE FROM star")
    fun deleteAll()
}
