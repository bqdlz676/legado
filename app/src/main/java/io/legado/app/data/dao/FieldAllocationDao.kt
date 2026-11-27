package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.FieldAllocationEntity

/**
 * 分野表 DAO（表 `field_allocation`）。
 *
 * 双向闭环：点星官查州域走 [byAsterismId]；点州域反查星官走 [byRegionName]
 * （§十四 第 3 条：点井宿→雍州脉冲+州名；点雍州→井鬼高亮）。
 */
@Dao
interface FieldAllocationDao {

    @Query("SELECT * FROM field_allocation WHERE asterismId = :asterismId")
    fun byAsterismId(asterismId: Long): List<FieldAllocationEntity>

    @Query("SELECT * FROM field_allocation WHERE regionName = :regionName ORDER BY isVariant ASC")
    fun byRegionName(regionName: String): List<FieldAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg allocations: FieldAllocationEntity)

    @Query("DELETE FROM field_allocation")
    fun deleteAll()
}
