package com.almworks.sqlite4java;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.almworks.sqlite4java.SQLiteConstants.*;

public class SQLiteConnectionTests extends SQLiteConnectionFixture {
  public void testOpenFile() throws SQLiteException {
    SQLiteConnection connection = fileDb();
    assertFalse(connection.isOpen());
    try {
      connection.openReadonly();
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());

    connection = fileDb();
    boolean allowCreate = false;
    try {
      connection.open(allowCreate);
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }

    connection.open(true);
    assertTrue(connection.isOpen());
    assertEquals(dbFile(), connection.getDatabaseFile());

    connection.dispose();
    assertFalse(connection.isOpen());
  }

  public void testOpenMemory() throws SQLiteException {
    SQLiteConnection connection = memDb();
    assertFalse(connection.isOpen());
    try {
      connection.openReadonly();
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());

    connection = memDb();

    try {
      connection.open(false);
      fail("successfully opened");
    } catch (SQLiteException e) {
      // norm
    }

    connection.open();
    assertTrue(connection.isOpen());
    assertNull(connection.getDatabaseFile());
    assertTrue(connection.isMemoryDatabase());

    connection.dispose();
    assertFalse(connection.isOpen());
  }

  public void testExec() throws SQLiteException {
    SQLiteConnection db = fileDb();
    try {
      db.exec("create table xxx (x)");
      fail("exec unopened");
    } catch (SQLiteException e) {
      // ok
    }

    db.open();
    db.exec("pragma encoding=\"UTF-8\";");
    db.exec("create table x (x)");
    db.exec("insert into x values (1)");
    try {
      db.exec("blablabla");
      fail("execed bad sql");
    } catch (SQLiteException e) {
      // ok
    }
  }

  public void testGetTableColumnMetadata() throws SQLiteException {
    SQLiteConnection db = fileDb();
    db.open();
    db.exec("create table xxx (x INTEGER PRIMARY KEY)");

    try {
      String dbName = null;
      String tableName = "xxx";
      String columnName = "x";
      SQLiteColumnMetadata metadata = db.getTableColumnMetadata(dbName, tableName, columnName);
      assertEquals("INTEGER", metadata.getDataType());
      assertEquals("BINARY", metadata.getCollSeq());
      assertEquals(false, metadata.isNotNull());
      assertEquals(true, metadata.isPrimaryKey());
      assertEquals(false, metadata.isAutoinc());
    } catch (SQLiteException e) {
      fail("failed to get table column metadata");
    }
  }

  public void testSetAndGetLimit() throws SQLiteException {
    SQLiteConnection db = fileDb();
    db.open();
    int currentLimit = db.getLimit(SQLiteConstants.SQLITE_LIMIT_COLUMN);
    assertEquals(currentLimit, db.setLimit(SQLiteConstants.SQLITE_LIMIT_COLUMN, 5));
    assertEquals(5, db.getLimit(SQLiteConstants.SQLITE_LIMIT_COLUMN));
    db.exec("create table yyy (a integer, b integer, c integer, d integer, e integer);");
    try {
      db.exec("create table y (a integer, b integer, c integer, d integer, e integer, excessiveColumnName integer);");
      fail("exec should fail due to column count limitation");
    } catch (SQLiteException e) {
      // ok
    }
  }

  public void testCannotReopen() throws SQLiteException {
    SQLiteConnection connection = fileDb();
    connection.open();
    assertTrue(connection.isOpen());
    try {
      connection.open();
    } catch (AssertionError e) {
      // ok
    }
    assertTrue(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());
    connection.dispose();
    assertFalse(connection.isOpen());
    try {
      connection.open();
      fail("reopened connection");
    } catch (SQLiteException e) {
      // ok
    }
    assertFalse(connection.isOpen());
  }

  public void testOpenV2() throws SQLiteException {
    SQLiteConnection db = fileDb();
    db.openV2(SQLiteConstants.SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLiteConstants.SQLITE_OPEN_NOMUTEX);
    db.exec("create table x(x)");
    db.dispose();
  }

  public void testPrepareV3() throws SQLiteException {
    SQLiteConnection con = fileDb().open();
    con.exec("create table x(x)");
    SQLiteStatement stmt = con.prepare("insert into x values(?)", SQLiteConstants.SQLITE_PREPARE_PERSISTENT);
    stmt.bind(1, "42");
    stmt.step();
    con.dispose();
  }

  public void testIsReadOnly() throws Exception {
    for (int i = 0; i < 4; i++) {
      boolean readonlyOpen = (i & 1) != 0;
      boolean readonlyFile = (i & 2) != 0;

      // to recreate File
      setUp();
      SQLiteConnection con = fileDb().open();
      con.exec("create table x (x)");
      long expected = 42;
      con.exec(String.format("insert into x values (%d)", expected));
      con.dispose();

      File dataBaseFile = dbFile();

      if (readonlyFile) {
        assertTrue("can't make file readonly", dataBaseFile.setReadOnly());
      }

      con = new SQLiteConnection(dataBaseFile).openV2(
        readonlyOpen ?
        SQLiteConstants.SQLITE_OPEN_READONLY : SQLITE_OPEN_READWRITE);

      boolean isReadonly = readonlyFile || readonlyOpen;
      assertEquals(isReadonly, con.isReadOnly(null));
      assertEquals(isReadonly, con.isReadOnly("main"));
      try {
        // writable query
        con.exec("update x set x=x+1");
        expected++;
        if (isReadonly) {
          throw new Exception("should throw SQLiteException");
        }
      } catch (SQLiteException ex) {
        if (!isReadonly) {
          throw ex;
        }
      }

      SQLiteStatement st = con.prepare("select x from x");
      st.step();
      assertEquals(expected, st.columnLong(0));
    }
  }

  /**
   * Test sqlite3_db_cacheflush(). This is an attempt to produce a state where the flush occurs
   * during a write-transaction with dirty pages and an exclusive lock. Producing this state
   * predictably does not seem possible since it relies predicting the pager's exact caching behavior.
   * Therefore on both calls, SQLITE_OK and SQLITE_BUSY are assumed to be valid results.
   *
   * @throws Exception
   */
  public void testFlush() throws Exception {
    setUp();
    SQLiteConnection con = fileDb().open();

    con.exec("create table x (x integer)");
    con.exec("begin exclusive transaction");

    try {
      con.flush();
      con.exec("insert into x values(42)");
      con.exec("update x set x=x-1 where x=42");
      con.flush();
    } catch (SQLiteBusyException e) {
      Internal.logInfo(this,"flush on datatase returned SQLITE_BUSY");
    } finally {
      con.exec("commit");
      con.dispose();
    }
  }

  public void testBlocking() throws Exception {
    final File file = new File(tempName("db"));
    final SQLiteConnection con1 = new SQLiteConnection(file);
    final SQLiteConnection con2 = new SQLiteConnection(file);
    final int[] result = new int[] { 1 };
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          con1.openV2(SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_SHAREDCACHE);
          con1.setBlocking(true);
          SQLiteStatement stmt = con1.prepare("select * from x");
          stmt.step();
          stmt.dispose();
          result[0] = 0;
        } catch (Exception e) {
          result[0] = -1;
        } finally {
          con1.dispose();
        }
      }
    };

    try {
      con2.openV2(SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_SHAREDCACHE);
      con2.setBlocking(true);
      con2.exec("create table x (x integer)");
      con2.exec("begin exclusive transaction");
      t.start();
      Thread.sleep(100);
      con2.exec("insert into x values(42)");
      con2.exec("commit");
      t.join();
      assertEquals("testBlocking() failed to reach final state", 0, result[0]);
    } finally {
      con2.dispose();
   }
  }

}
