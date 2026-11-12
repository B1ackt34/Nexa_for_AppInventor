package org.nexa.appinventor.probe;

import java.util.*;
import java.util.function.Consumer;

/** Controller-only tests: synthetic phrase/backend, no real wallet or funds. */
public final class RestoreSessionTest {
  static final String PASSWORD = "new-synthetic-password";
  static int passed;
  static void check(boolean value) { if (!value) throw new AssertionError(); }
  static void test(String name, Runnable work) { work.run(); passed++; System.out.println("PASS " + name); }
  static void expect(String code, Runnable work) {
    try { work.run(); throw new AssertionError("Missing " + code); }
    catch (WalletSession.Failure error) {
      check(code.equals(error.code)); check(!error.getMessage().contains("SENSITIVE")); check(error.getCause() == null);
    }
  }
  static final class Store implements WalletSession.Storage {
    boolean exists, sidecar, orphan, confirmed = true, invalid;
    String fail = "", received;
    int creates, restores, resets, validations;
    final List<String> calls = new ArrayList<>();
    Runnable afterValidation = () -> { }, afterRestore = () -> { }, afterSave = () -> { }, afterAddress = () -> { };
    final Backend backend = new Backend();
    final WalletSession session = new WalletSession(this);
    public boolean exists() { return exists; }
    public boolean hasRestoreArtifacts() { return exists || sidecar; }
    public boolean hasSendRecord() { return orphan; }
    public String validateRecoveryPhrase(String phrase) {
      validations++; calls.add("validate");
      if (invalid) throw new WalletSession.Failure("INVALID_RECOVERY_PHRASE", "Invalid synthetic phrase");
      afterValidation.run(); return phrase.trim().toLowerCase(Locale.ROOT);
    }
    public WalletSession.Handle create() { creates++; throw new AssertionError("Never create instead of restore"); }
    public WalletSession.Handle restore(String phrase) {
      restores++; received = phrase; exists = true; calls.add("restore");
      afterRestore.run(); failAt("restore"); return backend;
    }
    public WalletSession.Handle open() { return backend; }
    public void resetBackup() { resets++; calls.add("backup"); failAt("backup"); confirmed = false; }
    public boolean backupConfirmed() { return confirmed; }
    public void confirmBackup() { confirmed = true; }
    void failAt(String stage) { if (fail.equals(stage)) throw new IllegalStateException("SENSITIVE SDK text"); }
    void restore() { session.restore(" SYNTHETIC PHRASE ", PASSWORD, () -> true); }
    final class Backend implements WalletSession.Handle {
      boolean encrypted, locked, ignoreLock;
      String password;
      int phraseReads;
      public String recoveryPhrase() { phraseReads++; return "SENSITIVE"; }
      public void encrypt(String passphrase) { calls.add("encrypt"); failAt("encrypt"); password = passphrase; encrypted = true; }
      public void save() { calls.add("save"); afterSave.run(); failAt("save"); }
      public Boolean unlock(String passphrase) { if (!encrypted) return null; locked = !password.equals(passphrase); return !locked; }
      public void lock() { calls.add("lock"); if (!ignoreLock) locked = true; }
      public Boolean lockedState() { return encrypted ? locked : null; }
      public void connect() { failAt("connect"); }
      public String currentTestnetAddress() { calls.add("address"); afterAddress.run(); failAt("address"); return "PUBLIC ADDRESS"; }
      public WalletBalance balance() { return new WalletBalance(0, 0, 0, false, true, 1, 100); }
      public Runnable startFastSync(Consumer<String> progress) { return () -> { }; }
    }
  }
  public static void main(String[] args) {
    test("restore validates before writes then encrypts saves and locks", () -> {
      Store s = new Store(); s.restore();
      check(s.calls.equals(List.of("validate", "backup", "restore", "encrypt", "save", "address", "lock")));
      check(s.session.isOpen() && s.backend.locked && s.backend.phraseReads == 0 && s.creates == 0);
      check(s.received.equals("synthetic phrase"));
    });
    test("restore does not claim synced balance or implicitly confirm backup", () -> {
      Store s = new Store(); s.restore(); check(!s.session.balance().synced && !s.confirmed);
      expect("BACKUP_REQUIRED", s.session::address); s.session.confirmBackup(); check(s.session.address().equals("PUBLIC ADDRESS"));
    });
    test("existing database rejected before validation or mutation", () -> {
      Store s = new Store(); s.exists = true; expect("WALLET_EXISTS", s::restore);
      check(s.validations == 0 && s.resets == 0 && s.restores == 0 && s.confirmed);
    });
    test("orphan SQLite sidecar is not overwritten", () -> {
      Store s = new Store(); s.sidecar = true; expect("WALLET_EXISTS", s::restore); check(s.resets == 0);
    });
    test("live wallet handle blocks restore even if file disappears", () -> {
      Store s = new Store(); s.restore(); s.exists = false; expect("WALLET_EXISTS", s::restore); check(s.restores == 1);
    });
    test("orphan payment journal blocks restore without deleting it", () -> {
      Store s = new Store(); s.orphan = true; expect("ORPHANED_SEND_RECORD", s::restore);
      check(s.orphan && s.restores == 0 && s.resets == 0);
    });
    test("weak password rejected before validation and writes", () -> {
      Store s = new Store(); expect("PASSPHRASE_REQUIRED", () -> s.session.restore("phrase", "short", () -> true));
      check(s.validations == 0 && !s.exists && s.resets == 0);
    });
    test("invalid phrase rejected without creating database or resetting backup", () -> {
      Store s = new Store(); s.invalid = true; expect("INVALID_RECOVERY_PHRASE", s::restore);
      check(s.confirmed && s.resets == 0 && s.restores == 0 && !s.exists);
    });
    test("revoked request cannot start restore", () -> {
      Store s = new Store(); expect("SESSION_LOCKED", () -> s.session.restore("phrase", PASSWORD, () -> false));
      check(s.validations == 0 && s.restores == 0);
    });
    test("background during validation prevents any metadata or database write", () -> {
      Store s = new Store(); s.afterValidation = s.session::invalidateAccess;
      expect("SESSION_LOCKED", s::restore); check(s.resets == 0 && !s.exists);
    });
    test("background during SDK recovery still encrypts saves and locks", () -> {
      Store s = new Store(); s.afterRestore = s.session::invalidateAccess;
      expect("SESSION_LOCKED", s::restore);
      check(s.exists && s.backend.encrypted && s.backend.locked && s.calls.contains("save"));
      check(!s.calls.contains("address") && !s.session.isOpen());
      s.session.open(PASSWORD); check(s.session.isOpen());
    });
    test("background during save suppresses session opening and relocks", () -> {
      Store s = new Store(); s.afterSave = s.session::invalidateAccess; expect("SESSION_LOCKED", s::restore);
      check(!s.session.isOpen() && s.backend.locked && !s.calls.contains("address"));
    });
    test("background during address read prevents publishing recovered session", () -> {
      Store s = new Store(); s.afterAddress = s.session::invalidateAccess; expect("SESSION_LOCKED", s::restore);
      check(!s.session.isOpen() && s.backend.locked);
    });
    test("backup persistence failure prevents SDK restore", () -> {
      Store s = new Store(); s.fail = "backup"; expect("RESTORE_FAILED", s::restore); check(s.restores == 0 && !s.exists);
    });
    test("failed SDK recovery retains partial database and never falls back to create", () -> {
      Store s = new Store(); s.fail = "restore"; expect("RESTORE_FAILED", s::restore);
      check(s.exists && !s.session.isOpen() && s.creates == 0); expect("WALLET_EXISTS", s::restore); check(s.restores == 1);
    });
    test("encryption failure is not success and never resets database", () -> {
      Store s = new Store(); s.fail = "encrypt"; expect("RESTORE_FAILED", s::restore);
      check(s.exists && !s.session.isOpen() && !s.calls.contains("save"));
    });
    test("save failure locks the encrypted handle and rejects another restore", () -> {
      Store s = new Store(); s.fail = "save"; expect("RESTORE_FAILED", s::restore);
      check(s.backend.locked && s.exists && !s.session.isOpen()); expect("WALLET_EXISTS", s::restore);
    });
    test("address failure preserves encrypted wallet without leaking SDK text", () -> {
      Store s = new Store(); s.fail = "address"; expect("RESTORE_FAILED", s::restore);
      check(s.backend.encrypted && s.backend.locked && !s.session.isOpen());
    });
    test("unverified locking fails closed", () -> {
      Store s = new Store(); s.backend.ignoreLock = true; expect("LOCK_NOT_CONFIRMED", s::restore);
      check(!s.session.isOpen()); expect("LOCK_NOT_CONFIRMED", s::restore);
    });
    test("restored wallet reopens with new local password and rejects another", () -> {
      Store s = new Store(); s.restore(); s.session.completeLock(s.session.invalidateAccess());
      expect("INVALID_PASSPHRASE", () -> s.session.open("wrong-password-long")); s.session.open(PASSWORD);
      check(s.session.isOpen() && s.backend.locked && s.restores == 1);
    });
    test("queued cancellation releases both phrase and password without executing", () -> {
      ForegroundGate gate = new ForegroundGate(); int[] executed = {0}, done = {0};
      GuardedRequest r = new GuardedRequest(gate, "phrase", PASSWORD, x -> executed[0]++, x -> done[0]++);
      check(r.retainsSecret()); r.cancel(); check(!r.retainsSecret()); r.run(); check(executed[0] == 0 && done[0] == 1);
    });
    test("dual-secret request releases references on completion and exception", () -> {
      ForegroundGate gate = new ForegroundGate();
      GuardedRequest r = new GuardedRequest(gate, "phrase", PASSWORD, x -> {
        check(x.secret().equals("phrase") && x.secondSecret().equals(PASSWORD));
      }, x -> { }); r.run(); check(!r.retainsSecret());
      GuardedRequest broken = new GuardedRequest(gate, "phrase", PASSWORD, x -> { throw new IllegalStateException(); }, x -> { });
      try { broken.run(); throw new AssertionError(); } catch (IllegalStateException expected) { }
      check(!broken.retainsSecret());
    });
    test("revoked dual-secret request cannot expose either field", () -> {
      ForegroundGate gate = new ForegroundGate(); GuardedRequest r = new GuardedRequest(gate, "phrase", PASSWORD, x -> { }, x -> { });
      gate.pause(); expect("SESSION_LOCKED", r::secret); expect("SESSION_LOCKED", r::secondSecret); r.cancel();
    });
    System.out.println("Passed: " + passed + "; controller only, no Android or SDK wallet runtime.");
  }
}
