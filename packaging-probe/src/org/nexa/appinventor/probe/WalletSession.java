package org.nexa.appinventor.probe;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** App-owned lifecycle rules; no Nexa API is invented by this abstraction. */
final class WalletSession {
  interface Handle {
    String recoveryPhrase();
    void encrypt(String passphrase);
    void save();
    Boolean unlock(String passphrase);
    void lock();
    Boolean lockedState();
    void connect();
    String currentTestnetAddress();
    WalletBalance balance();
    Runnable startFastSync(Consumer<String> progress);
  }

  interface FastSyncListener {
    void progress(String message);
    void finished(FastSyncResult result);
    void failed(String code, String message);
  }

  interface Watchdog {
    Runnable schedule(Runnable timeout);
  }

  static final class FastSyncResult {
    final boolean advanced, synced, cancelled;
    final String beforeHeight, afterHeight, chainHeight;
    FastSyncResult(long before, WalletBalance after, boolean cancelled) {
      this.advanced = Long.parseLong(after.syncedHeight) > before;
      this.synced = after.synced;
      this.cancelled = cancelled;
      this.beforeHeight = Long.toString(before);
      this.afterHeight = after.syncedHeight;
      this.chainHeight = after.chainHeight;
    }
  }

  private static final class FastAttempt {
    final long before;
    final FastSyncListener listener;
    Runnable abort = () -> { };
    Runnable cancelWatchdog = () -> { };
    boolean cancelled, failed, handedOff;
    FastAttempt(long before, FastSyncListener listener) {
      this.before = before;
      this.listener = listener;
    }
  }

  interface Storage {
    boolean exists();
    Handle create();
    Handle open();
    void resetBackup();
    boolean backupConfirmed();
    void confirmBackup();
  }

  static final class Failure extends RuntimeException {
    final String code;
    Failure(String code, String message) {
      super(message);
      this.code = code;
    }
  }

  private final Storage storage;
  private Handle handle;
  private volatile String address;
  private volatile FastAttempt fastAttempt;
  private final AtomicLong epoch = new AtomicLong();
  private volatile long handledEpoch;
  private volatile boolean lockBlocked;

  long invalidateAccess() {
    long ticket = epoch.incrementAndGet();
    address = null;
    return ticket;
  }

  synchronized void completeLock(long ticket) {
    if (ticket != epoch.get() || ticket == handledEpoch) return;
    address = null;
    FastAttempt attempt = fastAttempt;
    if (attempt != null) {
      attempt.failed = true;
      attempt.cancelled = true;
      attempt.cancelWatchdog.run();
      try { attempt.abort.run(); } catch (Exception | LinkageError ignored) { }
    }
    if (handle != null) lockKeys();
    handledEpoch = ticket;
  }

  private void prepare() {
    if (lockBlocked) throw new Failure("LOCK_NOT_CONFIRMED", "Key locking was not confirmed. Stop the app process.");
    completeLock(epoch.get());
  }

  private void lockKeys() {
    try {
      handle.lock();
      // nexa-wallet-lifecycle-and-chain/SKILL.md, pattern 6.
      if (!Boolean.TRUE.equals(handle.lockedState())) throw new IllegalStateException();
    } catch (Exception | LinkageError failure) {
      lockBlocked = true;
      address = null;
      throw new Failure("LOCK_NOT_CONFIRMED", "Key locking was not confirmed. Stop the app process.");
    }
  }

  private void checkpoint(long ticket, BooleanSupplier permitted) {
    if (epoch.get() != ticket || !permitted.getAsBoolean()) {
      address = null;
      throw new Failure("SESSION_LOCKED", "Session access was revoked. Return to the app and reopen the wallet.");
    }
  }

  WalletSession(Storage storage) {
    this.storage = storage;
  }

  private static void requirePassphrase(String passphrase) {
    if (passphrase == null || passphrase.length() < 12) {
      throw new Failure("PASSPHRASE_REQUIRED", "Use a passphrase of at least 12 characters.");
    }
  }

  synchronized String create(String passphrase) { return create(passphrase, () -> true); }

  synchronized String create(String passphrase, BooleanSupplier permitted) {
    prepare();
    long ticket = epoch.get();
    checkpoint(ticket, permitted);
    requireNoFastSync();
    requirePassphrase(passphrase);
    if (handle != null || storage.exists()) {
      throw new Failure("WALLET_EXISTS", "A testnet wallet exists. Use OpenWallet.");
    }
    // Wallet lifecycle skill, patterns 1/3: never replace a wallet after an open error.
    address = null;
    storage.resetBackup();
    handle = storage.create();
    try {
      // recoveryFlow.md: show once, encrypt, persist, then permit receiving.
      String phrase = handle.recoveryPhrase();
      handle.encrypt(passphrase);
      handle.save();
      checkpoint(ticket, permitted);
      String candidate = handle.currentTestnetAddress();
      lockKeys();
      checkpoint(ticket, permitted);
      address = candidate;
      return phrase;
    } catch (Exception | LinkageError failure) {
      address = null;
      try { lockKeys(); } catch (Exception | LinkageError ignored) { }
      throw failure;
    }
  }

  synchronized void open(String passphrase) { open(passphrase, () -> true); }

  synchronized void open(String passphrase, BooleanSupplier permitted) {
    prepare();
    long ticket = epoch.get();
    checkpoint(ticket, permitted);
    requireNoFastSync();
    requirePassphrase(passphrase);
    address = null;
    String phase = "READ_DATABASE";
    try {
      if (handle == null) {
        if (!storage.exists()) {
          throw new Failure("WALLET_NOT_FOUND", "No testnet wallet exists. Use CreateWallet.");
        }
        // Open disconnected so attachment cannot derive keys before unlock.
        // Keep the handle for password retries without reopening the same DB.
        handle = storage.open();
      }
      checkpoint(ticket, permitted);
      phase = "UNLOCK";
      Boolean unlocked = handle.unlock(passphrase);
      if (unlocked == null) {
        throw new Failure("WALLET_NOT_ENCRYPTED", "The existing wallet is not encrypted. No changes made.");
      }
      if (!unlocked) {
        throw new Failure("INVALID_PASSPHRASE", "The passphrase is incorrect.");
      }
      checkpoint(ticket, permitted);
      phase = "CONNECT_CHAIN";
      handle.connect();
      checkpoint(ticket, permitted);
      phase = "READ_ADDRESS";
      String candidate = handle.currentTestnetAddress();
      phase = "RELOCK";
      lockKeys();
      checkpoint(ticket, permitted);
      address = candidate;
    } catch (Exception | LinkageError failure) {
      address = null;
      if (handle != null) {
        try { lockKeys(); } catch (Exception | LinkageError ignored) { }
      }
      if (failure instanceof Failure) throw (Failure) failure;
      // Expose only the operation stage and exception type, not SDK text/secrets.
      throw new Failure(failure.getClass().getSimpleName(),
          "OpenWallet failed during " + phase + ". Existing wallet data was not replaced.");
    }
  }

  boolean isOpen() {
    return address != null && handledEpoch == epoch.get() && !lockBlocked;
  }

  synchronized String showRecoveryPhrase(String passphrase, BooleanSupplier permitted) {
    prepare();
    requireNoFastSync();
    requireOpen();
    if (storage.backupConfirmed()) throw new Failure("BACKUP_ALREADY_CONFIRMED", "Recovery display is available only until initial backup confirmation.");
    requirePassphrase(passphrase);
    long ticket = epoch.get();
    checkpoint(ticket, permitted);
    try {
      if (!Boolean.TRUE.equals(handle.unlock(passphrase))) throw new Failure("INVALID_PASSPHRASE", "The passphrase is incorrect.");
      checkpoint(ticket, permitted);
      String phrase = handle.recoveryPhrase();
      lockKeys();
      checkpoint(ticket, permitted);
      return phrase;
    } finally {
      lockKeys();
    }
  }

  synchronized void confirmBackup() {
    requireOpen();
    storage.confirmBackup();
  }

  synchronized String address() {
    requireOpen();
    if (!storage.backupConfirmed()) {
      throw new Failure("BACKUP_REQUIRED", "Record your recovery phrase, then call ConfirmBackup.");
    }
    // Cached public destination from the last successful create/open; no key unlock.
    return address;
  }

  synchronized WalletBalance balance() {
    requireOpen();
    // Read-only: does not unlock, save, derive a new address or broadcast.
    return handle.balance();
  }

  boolean fastSyncRunning() { return fastAttempt != null; }

  synchronized void fastSync(String passphrase, Executor callbacks, Watchdog watchdog,
      FastSyncListener listener) {
    fastSync(passphrase, callbacks, watchdog, listener, () -> true);
  }

  synchronized void fastSync(String passphrase, Executor callbacks, Watchdog watchdog,
      FastSyncListener listener, BooleanSupplier permitted) {
    prepare();
    long ticket = epoch.get();
    checkpoint(ticket, permitted);
    requireNoFastSync();
    requireOpen();
    requirePassphrase(passphrase);
    WalletBalance before = handle.balance();
    long height = Long.parseLong(before.syncedHeight);
    if (before.synced) {
      listener.finished(new FastSyncResult(height, before, false));
      return;
    }
    if (Long.parseLong(before.chainHeight) <= 0) {
      throw new Failure("CHAIN_TIP_UNAVAILABLE", "Wait until GetBalance reports a known chain height.");
    }
    FastAttempt attempt = new FastAttempt(height, listener);
    fastAttempt = attempt;
    try {
      checkpoint(ticket, permitted);
      Boolean unlocked = handle.unlock(passphrase);
      checkpoint(ticket, permitted);
      if (unlocked == null) throw new Failure("WALLET_NOT_ENCRYPTED", "The wallet must be encrypted.");
      if (!unlocked) throw new Failure("INVALID_PASSPHRASE", "The passphrase is incorrect.");
      // SDK fastForward is asynchronous. Keep keys available until its terminal callback.
      attempt.handedOff = true;
      attempt.abort = handle.startFastSync(message -> callbacks.execute(
          () -> fastSyncProgress(attempt, message)));
      checkpoint(ticket, permitted);
      // Also safe when a test/backend delivers a terminal callback synchronously.
      if (fastAttempt == attempt) {
        attempt.cancelWatchdog = watchdog.schedule(() -> fastSyncTimeout(attempt));
      }
    } catch (Exception | LinkageError failure) {
      attempt.cancelWatchdog.run();
      if (attempt.handedOff) {
        // A thrown async-start call is not proof that no SDK thread was launched.
        attempt.cancelled = true;
        attempt.failed = true;
        try { attempt.abort.run(); } catch (Exception | LinkageError ignored) { }
      }
      try { lockKeys(); }
      catch (Exception | LinkageError lockFailure) {
        attempt.failed = true;
        throw new Failure("FAST_SYNC_LOCK_FAILED", "Fast sync could not relock keys. Stop the app process.");
      }
      if (!attempt.handedOff && fastAttempt == attempt) fastAttempt = null;
      if (attempt.handedOff) {
        throw new Failure("FAST_SYNC_START_FAILED", "Fast sync could not start reliably; keys relocked. Restart the app process if FastSyncRunning remains true.");
      }
      throw failure;
    }
  }

  private synchronized void fastSyncProgress(FastAttempt attempt, String message) {
    if (fastAttempt != attempt || message == null) return;
    if (!"finished".equals(message)) {
      if (!attempt.failed) attempt.listener.progress(message);
      return;
    }
    attempt.cancelWatchdog.run();
    try {
      // "finished" means the search ended, including on failure; never call it success.
      WalletBalance after = attempt.failed ? null : handle.balance();
      lockKeys();
      fastAttempt = null;
      if (!attempt.failed) {
        attempt.listener.finished(new FastSyncResult(attempt.before, after, attempt.cancelled));
      }
    } catch (Exception | LinkageError failure) {
      try { lockKeys(); } catch (Exception | LinkageError ignored) { }
      // Retain the guard if cleanup failed; do not start another SDK search on this handle.
      attempt.failed = true;
      attempt.listener.failed("FAST_SYNC_CLEANUP_FAILED", "Fast sync ended but cleanup failed. Restart the app process.");
    }
  }

  synchronized void cancelFastSync() {
    FastAttempt attempt = fastAttempt;
    if (attempt == null) throw new Failure("FAST_SYNC_NOT_RUNNING", "No fast sync is running.");
    if (!attempt.cancelled) {
      attempt.cancelled = true;
      // Wallet lifecycle skill: aborter.obj = true requests a cooperative stop.
      attempt.abort.run();
      if (!attempt.failed) attempt.listener.progress("Cancellation requested; waiting for the SDK.");
    }
  }

  private synchronized void fastSyncTimeout(FastAttempt attempt) {
    if (fastAttempt != attempt || attempt.failed) return;
    attempt.cancelled = true;
    attempt.failed = true;
    try { attempt.abort.run(); } catch (Exception | LinkageError ignored) { }
    String code = "FAST_SYNC_TIMEOUT";
    String message = "Fast sync exceeded five minutes. Stop requested and keys relocked. Restart the app process if it remains busy.";
    try { lockKeys(); }
    catch (Exception | LinkageError failure) {
      code = "FAST_SYNC_LOCK_FAILED";
      message = "Fast sync timed out and key relocking failed. Stop the app process.";
    }
    // Keep the guard until "finished": requesting abort does not prove the SDK stopped.
    attempt.listener.failed(code, message);
  }

  private void requireNoFastSync() {
    if (fastAttempt != null) {
      throw new Failure("FAST_SYNC_RUNNING", "Wait for fast sync to finish before opening or creating a wallet.");
    }
  }

  private void requireOpen() {
    if (!isOpen()) {
      throw new Failure("WALLET_NOT_OPEN", "Create or open the testnet wallet first.");
    }
  }
}
