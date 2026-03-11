package com.svcmonitor.app.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@SuppressWarnings({"unchecked", "deprecation"})
public final class SvcEventDao_Impl implements SvcEventDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SvcEventEntity> __insertionAdapterOfSvcEventEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  private final SharedSQLiteStatement __preparedStmtOfUpdateFpChain;

  public SvcEventDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSvcEventEntity = new EntityInsertionAdapter<SvcEventEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `events` (`seq`,`nr`,`name`,`tgid`,`pid`,`uid`,`comm`,`pc`,`caller`,`fp`,`sp`,`bt`,`cloneFn`,`ret`,`a0`,`a1`,`a2`,`a3`,`a4`,`a5`,`desc`,`fpChain`,`createdAtNs`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SvcEventEntity entity) {
        statement.bindLong(1, entity.getSeq());
        statement.bindLong(2, entity.getNr());
        if (entity.getName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getName());
        }
        statement.bindLong(4, entity.getTgid());
        statement.bindLong(5, entity.getPid());
        statement.bindLong(6, entity.getUid());
        if (entity.getComm() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getComm());
        }
        statement.bindLong(8, entity.getPc());
        statement.bindLong(9, entity.getCaller());
        statement.bindLong(10, entity.getFp());
        statement.bindLong(11, entity.getSp());
        if (entity.getBt() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getBt());
        }
        statement.bindLong(13, entity.getCloneFn());
        statement.bindLong(14, entity.getRet());
        statement.bindLong(15, entity.getA0());
        statement.bindLong(16, entity.getA1());
        statement.bindLong(17, entity.getA2());
        statement.bindLong(18, entity.getA3());
        statement.bindLong(19, entity.getA4());
        statement.bindLong(20, entity.getA5());
        if (entity.getDesc() == null) {
          statement.bindNull(21);
        } else {
          statement.bindString(21, entity.getDesc());
        }
        if (entity.getFpChain() == null) {
          statement.bindNull(22);
        } else {
          statement.bindString(22, entity.getFpChain());
        }
        statement.bindLong(23, entity.getCreatedAtNs());
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM events";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateFpChain = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE events SET fpChain = ? WHERE seq = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsertAll(final List<SvcEventEntity> events,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSvcEventEntity.insert(events);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateFpChain(final long seq, final String fpChain,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateFpChain.acquire();
        int _argIndex = 1;
        if (fpChain == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, fpChain);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, seq);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateFpChain.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object latest(final int limit,
      final Continuation<? super List<SvcEventEntity>> $completion) {
    final String _sql = "SELECT * FROM events ORDER BY seq DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SvcEventEntity>>() {
      @Override
      @NonNull
      public List<SvcEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfNr = CursorUtil.getColumnIndexOrThrow(_cursor, "nr");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTgid = CursorUtil.getColumnIndexOrThrow(_cursor, "tgid");
          final int _cursorIndexOfPid = CursorUtil.getColumnIndexOrThrow(_cursor, "pid");
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfComm = CursorUtil.getColumnIndexOrThrow(_cursor, "comm");
          final int _cursorIndexOfPc = CursorUtil.getColumnIndexOrThrow(_cursor, "pc");
          final int _cursorIndexOfCaller = CursorUtil.getColumnIndexOrThrow(_cursor, "caller");
          final int _cursorIndexOfFp = CursorUtil.getColumnIndexOrThrow(_cursor, "fp");
          final int _cursorIndexOfSp = CursorUtil.getColumnIndexOrThrow(_cursor, "sp");
          final int _cursorIndexOfBt = CursorUtil.getColumnIndexOrThrow(_cursor, "bt");
          final int _cursorIndexOfCloneFn = CursorUtil.getColumnIndexOrThrow(_cursor, "cloneFn");
          final int _cursorIndexOfRet = CursorUtil.getColumnIndexOrThrow(_cursor, "ret");
          final int _cursorIndexOfA0 = CursorUtil.getColumnIndexOrThrow(_cursor, "a0");
          final int _cursorIndexOfA1 = CursorUtil.getColumnIndexOrThrow(_cursor, "a1");
          final int _cursorIndexOfA2 = CursorUtil.getColumnIndexOrThrow(_cursor, "a2");
          final int _cursorIndexOfA3 = CursorUtil.getColumnIndexOrThrow(_cursor, "a3");
          final int _cursorIndexOfA4 = CursorUtil.getColumnIndexOrThrow(_cursor, "a4");
          final int _cursorIndexOfA5 = CursorUtil.getColumnIndexOrThrow(_cursor, "a5");
          final int _cursorIndexOfDesc = CursorUtil.getColumnIndexOrThrow(_cursor, "desc");
          final int _cursorIndexOfFpChain = CursorUtil.getColumnIndexOrThrow(_cursor, "fpChain");
          final int _cursorIndexOfCreatedAtNs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtNs");
          final List<SvcEventEntity> _result = new ArrayList<SvcEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SvcEventEntity _item;
            final long _tmpSeq;
            _tmpSeq = _cursor.getLong(_cursorIndexOfSeq);
            final int _tmpNr;
            _tmpNr = _cursor.getInt(_cursorIndexOfNr);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final int _tmpTgid;
            _tmpTgid = _cursor.getInt(_cursorIndexOfTgid);
            final int _tmpPid;
            _tmpPid = _cursor.getInt(_cursorIndexOfPid);
            final int _tmpUid;
            _tmpUid = _cursor.getInt(_cursorIndexOfUid);
            final String _tmpComm;
            if (_cursor.isNull(_cursorIndexOfComm)) {
              _tmpComm = null;
            } else {
              _tmpComm = _cursor.getString(_cursorIndexOfComm);
            }
            final long _tmpPc;
            _tmpPc = _cursor.getLong(_cursorIndexOfPc);
            final long _tmpCaller;
            _tmpCaller = _cursor.getLong(_cursorIndexOfCaller);
            final long _tmpFp;
            _tmpFp = _cursor.getLong(_cursorIndexOfFp);
            final long _tmpSp;
            _tmpSp = _cursor.getLong(_cursorIndexOfSp);
            final String _tmpBt;
            if (_cursor.isNull(_cursorIndexOfBt)) {
              _tmpBt = null;
            } else {
              _tmpBt = _cursor.getString(_cursorIndexOfBt);
            }
            final long _tmpCloneFn;
            _tmpCloneFn = _cursor.getLong(_cursorIndexOfCloneFn);
            final long _tmpRet;
            _tmpRet = _cursor.getLong(_cursorIndexOfRet);
            final long _tmpA0;
            _tmpA0 = _cursor.getLong(_cursorIndexOfA0);
            final long _tmpA1;
            _tmpA1 = _cursor.getLong(_cursorIndexOfA1);
            final long _tmpA2;
            _tmpA2 = _cursor.getLong(_cursorIndexOfA2);
            final long _tmpA3;
            _tmpA3 = _cursor.getLong(_cursorIndexOfA3);
            final long _tmpA4;
            _tmpA4 = _cursor.getLong(_cursorIndexOfA4);
            final long _tmpA5;
            _tmpA5 = _cursor.getLong(_cursorIndexOfA5);
            final String _tmpDesc;
            if (_cursor.isNull(_cursorIndexOfDesc)) {
              _tmpDesc = null;
            } else {
              _tmpDesc = _cursor.getString(_cursorIndexOfDesc);
            }
            final String _tmpFpChain;
            if (_cursor.isNull(_cursorIndexOfFpChain)) {
              _tmpFpChain = null;
            } else {
              _tmpFpChain = _cursor.getString(_cursorIndexOfFpChain);
            }
            final long _tmpCreatedAtNs;
            _tmpCreatedAtNs = _cursor.getLong(_cursorIndexOfCreatedAtNs);
            _item = new SvcEventEntity(_tmpSeq,_tmpNr,_tmpName,_tmpTgid,_tmpPid,_tmpUid,_tmpComm,_tmpPc,_tmpCaller,_tmpFp,_tmpSp,_tmpBt,_tmpCloneFn,_tmpRet,_tmpA0,_tmpA1,_tmpA2,_tmpA3,_tmpA4,_tmpA5,_tmpDesc,_tmpFpChain,_tmpCreatedAtNs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object byTid(final int tid, final int limit,
      final Continuation<? super List<SvcEventEntity>> $completion) {
    final String _sql = "SELECT * FROM events WHERE pid = ? ORDER BY seq DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, tid);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SvcEventEntity>>() {
      @Override
      @NonNull
      public List<SvcEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfNr = CursorUtil.getColumnIndexOrThrow(_cursor, "nr");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTgid = CursorUtil.getColumnIndexOrThrow(_cursor, "tgid");
          final int _cursorIndexOfPid = CursorUtil.getColumnIndexOrThrow(_cursor, "pid");
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfComm = CursorUtil.getColumnIndexOrThrow(_cursor, "comm");
          final int _cursorIndexOfPc = CursorUtil.getColumnIndexOrThrow(_cursor, "pc");
          final int _cursorIndexOfCaller = CursorUtil.getColumnIndexOrThrow(_cursor, "caller");
          final int _cursorIndexOfFp = CursorUtil.getColumnIndexOrThrow(_cursor, "fp");
          final int _cursorIndexOfSp = CursorUtil.getColumnIndexOrThrow(_cursor, "sp");
          final int _cursorIndexOfBt = CursorUtil.getColumnIndexOrThrow(_cursor, "bt");
          final int _cursorIndexOfCloneFn = CursorUtil.getColumnIndexOrThrow(_cursor, "cloneFn");
          final int _cursorIndexOfRet = CursorUtil.getColumnIndexOrThrow(_cursor, "ret");
          final int _cursorIndexOfA0 = CursorUtil.getColumnIndexOrThrow(_cursor, "a0");
          final int _cursorIndexOfA1 = CursorUtil.getColumnIndexOrThrow(_cursor, "a1");
          final int _cursorIndexOfA2 = CursorUtil.getColumnIndexOrThrow(_cursor, "a2");
          final int _cursorIndexOfA3 = CursorUtil.getColumnIndexOrThrow(_cursor, "a3");
          final int _cursorIndexOfA4 = CursorUtil.getColumnIndexOrThrow(_cursor, "a4");
          final int _cursorIndexOfA5 = CursorUtil.getColumnIndexOrThrow(_cursor, "a5");
          final int _cursorIndexOfDesc = CursorUtil.getColumnIndexOrThrow(_cursor, "desc");
          final int _cursorIndexOfFpChain = CursorUtil.getColumnIndexOrThrow(_cursor, "fpChain");
          final int _cursorIndexOfCreatedAtNs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtNs");
          final List<SvcEventEntity> _result = new ArrayList<SvcEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SvcEventEntity _item;
            final long _tmpSeq;
            _tmpSeq = _cursor.getLong(_cursorIndexOfSeq);
            final int _tmpNr;
            _tmpNr = _cursor.getInt(_cursorIndexOfNr);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final int _tmpTgid;
            _tmpTgid = _cursor.getInt(_cursorIndexOfTgid);
            final int _tmpPid;
            _tmpPid = _cursor.getInt(_cursorIndexOfPid);
            final int _tmpUid;
            _tmpUid = _cursor.getInt(_cursorIndexOfUid);
            final String _tmpComm;
            if (_cursor.isNull(_cursorIndexOfComm)) {
              _tmpComm = null;
            } else {
              _tmpComm = _cursor.getString(_cursorIndexOfComm);
            }
            final long _tmpPc;
            _tmpPc = _cursor.getLong(_cursorIndexOfPc);
            final long _tmpCaller;
            _tmpCaller = _cursor.getLong(_cursorIndexOfCaller);
            final long _tmpFp;
            _tmpFp = _cursor.getLong(_cursorIndexOfFp);
            final long _tmpSp;
            _tmpSp = _cursor.getLong(_cursorIndexOfSp);
            final String _tmpBt;
            if (_cursor.isNull(_cursorIndexOfBt)) {
              _tmpBt = null;
            } else {
              _tmpBt = _cursor.getString(_cursorIndexOfBt);
            }
            final long _tmpCloneFn;
            _tmpCloneFn = _cursor.getLong(_cursorIndexOfCloneFn);
            final long _tmpRet;
            _tmpRet = _cursor.getLong(_cursorIndexOfRet);
            final long _tmpA0;
            _tmpA0 = _cursor.getLong(_cursorIndexOfA0);
            final long _tmpA1;
            _tmpA1 = _cursor.getLong(_cursorIndexOfA1);
            final long _tmpA2;
            _tmpA2 = _cursor.getLong(_cursorIndexOfA2);
            final long _tmpA3;
            _tmpA3 = _cursor.getLong(_cursorIndexOfA3);
            final long _tmpA4;
            _tmpA4 = _cursor.getLong(_cursorIndexOfA4);
            final long _tmpA5;
            _tmpA5 = _cursor.getLong(_cursorIndexOfA5);
            final String _tmpDesc;
            if (_cursor.isNull(_cursorIndexOfDesc)) {
              _tmpDesc = null;
            } else {
              _tmpDesc = _cursor.getString(_cursorIndexOfDesc);
            }
            final String _tmpFpChain;
            if (_cursor.isNull(_cursorIndexOfFpChain)) {
              _tmpFpChain = null;
            } else {
              _tmpFpChain = _cursor.getString(_cursorIndexOfFpChain);
            }
            final long _tmpCreatedAtNs;
            _tmpCreatedAtNs = _cursor.getLong(_cursorIndexOfCreatedAtNs);
            _item = new SvcEventEntity(_tmpSeq,_tmpNr,_tmpName,_tmpTgid,_tmpPid,_tmpUid,_tmpComm,_tmpPc,_tmpCaller,_tmpFp,_tmpSp,_tmpBt,_tmpCloneFn,_tmpRet,_tmpA0,_tmpA1,_tmpA2,_tmpA3,_tmpA4,_tmpA5,_tmpDesc,_tmpFpChain,_tmpCreatedAtNs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object search(final String query, final int limit,
      final Continuation<? super List<SvcEventEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT events.* FROM events\n"
            + "        JOIN events_fts ON events.rowid = events_fts.rowid\n"
            + "        WHERE events_fts MATCH ?\n"
            + "        ORDER BY events.seq DESC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SvcEventEntity>>() {
      @Override
      @NonNull
      public List<SvcEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfNr = CursorUtil.getColumnIndexOrThrow(_cursor, "nr");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTgid = CursorUtil.getColumnIndexOrThrow(_cursor, "tgid");
          final int _cursorIndexOfPid = CursorUtil.getColumnIndexOrThrow(_cursor, "pid");
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfComm = CursorUtil.getColumnIndexOrThrow(_cursor, "comm");
          final int _cursorIndexOfPc = CursorUtil.getColumnIndexOrThrow(_cursor, "pc");
          final int _cursorIndexOfCaller = CursorUtil.getColumnIndexOrThrow(_cursor, "caller");
          final int _cursorIndexOfFp = CursorUtil.getColumnIndexOrThrow(_cursor, "fp");
          final int _cursorIndexOfSp = CursorUtil.getColumnIndexOrThrow(_cursor, "sp");
          final int _cursorIndexOfBt = CursorUtil.getColumnIndexOrThrow(_cursor, "bt");
          final int _cursorIndexOfCloneFn = CursorUtil.getColumnIndexOrThrow(_cursor, "cloneFn");
          final int _cursorIndexOfRet = CursorUtil.getColumnIndexOrThrow(_cursor, "ret");
          final int _cursorIndexOfA0 = CursorUtil.getColumnIndexOrThrow(_cursor, "a0");
          final int _cursorIndexOfA1 = CursorUtil.getColumnIndexOrThrow(_cursor, "a1");
          final int _cursorIndexOfA2 = CursorUtil.getColumnIndexOrThrow(_cursor, "a2");
          final int _cursorIndexOfA3 = CursorUtil.getColumnIndexOrThrow(_cursor, "a3");
          final int _cursorIndexOfA4 = CursorUtil.getColumnIndexOrThrow(_cursor, "a4");
          final int _cursorIndexOfA5 = CursorUtil.getColumnIndexOrThrow(_cursor, "a5");
          final int _cursorIndexOfDesc = CursorUtil.getColumnIndexOrThrow(_cursor, "desc");
          final int _cursorIndexOfFpChain = CursorUtil.getColumnIndexOrThrow(_cursor, "fpChain");
          final int _cursorIndexOfCreatedAtNs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtNs");
          final List<SvcEventEntity> _result = new ArrayList<SvcEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SvcEventEntity _item;
            final long _tmpSeq;
            _tmpSeq = _cursor.getLong(_cursorIndexOfSeq);
            final int _tmpNr;
            _tmpNr = _cursor.getInt(_cursorIndexOfNr);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final int _tmpTgid;
            _tmpTgid = _cursor.getInt(_cursorIndexOfTgid);
            final int _tmpPid;
            _tmpPid = _cursor.getInt(_cursorIndexOfPid);
            final int _tmpUid;
            _tmpUid = _cursor.getInt(_cursorIndexOfUid);
            final String _tmpComm;
            if (_cursor.isNull(_cursorIndexOfComm)) {
              _tmpComm = null;
            } else {
              _tmpComm = _cursor.getString(_cursorIndexOfComm);
            }
            final long _tmpPc;
            _tmpPc = _cursor.getLong(_cursorIndexOfPc);
            final long _tmpCaller;
            _tmpCaller = _cursor.getLong(_cursorIndexOfCaller);
            final long _tmpFp;
            _tmpFp = _cursor.getLong(_cursorIndexOfFp);
            final long _tmpSp;
            _tmpSp = _cursor.getLong(_cursorIndexOfSp);
            final String _tmpBt;
            if (_cursor.isNull(_cursorIndexOfBt)) {
              _tmpBt = null;
            } else {
              _tmpBt = _cursor.getString(_cursorIndexOfBt);
            }
            final long _tmpCloneFn;
            _tmpCloneFn = _cursor.getLong(_cursorIndexOfCloneFn);
            final long _tmpRet;
            _tmpRet = _cursor.getLong(_cursorIndexOfRet);
            final long _tmpA0;
            _tmpA0 = _cursor.getLong(_cursorIndexOfA0);
            final long _tmpA1;
            _tmpA1 = _cursor.getLong(_cursorIndexOfA1);
            final long _tmpA2;
            _tmpA2 = _cursor.getLong(_cursorIndexOfA2);
            final long _tmpA3;
            _tmpA3 = _cursor.getLong(_cursorIndexOfA3);
            final long _tmpA4;
            _tmpA4 = _cursor.getLong(_cursorIndexOfA4);
            final long _tmpA5;
            _tmpA5 = _cursor.getLong(_cursorIndexOfA5);
            final String _tmpDesc;
            if (_cursor.isNull(_cursorIndexOfDesc)) {
              _tmpDesc = null;
            } else {
              _tmpDesc = _cursor.getString(_cursorIndexOfDesc);
            }
            final String _tmpFpChain;
            if (_cursor.isNull(_cursorIndexOfFpChain)) {
              _tmpFpChain = null;
            } else {
              _tmpFpChain = _cursor.getString(_cursorIndexOfFpChain);
            }
            final long _tmpCreatedAtNs;
            _tmpCreatedAtNs = _cursor.getLong(_cursorIndexOfCreatedAtNs);
            _item = new SvcEventEntity(_tmpSeq,_tmpNr,_tmpName,_tmpTgid,_tmpPid,_tmpUid,_tmpComm,_tmpPc,_tmpCaller,_tmpFp,_tmpSp,_tmpBt,_tmpCloneFn,_tmpRet,_tmpA0,_tmpA1,_tmpA2,_tmpA3,_tmpA4,_tmpA5,_tmpDesc,_tmpFpChain,_tmpCreatedAtNs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object searchAll(final String query, final int limit,
      final Continuation<? super List<SvcEventEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM events\n"
            + "        WHERE (\n"
            + "            name LIKE '%' || ? || '%' OR\n"
            + "            comm LIKE '%' || ? || '%' OR\n"
            + "            desc LIKE '%' || ? || '%' OR\n"
            + "            fpChain LIKE '%' || ? || '%' OR\n"
            + "            bt LIKE '%' || ? || '%' OR\n"
            + "            CAST(seq AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(nr AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(tgid AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(pid AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(uid AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(ret AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a0 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a1 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a2 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a3 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a4 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a5 AS TEXT) LIKE '%' || ? || '%'\n"
            + "        )\n"
            + "        ORDER BY seq DESC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 18);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 2;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 3;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 4;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 5;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 6;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 7;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 8;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 9;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 10;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 11;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 12;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 13;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 14;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 15;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 16;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 17;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 18;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SvcEventEntity>>() {
      @Override
      @NonNull
      public List<SvcEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfNr = CursorUtil.getColumnIndexOrThrow(_cursor, "nr");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTgid = CursorUtil.getColumnIndexOrThrow(_cursor, "tgid");
          final int _cursorIndexOfPid = CursorUtil.getColumnIndexOrThrow(_cursor, "pid");
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfComm = CursorUtil.getColumnIndexOrThrow(_cursor, "comm");
          final int _cursorIndexOfPc = CursorUtil.getColumnIndexOrThrow(_cursor, "pc");
          final int _cursorIndexOfCaller = CursorUtil.getColumnIndexOrThrow(_cursor, "caller");
          final int _cursorIndexOfFp = CursorUtil.getColumnIndexOrThrow(_cursor, "fp");
          final int _cursorIndexOfSp = CursorUtil.getColumnIndexOrThrow(_cursor, "sp");
          final int _cursorIndexOfBt = CursorUtil.getColumnIndexOrThrow(_cursor, "bt");
          final int _cursorIndexOfCloneFn = CursorUtil.getColumnIndexOrThrow(_cursor, "cloneFn");
          final int _cursorIndexOfRet = CursorUtil.getColumnIndexOrThrow(_cursor, "ret");
          final int _cursorIndexOfA0 = CursorUtil.getColumnIndexOrThrow(_cursor, "a0");
          final int _cursorIndexOfA1 = CursorUtil.getColumnIndexOrThrow(_cursor, "a1");
          final int _cursorIndexOfA2 = CursorUtil.getColumnIndexOrThrow(_cursor, "a2");
          final int _cursorIndexOfA3 = CursorUtil.getColumnIndexOrThrow(_cursor, "a3");
          final int _cursorIndexOfA4 = CursorUtil.getColumnIndexOrThrow(_cursor, "a4");
          final int _cursorIndexOfA5 = CursorUtil.getColumnIndexOrThrow(_cursor, "a5");
          final int _cursorIndexOfDesc = CursorUtil.getColumnIndexOrThrow(_cursor, "desc");
          final int _cursorIndexOfFpChain = CursorUtil.getColumnIndexOrThrow(_cursor, "fpChain");
          final int _cursorIndexOfCreatedAtNs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtNs");
          final List<SvcEventEntity> _result = new ArrayList<SvcEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SvcEventEntity _item;
            final long _tmpSeq;
            _tmpSeq = _cursor.getLong(_cursorIndexOfSeq);
            final int _tmpNr;
            _tmpNr = _cursor.getInt(_cursorIndexOfNr);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final int _tmpTgid;
            _tmpTgid = _cursor.getInt(_cursorIndexOfTgid);
            final int _tmpPid;
            _tmpPid = _cursor.getInt(_cursorIndexOfPid);
            final int _tmpUid;
            _tmpUid = _cursor.getInt(_cursorIndexOfUid);
            final String _tmpComm;
            if (_cursor.isNull(_cursorIndexOfComm)) {
              _tmpComm = null;
            } else {
              _tmpComm = _cursor.getString(_cursorIndexOfComm);
            }
            final long _tmpPc;
            _tmpPc = _cursor.getLong(_cursorIndexOfPc);
            final long _tmpCaller;
            _tmpCaller = _cursor.getLong(_cursorIndexOfCaller);
            final long _tmpFp;
            _tmpFp = _cursor.getLong(_cursorIndexOfFp);
            final long _tmpSp;
            _tmpSp = _cursor.getLong(_cursorIndexOfSp);
            final String _tmpBt;
            if (_cursor.isNull(_cursorIndexOfBt)) {
              _tmpBt = null;
            } else {
              _tmpBt = _cursor.getString(_cursorIndexOfBt);
            }
            final long _tmpCloneFn;
            _tmpCloneFn = _cursor.getLong(_cursorIndexOfCloneFn);
            final long _tmpRet;
            _tmpRet = _cursor.getLong(_cursorIndexOfRet);
            final long _tmpA0;
            _tmpA0 = _cursor.getLong(_cursorIndexOfA0);
            final long _tmpA1;
            _tmpA1 = _cursor.getLong(_cursorIndexOfA1);
            final long _tmpA2;
            _tmpA2 = _cursor.getLong(_cursorIndexOfA2);
            final long _tmpA3;
            _tmpA3 = _cursor.getLong(_cursorIndexOfA3);
            final long _tmpA4;
            _tmpA4 = _cursor.getLong(_cursorIndexOfA4);
            final long _tmpA5;
            _tmpA5 = _cursor.getLong(_cursorIndexOfA5);
            final String _tmpDesc;
            if (_cursor.isNull(_cursorIndexOfDesc)) {
              _tmpDesc = null;
            } else {
              _tmpDesc = _cursor.getString(_cursorIndexOfDesc);
            }
            final String _tmpFpChain;
            if (_cursor.isNull(_cursorIndexOfFpChain)) {
              _tmpFpChain = null;
            } else {
              _tmpFpChain = _cursor.getString(_cursorIndexOfFpChain);
            }
            final long _tmpCreatedAtNs;
            _tmpCreatedAtNs = _cursor.getLong(_cursorIndexOfCreatedAtNs);
            _item = new SvcEventEntity(_tmpSeq,_tmpNr,_tmpName,_tmpTgid,_tmpPid,_tmpUid,_tmpComm,_tmpPc,_tmpCaller,_tmpFp,_tmpSp,_tmpBt,_tmpCloneFn,_tmpRet,_tmpA0,_tmpA1,_tmpA2,_tmpA3,_tmpA4,_tmpA5,_tmpDesc,_tmpFpChain,_tmpCreatedAtNs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object searchAllByTid(final String query, final int tid, final int limit,
      final Continuation<? super List<SvcEventEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM events\n"
            + "        WHERE pid = ? AND (\n"
            + "            name LIKE '%' || ? || '%' OR\n"
            + "            comm LIKE '%' || ? || '%' OR\n"
            + "            desc LIKE '%' || ? || '%' OR\n"
            + "            fpChain LIKE '%' || ? || '%' OR\n"
            + "            bt LIKE '%' || ? || '%' OR\n"
            + "            CAST(seq AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(nr AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(tgid AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(pid AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(uid AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(ret AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a0 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a1 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a2 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a3 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a4 AS TEXT) LIKE '%' || ? || '%' OR\n"
            + "            CAST(a5 AS TEXT) LIKE '%' || ? || '%'\n"
            + "        )\n"
            + "        ORDER BY seq DESC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 19);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, tid);
    _argIndex = 2;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 3;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 4;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 5;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 6;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 7;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 8;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 9;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 10;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 11;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 12;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 13;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 14;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 15;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 16;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 17;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 18;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 19;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SvcEventEntity>>() {
      @Override
      @NonNull
      public List<SvcEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfNr = CursorUtil.getColumnIndexOrThrow(_cursor, "nr");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTgid = CursorUtil.getColumnIndexOrThrow(_cursor, "tgid");
          final int _cursorIndexOfPid = CursorUtil.getColumnIndexOrThrow(_cursor, "pid");
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfComm = CursorUtil.getColumnIndexOrThrow(_cursor, "comm");
          final int _cursorIndexOfPc = CursorUtil.getColumnIndexOrThrow(_cursor, "pc");
          final int _cursorIndexOfCaller = CursorUtil.getColumnIndexOrThrow(_cursor, "caller");
          final int _cursorIndexOfFp = CursorUtil.getColumnIndexOrThrow(_cursor, "fp");
          final int _cursorIndexOfSp = CursorUtil.getColumnIndexOrThrow(_cursor, "sp");
          final int _cursorIndexOfBt = CursorUtil.getColumnIndexOrThrow(_cursor, "bt");
          final int _cursorIndexOfCloneFn = CursorUtil.getColumnIndexOrThrow(_cursor, "cloneFn");
          final int _cursorIndexOfRet = CursorUtil.getColumnIndexOrThrow(_cursor, "ret");
          final int _cursorIndexOfA0 = CursorUtil.getColumnIndexOrThrow(_cursor, "a0");
          final int _cursorIndexOfA1 = CursorUtil.getColumnIndexOrThrow(_cursor, "a1");
          final int _cursorIndexOfA2 = CursorUtil.getColumnIndexOrThrow(_cursor, "a2");
          final int _cursorIndexOfA3 = CursorUtil.getColumnIndexOrThrow(_cursor, "a3");
          final int _cursorIndexOfA4 = CursorUtil.getColumnIndexOrThrow(_cursor, "a4");
          final int _cursorIndexOfA5 = CursorUtil.getColumnIndexOrThrow(_cursor, "a5");
          final int _cursorIndexOfDesc = CursorUtil.getColumnIndexOrThrow(_cursor, "desc");
          final int _cursorIndexOfFpChain = CursorUtil.getColumnIndexOrThrow(_cursor, "fpChain");
          final int _cursorIndexOfCreatedAtNs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtNs");
          final List<SvcEventEntity> _result = new ArrayList<SvcEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SvcEventEntity _item;
            final long _tmpSeq;
            _tmpSeq = _cursor.getLong(_cursorIndexOfSeq);
            final int _tmpNr;
            _tmpNr = _cursor.getInt(_cursorIndexOfNr);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final int _tmpTgid;
            _tmpTgid = _cursor.getInt(_cursorIndexOfTgid);
            final int _tmpPid;
            _tmpPid = _cursor.getInt(_cursorIndexOfPid);
            final int _tmpUid;
            _tmpUid = _cursor.getInt(_cursorIndexOfUid);
            final String _tmpComm;
            if (_cursor.isNull(_cursorIndexOfComm)) {
              _tmpComm = null;
            } else {
              _tmpComm = _cursor.getString(_cursorIndexOfComm);
            }
            final long _tmpPc;
            _tmpPc = _cursor.getLong(_cursorIndexOfPc);
            final long _tmpCaller;
            _tmpCaller = _cursor.getLong(_cursorIndexOfCaller);
            final long _tmpFp;
            _tmpFp = _cursor.getLong(_cursorIndexOfFp);
            final long _tmpSp;
            _tmpSp = _cursor.getLong(_cursorIndexOfSp);
            final String _tmpBt;
            if (_cursor.isNull(_cursorIndexOfBt)) {
              _tmpBt = null;
            } else {
              _tmpBt = _cursor.getString(_cursorIndexOfBt);
            }
            final long _tmpCloneFn;
            _tmpCloneFn = _cursor.getLong(_cursorIndexOfCloneFn);
            final long _tmpRet;
            _tmpRet = _cursor.getLong(_cursorIndexOfRet);
            final long _tmpA0;
            _tmpA0 = _cursor.getLong(_cursorIndexOfA0);
            final long _tmpA1;
            _tmpA1 = _cursor.getLong(_cursorIndexOfA1);
            final long _tmpA2;
            _tmpA2 = _cursor.getLong(_cursorIndexOfA2);
            final long _tmpA3;
            _tmpA3 = _cursor.getLong(_cursorIndexOfA3);
            final long _tmpA4;
            _tmpA4 = _cursor.getLong(_cursorIndexOfA4);
            final long _tmpA5;
            _tmpA5 = _cursor.getLong(_cursorIndexOfA5);
            final String _tmpDesc;
            if (_cursor.isNull(_cursorIndexOfDesc)) {
              _tmpDesc = null;
            } else {
              _tmpDesc = _cursor.getString(_cursorIndexOfDesc);
            }
            final String _tmpFpChain;
            if (_cursor.isNull(_cursorIndexOfFpChain)) {
              _tmpFpChain = null;
            } else {
              _tmpFpChain = _cursor.getString(_cursorIndexOfFpChain);
            }
            final long _tmpCreatedAtNs;
            _tmpCreatedAtNs = _cursor.getLong(_cursorIndexOfCreatedAtNs);
            _item = new SvcEventEntity(_tmpSeq,_tmpNr,_tmpName,_tmpTgid,_tmpPid,_tmpUid,_tmpComm,_tmpPc,_tmpCaller,_tmpFp,_tmpSp,_tmpBt,_tmpCloneFn,_tmpRet,_tmpA0,_tmpA1,_tmpA2,_tmpA3,_tmpA4,_tmpA5,_tmpDesc,_tmpFpChain,_tmpCreatedAtNs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object countAll(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM events";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object threadStats(final int tgid, final int limit,
      final Continuation<? super List<ThreadStat>> $completion) {
    final String _sql = "\n"
            + "        SELECT pid AS pid, COUNT(*) AS count\n"
            + "        FROM events\n"
            + "        WHERE tgid = ?\n"
            + "        GROUP BY pid\n"
            + "        ORDER BY count DESC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, tgid);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ThreadStat>>() {
      @Override
      @NonNull
      public List<ThreadStat> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPid = 0;
          final int _cursorIndexOfCount = 1;
          final List<ThreadStat> _result = new ArrayList<ThreadStat>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ThreadStat _item;
            final int _tmpPid;
            _tmpPid = _cursor.getInt(_cursorIndexOfPid);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new ThreadStat(_tmpPid,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object threadEdges(final int tgid, final int limit,
      final Continuation<? super List<ThreadEdge>> $completion) {
    final String _sql = "\n"
            + "        SELECT seq AS seq, pid AS parentPid, ret AS childPid\n"
            + "        FROM events\n"
            + "        WHERE tgid = ? AND nr IN (220, 435) AND ret > 0\n"
            + "        ORDER BY seq ASC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, tgid);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ThreadEdge>>() {
      @Override
      @NonNull
      public List<ThreadEdge> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSeq = 0;
          final int _cursorIndexOfParentPid = 1;
          final int _cursorIndexOfChildPid = 2;
          final List<ThreadEdge> _result = new ArrayList<ThreadEdge>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ThreadEdge _item;
            final long _tmpSeq;
            _tmpSeq = _cursor.getLong(_cursorIndexOfSeq);
            final int _tmpParentPid;
            _tmpParentPid = _cursor.getInt(_cursorIndexOfParentPid);
            final long _tmpChildPid;
            _tmpChildPid = _cursor.getLong(_cursorIndexOfChildPid);
            _item = new ThreadEdge(_tmpSeq,_tmpParentPid,_tmpChildPid);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
