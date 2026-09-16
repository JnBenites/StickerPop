package com.jnbenites.stickerpop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val path: String,
    val active: Boolean = false,
    val posX: Int = 100,
    val posY: Int = 100,
    val size: Int = 300
)