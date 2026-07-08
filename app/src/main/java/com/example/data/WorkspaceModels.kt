package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class Workspace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val workspaceId: Long? = null
)

@Entity(tableName = "annotations")
data class Annotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String, // "MESSAGE", "PARTICIPANT", "REPORT", "MEDIA"
    val targetId: String, // ID of the target
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: String // comma separated tag IDs or names
)
