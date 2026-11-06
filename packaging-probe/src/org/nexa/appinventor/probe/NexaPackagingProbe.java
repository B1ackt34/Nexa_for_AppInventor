package org.nexa.appinventor.probe;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.Extension;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import org.nexa.libnexakotlin.LibnexaandroidKt;
import com.google.appinventor.components.runtime.OnPauseListener;
import com.google.appinventor.components.runtime.OnStopListener;
import com.google.appinventor.components.runtime.OnResumeListener;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Experimental testnet wallet. No mainnet, private-key import or sending operations. */
@Extension(description = "Experimental Nexa testnet wallet with manual Electrum fast sync.")
@DesignerComponent(version = 7, description = "Experimental Nexa testnet wallet",
    category = ComponentCategory.EXTENSION, nonVisible = true, androidMinSdk = 29)
@SimpleObject(external = true)
public final class NexaPackagingProbe extends AndroidNonvisibleComponent implements OnPauseListener, OnStopListener, OnResumeListener {
  private static final Object INITIALIZATION_LOCK = new Object();
  private static volatile boolean initialized;
  private static final ThreadPoolExecutor WALLET_WORKER = new ThreadPoolExecutor(1, 1, 0,
      TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "NexaWallet"));
  private static final java.util.concurrent.ExecutorService SECURITY_WORKER = Executors.newSingleThreadExecutor(
      runnable -> new Thread(runnable, "NexaLock"));
  private static volatile WalletSession session;
  private static final ScheduledThreadPoolExecutor FAST_SYNC_WATCHDOG = createWatchdog();
  private final android.content.Context applicationContext;
  private boolean initializing;
  private final ForegroundGate gate = new ForegroundGate();
  private final Set<GuardedRequest> pending = ConcurrentHashMap.newKeySet();
  private final SensitiveScreen screen;

  public NexaPackagingProbe(ComponentContainer container) {
    super(container.$form());
    applicationContext = container.$context().getApplicationContext();
    screen = new SensitiveScreen(form);
    form.registerForOnPause(this);
    form.registerForOnStop(this);
    form.registerForOnResume(this);
  }

  @SimpleProperty(description = "True after native initialization succeeds in this process.")
  public boolean Initialized() {
    return initialized;
  }

  @SimpleProperty(description = "The only supported network: testnet.")
  public String Network() { return "testnet"; }

  @SimpleProperty(description = "True after this process successfully creates or opens the wallet.")
  public boolean WalletOpen() {
    WalletSession active = session;
    return active != null && active.isOpen();
  }

  private static ScheduledThreadPoolExecutor createWatchdog() {
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1,
        runnable -> new Thread(runnable, "NexaFastSyncWatchdog"));
    timer.setRemoveOnCancelPolicy(true);
    return timer;
  }

  @SimpleProperty(description = "True while an SDK fast sync remains in flight, including a stop awaiting confirmation.")
  public boolean FastSyncRunning() {
    WalletSession active = session;
    return active != null && active.fastSyncRunning();
  }

  private static void initializeNative() {
    synchronized (INITIALIZATION_LOCK) {
      if (!initialized) {
        // nexa-wallet-lifecycle-and-chain/SKILL.md, Setup and versions.
        if (LibnexaandroidKt.initializeLibNexa("") == null) {
          throw new IllegalStateException("Nexa initialization returned no native library.");
        }
        initialized = true;
      }
    }
  }

  @SimpleFunction(description = "Initialize Nexa off the UI thread. Reports completion by event.")
  public void Initialize() {
    if (initializing) {
      InitializationFailed("BUSY", "Initialization is already running.");
      return;
    }
    initializing = true;
    new Thread(() -> {
      try {
        initializeNative();
        form.runOnUiThread(() -> {
          initializing = false;
          InitializationSucceeded();
        });
      } catch (RuntimeException | LinkageError failure) {
        final String code = failure.getClass().getSimpleName();
        final String message = failure.getMessage() == null ? code : failure.getMessage();
        form.runOnUiThread(() -> {
          initializing = false;
          InitializationFailed(code, message);
        });
      }
    }, "NexaPackagingProbe").start();
  }

  @SimpleEvent(description = "Nexa native initialization completed; does not imply wallet readiness.")
  public void InitializationSucceeded() {
    EventDispatcher.dispatchEvent(this, "InitializationSucceeded");
  }

  @SimpleEvent(description = "Native initialization failed, or another call is in progress.")
  public void InitializationFailed(String code, String message) {
    EventDispatcher.dispatchEvent(this, "InitializationFailed", code, message);
  }

  private WalletSession walletSession() {
    initializeNative();
    synchronized (INITIALIZATION_LOCK) {
      if (session == null) session = new WalletSession(new NexaWalletStorage(applicationContext));
      return session;
    }
  }

  private void post(long token, Runnable event) {
    form.runOnUiThread(() -> { if (gate.current(token)) event.run(); });
  }

  private void walletOperation(String operation, Consumer<GuardedRequest> work) {
    walletOperation(operation, null, work);
  }

  private void walletOperation(String operation, String secret, Consumer<GuardedRequest> work) {
    if (!gate.active()) return;
    GuardedRequest request = new GuardedRequest(gate, secret, current -> {
      try {
        work.accept(current);
      } catch (WalletSession.Failure failure) {
        post(current.token, () -> WalletFailed(operation, failure.code, failure.getMessage()));
      } catch (Exception | LinkageError failure) {
        String code = failure.getClass().getSimpleName();
        post(current.token, () -> WalletFailed(operation, code,
            "Nexa could not complete " + operation + ". The wallet was not automatically replaced."));
      }
    }, pending::remove);
    pending.add(request);
    WALLET_WORKER.execute(request);
  }

  private void revoke(String reason) {
    for (GuardedRequest request : pending) {
      request.cancel();
      if (WALLET_WORKER.remove(request)) pending.remove(request);
    }
    screen.clear();
    WalletSession active = session;
    if (active != null) {
      long ticket = active.invalidateAccess();
      SECURITY_WORKER.execute(() -> {
        try { active.completeLock(ticket); }
        catch (WalletSession.Failure failure) {
          long token = gate.capture();
          post(token, () -> WalletFailed("LockWallet", failure.code, failure.getMessage()));
        }
      });
    }
    SessionLocked(reason);
  }

  public void onPause() { if (gate.pause()) revoke("BACKGROUND"); }
  public void onStop() { if (gate.pause()) revoke("BACKGROUND"); }
  public void onResume() {
    screen.protect();
    boolean returning = !gate.active();
    if (returning) gate.resume();
    if (returning && session != null) AuthenticationRequired();
  }

  @SimpleFunction(description = "Revoke session access and request verified key locking. Does not stop normal chain synchronization.")
  public void LockWallet() { gate.invalidate(); revoke("MANUAL"); }

  @SimpleProperty(description = "Whether Android FLAG_SECURE is set on this Form. Does not protect other Forms or rooted devices.")
  public boolean ScreenProtected() { return screen.protectedScreen(); }

  @SimpleEvent(description = "Session access revoked immediately; in-flight SDK work must return before key locking can complete.")
  public void SessionLocked(String reason) { EventDispatcher.dispatchEvent(this, "SessionLocked", reason); }

  @SimpleEvent(description = "App resumed after backgrounding. Call OpenWallet again with the passphrase.")
  public void AuthenticationRequired() { EventDispatcher.dispatchEvent(this, "AuthenticationRequired"); }

  @SimpleFunction(description = "Re-authenticate to display an unfinished initial backup. Disabled after ConfirmBackup. Requires an open wallet.")
  public void ShowRecoveryPhrase(String passphrase) {
    walletOperation("ShowRecoveryPhrase", passphrase, request -> {
      String phrase = walletSession().showRecoveryPhrase(request.secret(), request::valid);
      post(request.token, () -> RecoveryPhraseReady(phrase));
    });
  }

  @SimpleEvent(description = "Recovery phrase for unfinished initial backup only. Never persist, log or transmit it.")
  public void RecoveryPhraseReady(String recoveryPhrase) {
    long token = gate.capture();
    try { EventDispatcher.dispatchEvent(this, "RecoveryPhraseReady", recoveryPhrase); }
    finally { screen.trackPhrase(recoveryPhrase); if (!gate.current(token)) screen.clear(); }
  }

  @SimpleFunction(description = "Create a new encrypted testnet wallet. Passphrase: at least 12 characters. Fails if a wallet exists.")
  public void CreateWallet(String passphrase) {
    walletOperation("CreateWallet", passphrase, request -> {
      String phrase = walletSession().create(request.secret(), request::valid);
      post(request.token, () -> WalletCreated(phrase));
    });
  }

  @SimpleFunction(description = "Reopen the existing testnet wallet with its passphrase. Never creates or resets a wallet.")
  public void OpenWallet(String passphrase) {
    walletOperation("OpenWallet", passphrase, request -> {
      walletSession().open(request.secret(), request::valid);
      post(request.token, this::WalletOpened);
    });
  }

  @SimpleFunction(description = "Confirm you have recorded the recovery phrase. Enables GetAddress; this does not verify the backup contents.")
  public void ConfirmBackup() {
    walletOperation("ConfirmBackup", request -> {
      walletSession().confirmBackup();
      post(request.token, this::BackupConfirmed);
    });
  }

  @SimpleFunction(description = "Read the public receive address cached at create/open, without rotating it. Requires backup confirmation.")
  public void GetAddress() {
    walletOperation("GetAddress", request -> {
      String address = walletSession().address();
      post(request.token, () -> AddressReceived(address));
    });
  }

  @SimpleFunction(description = "Read balances and sync state asynchronously. Amounts are exact NEXA text with 2 decimal places. Requires an open wallet.")
  public void GetBalance() {
    walletOperation("GetBalance", request -> {
      WalletBalance balance = walletSession().balance();
      post(request.token, () -> BalanceReceived(balance.total, balance.confirmed,
          balance.unconfirmed, balance.synced, balance.connected,
          balance.syncedHeight, balance.chainHeight));
    });
  }

  @SimpleFunction(description = "Manually fast-sync via Electrum, trusting its history. Requires the open wallet passphrase; keys are temporarily unlocked. Five-minute watchdog.")
  public void FastSync(String passphrase) {
    walletOperation("FastSync", passphrase, request -> walletSession().fastSync(request.secret(), WALLET_WORKER,
        timeout -> {
          ScheduledFuture<?> scheduled = FAST_SYNC_WATCHDOG.schedule(timeout, 5, TimeUnit.MINUTES);
          return () -> scheduled.cancel(false);
        }, new WalletSession.FastSyncListener() {
          public void progress(String message) {
            post(request.token, () -> FastSyncProgress(message));
          }
          public void finished(WalletSession.FastSyncResult result) {
            post(request.token, () -> FastSyncFinished(result.advanced, result.synced,
                result.cancelled, result.beforeHeight, result.afterHeight, result.chainHeight));
          }
          public void failed(String code, String message) {
            post(request.token, () -> WalletFailed("FastSync", code, message));
          }
        }, request::valid));
  }

  @SimpleFunction(description = "Request cooperative cancellation. FastSyncRunning remains true until the SDK finishes; this is not immediate thread termination.")
  public void CancelFastSync() {
    walletOperation("CancelFastSync", request -> walletSession().cancelFastSync());
  }

  @SimpleEvent(description = "New encrypted testnet wallet created. Show recoveryPhrase privately once for backup; never log or transmit it.")
  public void WalletCreated(String recoveryPhrase) {
    long token = gate.capture();
    try { EventDispatcher.dispatchEvent(this, "WalletCreated", recoveryPhrase); }
    finally { screen.trackPhrase(recoveryPhrase); if (!gate.current(token)) screen.clear(); }
  }

  @SimpleEvent(description = "Existing testnet wallet opened. Keys are relocked; this does not imply blockchain sync.")
  public void WalletOpened() { EventDispatcher.dispatchEvent(this, "WalletOpened"); }

  @SimpleEvent(description = "Backup confirmation saved.")
  public void BackupConfirmed() { EventDispatcher.dispatchEvent(this, "BackupConfirmed"); }

  @SimpleEvent(description = "Public testnet receive address from the last create/open.")
  public void AddressReceived(String address) {
    EventDispatcher.dispatchEvent(this, "AddressReceived", address);
  }

  @SimpleEvent(description = "Balances in NEXA text (2 decimals), sync flag, P2P connection flag and block heights as text. Unsynced/offline values may be incomplete or stale; not a spendable balance guarantee.")
  public void BalanceReceived(String total, String confirmed, String unconfirmed,
      boolean synced, boolean connected, String syncedHeight, String chainHeight) {
    EventDispatcher.dispatchEvent(this, "BalanceReceived", total, confirmed,
        unconfirmed, synced, connected, syncedHeight, chainHeight);
  }

  @SimpleEvent(description = "SDK fast-sync progress text. It is not a percentage; do not interpret start/finished as success.")
  public void FastSyncProgress(String message) {
    EventDispatcher.dispatchEvent(this, "FastSyncProgress", message);
  }

  @SimpleEvent(description = "Attempt ended and keys relocked. advanced only reports a height increase, which can include normal SPV progress; synced is the SDK state, not independent verification of Electrum history.")
  public void FastSyncFinished(boolean advanced, boolean synced, boolean cancelled,
      String beforeHeight, String afterHeight, String chainHeight) {
    EventDispatcher.dispatchEvent(this, "FastSyncFinished", advanced, synced,
        cancelled, beforeHeight, afterHeight, chainHeight);
  }

  @SimpleEvent(description = "Wallet operation failed. Does not include the passphrase or recovery phrase.")
  public void WalletFailed(String operation, String code, String message) {
    EventDispatcher.dispatchEvent(this, "WalletFailed", operation, code, message);
  }
}
