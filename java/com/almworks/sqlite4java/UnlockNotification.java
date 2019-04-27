/*
 * Copyright 2011 ALM Works Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.almworks.sqlite4java;

import static com.almworks.sqlite4java.SQLiteConstants.*;

/**
 * This class is used as the context object for sqlite3_unlock_notify
 * callbacks and is used as the mutex that is waited on when there is a lock
 * and notified when it is released.
 *
 * @see <a href="http://www.sqlite.org/unlock_notify.html">SQLite Unlock Notification</a>
 */
abstract class UnlockNotification {
  /**
   * Indicates whether an unlock notification has occured and the callback
   * has accepted the notification.
   */
  volatile private boolean hasFired;

  /**
   * This callback is called by SQLite if this {@link
   * UnlockNotification} object is the object that was registered
   * with the sqlite3_unlock_notify() call.
   *
   * It gets called when SQLite is ready to indicate when a lock is
   * released.
   */
  public void callback() {
    synchronized (this) {
      hasFired = true;
      this.notifyAll();
    }
  }
  
  /**
   * @see _SQLiteDatabaseUnlockNotification#manualUnlockNotify()
   * @see _SQLiteStatementUnlockNotification#manualUnlockNotify()
   */
  protected abstract int manualUnlockNotify();
  
  /**
   * The sqlite <a href="http://www.sqlite.org/unlock_notify.html">Unlock
   * Notify</a> documentation describes well what this does, but in short it
   * will wait for the callback registered to send a notification that the
   * lock has been released.
   */
  protected int wait_for_unlock_notify() throws SQLiteException {
    // this makes a call to sqlite3_unlock_notify() via JNI that registers
    // the callback
    int rc = manualUnlockNotify();
    assert(rc == SQLiteConstants.SQLITE_LOCKED || rc == SQLiteConstants.SQLITE_LOCKED_SHAREDCACHE || rc == SQLiteConstants.SQLITE_OK);
    if (rc == SQLITE_OK) {
      synchronized(this) {
        while (!hasFired) {
          try {
            // wait for notification from the callback
            if (Internal.isFineLogging())
                Internal.logFine(this, "blocking in wait_for_unlock_notify()");
            wait();
          } catch(InterruptedException e) {
            throw new SQLiteException(SQLiteConstants.WRAPPER_WEIRD, "wait() interrupted", e);
          }
        }
      }
    }
    
    return rc;
  }
  
  /**
   * Unlock notify implementation for {@link SQLiteConnection}.
   */
  static class _SQLiteDatabaseUnlockNotification extends UnlockNotification {
    SWIGTYPE_p_sqlite3 handle;
    
    protected _SQLiteDatabaseUnlockNotification(SWIGTYPE_p_sqlite3 handle) {
      this.handle = handle;
    }
    
    /**
     * Call sqlite3_unlock_notify for a database handle.
     */
    protected int manualUnlockNotify() {
      return _SQLiteManual.sqlite3_db_unlock_notify(handle, this);
    }
  }

  /**
   * Unlock notify implementation for {@link SQLiteStatement}.
   */
  static class _SQLiteStatementUnlockNotification extends UnlockNotification {
    SWIGTYPE_p_sqlite3_stmt handle;
    
    protected _SQLiteStatementUnlockNotification(SWIGTYPE_p_sqlite3_stmt handle) {
      this.handle = handle;
    }
    
    /**
     * Call sqlite3_unlock_notify for a statement handle.
     */
    protected int manualUnlockNotify() {
      return _SQLiteManual.sqlite3_statement_unlock_notify(handle, this);
    }
  }
}
