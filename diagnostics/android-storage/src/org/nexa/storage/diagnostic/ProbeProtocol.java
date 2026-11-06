package org.nexa.storage.diagnostic;

/** App-owned experiment protocol. All SDK calls are confined to the Android adapter. */
public final class ProbeProtocol {
  public static final String TEST_PASSWORD = "DiagnosticOnly-NoFunds-StorageProbe-0.1.0";
  public interface Handle {
    Boolean lockedState();
    Boolean unlock(String password);
    String phrase();
    void encrypt(String password);
    void save();
    void lock();
  }
  public interface Backend { boolean exists(); Handle create(); Handle open(); }
  public interface Observer {
    void mark(String phase);
    void observe(String phase, String phrase);
    void state(String key, String value);
    void armInterruption();
    void stopNow();
  }
  public static final class Failure extends RuntimeException {
    public final String code;
    Failure(String code) { super(code); this.code = code; }
  }
  private final Backend backend;
  private Handle handle;
  public ProbeProtocol(Backend backend) { this.backend = backend; }

  public void create(Observer observer, boolean interruptDuring, boolean interruptAfter) {
    if (handle != null || backend.exists()) throw new Failure("DIAGNOSTIC_DATABASE_EXISTS");
    observer.mark("CREATING");
    if (interruptDuring) observer.armInterruption();
    handle = backend.create();
    observer.mark("CREATED");
    if (interruptAfter) { observer.stopNow(); return; }
    Boolean locked = handle.lockedState();
    observer.state("encrypted", Boolean.toString(locked != null));
    if (locked != null) throw new Failure("UNEXPECTED_ENCRYPTED_NEW_WALLET");
    // Deliberately observe the initial unencrypted SDK state in this disposable app only.
    observer.observe("AFTER_CREATE_BEFORE_ENCRYPT", handle.phrase());
  }

  public void encrypt(Observer observer, boolean interruptDuring) {
    Handle wallet = load();
    if (wallet.lockedState() != null) throw new Failure("ALREADY_ENCRYPTED_USE_REOPEN");
    String phrase = wallet.phrase();
    observer.mark("ENCRYPTING");
    if (interruptDuring) observer.armInterruption();
    try {
      wallet.encrypt(TEST_PASSWORD);
      wallet.save();
    } finally {
      wallet.lock();
    }
    if (!Boolean.TRUE.equals(wallet.lockedState())) throw new Failure("LOCK_NOT_CONFIRMED");
    observer.mark("ENCRYPTED_AND_LOCKED");
    observer.state("encrypted", "true");
    observer.state("locked", "true");
    observer.observe("AFTER_ENCRYPT_AND_LOCK", phrase);
  }

  public void reopen(Observer observer) {
    if (handle != null) throw new Failure("RESTART_DIAGNOSTIC_PROCESS_FIRST");
    observer.mark("REOPENING");
    Handle wallet = load();
    Boolean state = wallet.lockedState();
    observer.state("encrypted", Boolean.toString(state != null));
    String phrase;
    try {
      if (state != null && !Boolean.TRUE.equals(wallet.unlock(TEST_PASSWORD))) {
        throw new Failure("DIAGNOSTIC_PASSWORD_REJECTED");
      }
      phrase = wallet.phrase();
    } finally {
      wallet.lock();
    }
    if (state != null && !Boolean.TRUE.equals(wallet.lockedState())) throw new Failure("LOCK_NOT_CONFIRMED");
    observer.state("locked", state == null ? "NOT_ENCRYPTED" : "true");
    observer.mark("REOPENED");
    observer.observe("AFTER_PROCESS_RESTART", phrase);
  }

  private Handle load() {
    if (handle == null) {
      if (!backend.exists()) throw new Failure("DIAGNOSTIC_DATABASE_ABSENT");
      // Existing corrupt/incomplete data are never replaced on failure.
      handle = backend.open();
    }
    return handle;
  }
}
