package org.nexa.appinventor.probe;

/** App-owned lifecycle rules; no Nexa API is invented by this abstraction. */
final class WalletSession {
  interface Handle {
    String recoveryPhrase();
    void encrypt(String passphrase);
    void save();
    Boolean unlock(String passphrase);
    void lock();
    String currentTestnetAddress();
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

  WalletSession(Storage storage) {
    this.storage = storage;
  }

  private static void requirePassphrase(String passphrase) {
    if (passphrase == null || passphrase.length() < 12) {
      throw new Failure("PASSPHRASE_REQUIRED", "Use a passphrase of at least 12 characters.");
    }
  }

  synchronized String create(String passphrase) {
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
      String candidate = handle.currentTestnetAddress();
      handle.lock();
      address = candidate;
      return phrase;
    } catch (Exception | LinkageError failure) {
      address = null;
      try { handle.lock(); } catch (Exception | LinkageError ignored) { }
      throw failure;
    }
  }

  synchronized void open(String passphrase) {
    requirePassphrase(passphrase);
    address = null;
    if (handle == null) {
      if (!storage.exists()) {
        throw new Failure("WALLET_NOT_FOUND", "No testnet wallet exists. Use CreateWallet.");
      }
      // Keep the handle for retries; do not open the same DB twice in this process.
      handle = storage.open();
    }
    try {
      Boolean unlocked = handle.unlock(passphrase);
      if (unlocked == null) {
        throw new Failure("WALLET_NOT_ENCRYPTED", "The existing wallet is not encrypted. No changes made.");
      }
      if (!unlocked) {
        throw new Failure("INVALID_PASSPHRASE", "The passphrase is incorrect.");
      }
      String candidate = handle.currentTestnetAddress();
      handle.lock();
      address = candidate;
    } catch (Exception | LinkageError failure) {
      address = null;
      try { handle.lock(); } catch (Exception | LinkageError ignored) { }
      throw failure;
    }
  }

  boolean isOpen() {
    return address != null;
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

  private void requireOpen() {
    if (address == null) {
      throw new Failure("WALLET_NOT_OPEN", "Create or open the testnet wallet first.");
    }
  }
}
