package com.svcmonitor.app.db

import com.svcmonitor.app.StatusParser

fun StatusParser.SvcEvent.toEntity(fpChain: String, createdAtNs: Long): SvcEventEntity {
    return SvcEventEntity(
        seq = seq,
        nr = nr,
        name = name,
        tgid = tgid,
        pid = pid,
        uid = uid,
        comm = comm,
        pc = pc,
        caller = caller,
        fp = fp,
        sp = sp,
        bt = bt.joinToString("|") { java.lang.Long.toHexString(it) },
        cloneFn = cloneFn,
        ret = ret,
        a0 = a0,
        a1 = a1,
        a2 = a2,
        a3 = a3,
        a4 = a4,
        a5 = a5,
        desc = desc,
        fpChain = fpChain,
        createdAtNs = createdAtNs
    )
}

fun SvcEventEntity.toSvcEvent(): StatusParser.SvcEvent {
    val btList = if (bt.isBlank()) {
        emptyList()
    } else {
        bt.split("|").mapNotNull { it.trim().toLongOrNull(16) }
    }
    return StatusParser.SvcEvent(
        seq = seq,
        nr = nr,
        name = name,
        tgid = tgid,
        pid = pid,
        uid = uid,
        comm = comm,
        pc = pc,
        caller = caller,
        fp = fp,
        sp = sp,
        bt = btList,
        cloneFn = cloneFn,
        ret = ret,
        a0 = a0,
        a1 = a1,
        a2 = a2,
        a3 = a3,
        a4 = a4,
        a5 = a5,
        desc = desc
    )
}
