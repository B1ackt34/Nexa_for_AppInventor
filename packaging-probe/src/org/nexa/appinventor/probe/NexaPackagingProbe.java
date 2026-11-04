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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Experimental testnet wallet. No mainnet, import, balance or sending operations. */
@Extension(description = "Experimental Nexa testnet wallet: create, reopen and read its receive address.")
@DesignerComponent(version = 3, description = "Experimental Nexa testnet wallet",
    category = ComponentCategory.EXTENSION, nonVisible = true, androidMinSdk = 29)
@SimpleObject(external = true)
public final class NexaPackagingProbe extends AndroidNonvisibleComponent {
  private static final Object INITIALIZATION_LOCK = new Object();
  private static volatile boolean initialized;
  private static final ExecutorService WALLET_WORKER = Executors.newSingleThreadExecutor(
      runnable -> new Thread(runnable, "NexaWallet"));
  private static volatile WalletSession session;
  private final android.content.Context applicationContext;
  private boolean initializing;

  public NexaPackagingProbe(ComponentContainer container) {
    super(container.$form());
    applicationContext = container.$context().getApplicationContext();
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

  private void walletOperation(String operation, Runnable work) {
    WALLET_WORKER.execute(() -> {
      try {
        work.run();
      } catch (WalletSession.Failure failure) {
        form.runOnUiThread(() -> WalletFailed(operation, failure.code, failure.getMessage()));
      } catch (Exception | LinkageError failure) {
        // Do not forward SDK exception text: it may include secret inputs.
        String code = failure.getClass().getSimpleName();
        form.runOnUiThread(() -> WalletFailed(operation, code,
            "Nexa could not complete " + operation + ". The wallet was not automatically replaced."));
      }
    });
  }

  @SimpleFunction(description = "Create a new encrypted testnet wallet. Passphrase: at least 12 characters. Fails if a wallet exists.")
  public void CreateWallet(String passphrase) {
    walletOperation("CreateWallet", () -> {
      String phrase = walletSession().create(passphrase);
      form.runOnUiThread(() -> WalletCreated(phrase));
    });
  }

  @SimpleFunction(description = "Reopen the existing testnet wallet with its passphrase. Never creates or resets a wallet.")
  public void OpenWallet(String passphrase) {
    walletOperation("OpenWallet", () -> {
      walletSession().open(passphrase);
      form.runOnUiThread(this::WalletOpened);
    });
  }

  @SimpleFunction(description = "Confirm you have recorded the recovery phrase. Enables GetAddress; this does not verify the backup contents.")
  public void ConfirmBackup() {
    walletOperation("ConfirmBackup", () -> {
      walletSession().confirmBackup();
      form.runOnUiThread(this::BackupConfirmed);
    });
  }

  @SimpleFunction(description = "Read the public receive address cached at create/open, without rotating it. Requires backup confirmation.")
  public void GetAddress() {
    walletOperation("GetAddress", () -> {
      String address = walletSession().address();
      form.runOnUiThread(() -> AddressReceived(address));
    });
  }

  @SimpleEvent(description = "New encrypted testnet wallet created. Show recoveryPhrase privately once for backup; never log or transmit it.")
  public void WalletCreated(String recoveryPhrase) {
    EventDispatcher.dispatchEvent(this, "WalletCreated", recoveryPhrase);
  }

  @SimpleEvent(description = "Existing testnet wallet opened. Keys are relocked; this does not imply blockchain sync.")
  public void WalletOpened() { EventDispatcher.dispatchEvent(this, "WalletOpened"); }

  @SimpleEvent(description = "Backup confirmation saved.")
  public void BackupConfirmed() { EventDispatcher.dispatchEvent(this, "BackupConfirmed"); }

  @SimpleEvent(description = "Public testnet receive address from the last create/open.")
  public void AddressReceived(String address) {
    EventDispatcher.dispatchEvent(this, "AddressReceived", address);
  }

  @SimpleEvent(description = "Wallet operation failed. Does not include the passphrase or recovery phrase.")
  public void WalletFailed(String operation, String code, String message) {
    EventDispatcher.dispatchEvent(this, "WalletFailed", operation, code, message);
  }
}
