package com.svcmonitor.app.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.FtsTableInfo;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unchecked", "deprecation"})
public final class SvcEventDb_Impl extends SvcEventDb {
  private volatile SvcEventDao _svcEventDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`seq` INTEGER NOT NULL, `nr` INTEGER NOT NULL, `name` TEXT NOT NULL, `tgid` INTEGER NOT NULL, `pid` INTEGER NOT NULL, `uid` INTEGER NOT NULL, `comm` TEXT NOT NULL, `pc` INTEGER NOT NULL, `caller` INTEGER NOT NULL, `fp` INTEGER NOT NULL, `sp` INTEGER NOT NULL, `bt` TEXT NOT NULL, `cloneFn` INTEGER NOT NULL, `ret` INTEGER NOT NULL, `a0` INTEGER NOT NULL, `a1` INTEGER NOT NULL, `a2` INTEGER NOT NULL, `a3` INTEGER NOT NULL, `a4` INTEGER NOT NULL, `a5` INTEGER NOT NULL, `desc` TEXT NOT NULL, `fpChain` TEXT NOT NULL, `createdAtNs` INTEGER NOT NULL, PRIMARY KEY(`seq`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_tgid` ON `events` (`tgid`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_pid` ON `events` (`pid`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_nr` ON `events` (`nr`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_comm` ON `events` (`comm`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_createdAtNs` ON `events` (`createdAtNs`)");
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `events_fts` USING FTS4(`desc` TEXT NOT NULL, `comm` TEXT NOT NULL, `name` TEXT NOT NULL, `fpChain` TEXT NOT NULL, `bt` TEXT NOT NULL, content=`events`)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_BEFORE_UPDATE BEFORE UPDATE ON `events` BEGIN DELETE FROM `events_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_BEFORE_DELETE BEFORE DELETE ON `events` BEGIN DELETE FROM `events_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_AFTER_UPDATE AFTER UPDATE ON `events` BEGIN INSERT INTO `events_fts`(`docid`, `desc`, `comm`, `name`, `fpChain`, `bt`) VALUES (NEW.`rowid`, NEW.`desc`, NEW.`comm`, NEW.`name`, NEW.`fpChain`, NEW.`bt`); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_AFTER_INSERT AFTER INSERT ON `events` BEGIN INSERT INTO `events_fts`(`docid`, `desc`, `comm`, `name`, `fpChain`, `bt`) VALUES (NEW.`rowid`, NEW.`desc`, NEW.`comm`, NEW.`name`, NEW.`fpChain`, NEW.`bt`); END");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4069996ce658be9fc456d9bc5af9e54d')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `events`");
        db.execSQL("DROP TABLE IF EXISTS `events_fts`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_BEFORE_UPDATE BEFORE UPDATE ON `events` BEGIN DELETE FROM `events_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_BEFORE_DELETE BEFORE DELETE ON `events` BEGIN DELETE FROM `events_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_AFTER_UPDATE AFTER UPDATE ON `events` BEGIN INSERT INTO `events_fts`(`docid`, `desc`, `comm`, `name`, `fpChain`, `bt`) VALUES (NEW.`rowid`, NEW.`desc`, NEW.`comm`, NEW.`name`, NEW.`fpChain`, NEW.`bt`); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_events_fts_AFTER_INSERT AFTER INSERT ON `events` BEGIN INSERT INTO `events_fts`(`docid`, `desc`, `comm`, `name`, `fpChain`, `bt`) VALUES (NEW.`rowid`, NEW.`desc`, NEW.`comm`, NEW.`name`, NEW.`fpChain`, NEW.`bt`); END");
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsEvents = new HashMap<String, TableInfo.Column>(23);
        _columnsEvents.put("seq", new TableInfo.Column("seq", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("nr", new TableInfo.Column("nr", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("tgid", new TableInfo.Column("tgid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("pid", new TableInfo.Column("pid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("uid", new TableInfo.Column("uid", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("comm", new TableInfo.Column("comm", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("pc", new TableInfo.Column("pc", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("caller", new TableInfo.Column("caller", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("fp", new TableInfo.Column("fp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("sp", new TableInfo.Column("sp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("bt", new TableInfo.Column("bt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("cloneFn", new TableInfo.Column("cloneFn", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("ret", new TableInfo.Column("ret", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("a0", new TableInfo.Column("a0", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("a1", new TableInfo.Column("a1", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("a2", new TableInfo.Column("a2", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("a3", new TableInfo.Column("a3", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("a4", new TableInfo.Column("a4", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("a5", new TableInfo.Column("a5", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("desc", new TableInfo.Column("desc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("fpChain", new TableInfo.Column("fpChain", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("createdAtNs", new TableInfo.Column("createdAtNs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEvents = new HashSet<TableInfo.Index>(5);
        _indicesEvents.add(new TableInfo.Index("index_events_tgid", false, Arrays.asList("tgid"), Arrays.asList("ASC")));
        _indicesEvents.add(new TableInfo.Index("index_events_pid", false, Arrays.asList("pid"), Arrays.asList("ASC")));
        _indicesEvents.add(new TableInfo.Index("index_events_nr", false, Arrays.asList("nr"), Arrays.asList("ASC")));
        _indicesEvents.add(new TableInfo.Index("index_events_comm", false, Arrays.asList("comm"), Arrays.asList("ASC")));
        _indicesEvents.add(new TableInfo.Index("index_events_createdAtNs", false, Arrays.asList("createdAtNs"), Arrays.asList("ASC")));
        final TableInfo _infoEvents = new TableInfo("events", _columnsEvents, _foreignKeysEvents, _indicesEvents);
        final TableInfo _existingEvents = TableInfo.read(db, "events");
        if (!_infoEvents.equals(_existingEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "events(com.svcmonitor.app.db.SvcEventEntity).\n"
                  + " Expected:\n" + _infoEvents + "\n"
                  + " Found:\n" + _existingEvents);
        }
        final HashSet<String> _columnsEventsFts = new HashSet<String>(5);
        _columnsEventsFts.add("desc");
        _columnsEventsFts.add("comm");
        _columnsEventsFts.add("name");
        _columnsEventsFts.add("fpChain");
        _columnsEventsFts.add("bt");
        final FtsTableInfo _infoEventsFts = new FtsTableInfo("events_fts", _columnsEventsFts, "CREATE VIRTUAL TABLE IF NOT EXISTS `events_fts` USING FTS4(`desc` TEXT NOT NULL, `comm` TEXT NOT NULL, `name` TEXT NOT NULL, `fpChain` TEXT NOT NULL, `bt` TEXT NOT NULL, content=`events`)");
        final FtsTableInfo _existingEventsFts = FtsTableInfo.read(db, "events_fts");
        if (!_infoEventsFts.equals(_existingEventsFts)) {
          return new RoomOpenHelper.ValidationResult(false, "events_fts(com.svcmonitor.app.db.SvcEventFtsEntity).\n"
                  + " Expected:\n" + _infoEventsFts + "\n"
                  + " Found:\n" + _existingEventsFts);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "4069996ce658be9fc456d9bc5af9e54d", "47aeeaf50908ed6dd832d8108a337d7d");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(1);
    _shadowTablesMap.put("events_fts", "events");
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "events","events_fts");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `events`");
      _db.execSQL("DELETE FROM `events_fts`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SvcEventDao.class, SvcEventDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SvcEventDao dao() {
    if (_svcEventDao != null) {
      return _svcEventDao;
    } else {
      synchronized(this) {
        if(_svcEventDao == null) {
          _svcEventDao = new SvcEventDao_Impl(this);
        }
        return _svcEventDao;
      }
    }
  }
}
