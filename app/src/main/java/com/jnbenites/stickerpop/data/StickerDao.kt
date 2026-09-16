package com.jnbenites.stickerpop.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {

    @Query("SELECT * FROM stickers ORDER BY id ASC")
    fun getAll(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE active = 1")
    suspend fun getActive(): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun getById(id: Int): StickerEntity?

    @Insert
    suspend fun insert(sticker: StickerEntity): Long

    @Update
    suspend fun update(sticker: StickerEntity)

    @Delete
    suspend fun delete(sticker: StickerEntity)
}