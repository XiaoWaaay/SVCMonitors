package com.svcmonitor.app.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["tgid"]),
        Index(value = ["pid"]),
        Index(value = ["nr"]),
        Index(value = ["comm"]),
        Index(value = ["createdAtNs"])
    ]
)
data class SvcEventEntity(
    @PrimaryKey val seq: Long,
    val nr: Int,
    val name: String,
    val tgid: Int,
    val pid: Int,
    val uid: Int,
    val comm: String,
    val pc: Long,
    val caller: Long,
    val fp: Long,
    val sp: Long,
    val bt: String,
    val cloneFn: Long,
    val ret: Long,
    val a0: Long,
    val a1: Long,
    val a2: Long,
    val a3: Long,
    val a4: Long,
    val a5: Long,
    val desc: String,
    val fpChain: String,
    val createdAtNs: Long
)

@Fts4(contentEntity = SvcEventEntity::class)
@Entity(tableName = "events_fts")
data class SvcEventFtsEntity(
    val desc: String,
    val comm: String,
    val name: String,
    val fpChain: String,
    val bt: String
)
