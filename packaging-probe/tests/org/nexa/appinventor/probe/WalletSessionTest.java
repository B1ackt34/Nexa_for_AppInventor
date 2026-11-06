package org.nexa.appinventor.probe;

/** Desktop tests of app-owned lifecycle rules. Does not emulate Nexa or Android. */
public final class WalletSessionTest {
  private static int passed;
  private static final class FakeStorage implements WalletSession.Storage {
    boolean exists, confirmed;
    int creates, opens;
    RuntimeException openFailure;
    final FakeHandle wallet = new FakeHandle();
    public boolean exists() { return exists; }
    public WalletSession.Handle create() { creates++; exists = true; wallet.connected = true; return wallet; }
    public WalletSession.Handle open() {
      opens++;
      if (openFailure != null) throw openFailure;
      wallet.connected = false;
      return wallet;
    }
    public void resetBackup() { confirmed = false; }
    public boolean backupConfirmed() { return confirmed; }
    public void confirmBackup() { confirmed = true; }
  }
  private static final class FakeHandle implements WalletSession.Handle {
    boolean encrypted, locked, connected;
    boolean ignoreLock;
    Runnable afterUnlock = () -> { }, afterSave = () -> { }, afterConnect = () -> { };
    int connects;
    RuntimeException connectFailure;
    boolean failSave, checkedFailure;
    int phraseReads;
    int balanceReads, unlocks, addressReads;
    RuntimeException balanceFailure;
    RuntimeException fastStartFailure;
    java.util.function.Consumer<String> fastProgress;
    int fastStarts, aborts;
    boolean finishInline;
    WalletBalance reading = new WalletBalance(0, 0, 0, false, false, -1, -1);
    String password;
    public String recoveryPhrase() { phraseReads++; return "FAKE TEST PHRASE"; }
    public void encrypt(String passphrase) { password = passphrase; encrypted = true; locked = false; }
    public void save() { if (failSave) throw new IllegalStateException("disk failure"); afterSave.run(); }
    public Boolean unlock(String passphrase) {
      unlocks++;
      if (!encrypted) return null;
      boolean ok = password.equals(passphrase); locked = !ok; afterUnlock.run(); return ok;
    }
    public void lock() { if (!ignoreLock) locked = true; }
    public Boolean lockedState() { return encrypted ? locked : null; }
    public void connect() {
      connects++;
      if (locked) throw new IllegalStateException("keys required to attach chain");
      if (connectFailure != null) throw connectFailure;
      connected = true; afterConnect.run();
    }
    public String currentTestnetAddress() {
      addressReads++;
      if (checkedFailure) return sneaky(new java.io.IOException("checked SDK failure"));
      if (locked) throw new IllegalStateException("locked during derivation");
      return "FAKE TEST ADDRESS";
    }
    public WalletBalance balance() {
      balanceReads++;
      if (balanceFailure != null) throw balanceFailure;
      return reading;
    }
    public Runnable startFastSync(java.util.function.Consumer<String> progress) {
      fastStarts++;
      fastProgress = progress;
      if (fastStartFailure != null) throw fastStartFailure;
      progress.accept("start");
      if (finishInline) progress.accept("finished");
      return () -> aborts++;
    }
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> String sneaky(Throwable t) throws T { throw (T) t; }
  }
  private static void check(boolean condition) {
    if (!condition) throw new AssertionError();
  }
  private static void expect(String code, Runnable operation) {
    try { operation.run(); throw new AssertionError("expected " + code); }
    catch (WalletSession.Failure failure) { check(code.equals(failure.code)); }
  }
  private static void test(String name, Runnable work) {
    work.run(); passed++; System.out.println("PASS " + name);
  }
  private static final class Callbacks implements java.util.concurrent.Executor {
    final java.util.Queue<Runnable> queued = new java.util.ArrayDeque<>();
    public void execute(Runnable work) { queued.add(work); }
    void drain() { while (!queued.isEmpty()) queued.remove().run(); }
  }
  private static final class Watchdog implements WalletSession.Watchdog {
    Runnable timeout;
    boolean cancelled;
    public Runnable schedule(Runnable timeout) {
      this.timeout = timeout; cancelled = false; return () -> cancelled = true;
    }
    void fire() { if (!cancelled && timeout != null) timeout.run(); }
  }
  private static final class Listener implements WalletSession.FastSyncListener {
    final java.util.List<String> progress = new java.util.ArrayList<>();
    final java.util.List<WalletSession.FastSyncResult> results = new java.util.ArrayList<>();
    final java.util.List<String> failures = new java.util.ArrayList<>();
    public void progress(String message) { progress.add(message); }
    public void finished(WalletSession.FastSyncResult result) { results.add(result); }
    public void failed(String code, String message) { failures.add(code); }
  }
  private static final class FastFixture {
    final FakeStorage storage = new FakeStorage();
    final WalletSession session = new WalletSession(storage);
    final Callbacks callbacks = new Callbacks();
    final Watchdog watchdog = new Watchdog();
    final Listener listener = new Listener();
    FastFixture() {
      session.create("test-only-passphrase");
      storage.wallet.reading = new WalletBalance(0, 0, 0, false, true, 100, 1000);
    }
    void start() { session.fastSync("test-only-passphrase", callbacks, watchdog, listener); }
    void finish(boolean synced, long height) {
      storage.wallet.reading = new WalletBalance(0, 0, 0, synced, true, height, 1000);
      storage.wallet.fastProgress.accept("finished"); callbacks.drain();
    }
  }
  public static void main(String[] args) {
    String password = "test-only-passphrase";
    test("creation encrypts, saves and relocks before reporting a public session", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      check(session.create(password).equals("FAKE TEST PHRASE"));
      check(storage.wallet.encrypted && storage.wallet.locked && session.isOpen());
      check(storage.wallet.phraseReads == 1);
    });
    test("existing database cannot be recreated", () -> {
      FakeStorage storage = new FakeStorage(); storage.exists = true;
      expect("WALLET_EXISTS", () -> new WalletSession(storage).create(password));
      check(storage.creates == 0 && storage.opens == 0);
    });
    test("missing database is never created by OpenWallet", () -> {
      FakeStorage storage = new FakeStorage();
      expect("WALLET_NOT_FOUND", () -> new WalletSession(storage).open(password));
      check(storage.creates == 0 && storage.opens == 0);
    });
    test("corrupt database open failure never falls back to creation", () -> {
      FakeStorage storage = new FakeStorage(); storage.exists = true;
      storage.openFailure = new IllegalStateException("corrupt database");
      try { new WalletSession(storage).open(password); throw new AssertionError(); }
      catch (WalletSession.Failure expected) {
        check(expected.code.equals("IllegalStateException") && expected.getMessage().contains("READ_DATABASE"));
        check(storage.creates == 0 && storage.opens == 1);
      }
    });
    test("restart reopens same address without reading phrase or generating keys", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession first = new WalletSession(storage);
      first.create(password); first.confirmBackup(); String before = first.address();
      WalletSession restarted = new WalletSession(storage); restarted.open(password);
      check(before.equals(restarted.address()) && storage.creates == 1 && storage.opens == 1);
      check(storage.wallet.phraseReads == 1 && storage.wallet.locked);
    });
    test("wrong passphrase leaves keys locked and revokes public session", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); session.confirmBackup();
      expect("INVALID_PASSPHRASE", () -> session.open("wrong-passphrase"));
      check(storage.wallet.locked && !session.isOpen() && storage.creates == 1);
      expect("WALLET_NOT_OPEN", session::address);
    });
    test("address requires explicit backup confirmation", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); expect("BACKUP_REQUIRED", session::address);
      session.confirmBackup(); check(session.address().equals(session.address()));
    });
    test("stale backup confirmation is cleared for a genuinely new wallet", () -> {
      FakeStorage storage = new FakeStorage(); storage.confirmed = true;
      WalletSession session = new WalletSession(storage); session.create(password);
      expect("BACKUP_REQUIRED", session::address);
    });
    test("failed save does not expose an address or allow replacing partial database", () -> {
      FakeStorage storage = new FakeStorage(); storage.wallet.failSave = true;
      WalletSession session = new WalletSession(storage);
      try { session.create(password); throw new AssertionError(); }
      catch (IllegalStateException expected) { check(!session.isOpen() && storage.wallet.locked); }
      expect("WALLET_EXISTS", () -> session.create(password));
    });
    test("sneaky checked SDK exception also relocks the keys", () -> {
      FakeStorage storage = new FakeStorage(); storage.wallet.checkedFailure = true;
      WalletSession session = new WalletSession(storage);
      try { session.create(password); throw new AssertionError(); }
      catch (Exception expected) { check(expected instanceof java.io.IOException); }
      check(storage.wallet.locked && !session.isOpen());
    });
    test("unencrypted existing database is not silently rekeyed", () -> {
      FakeStorage storage = new FakeStorage(); storage.exists = true;
      expect("WALLET_NOT_ENCRYPTED", () -> new WalletSession(storage).open(password));
      check(!storage.wallet.encrypted && storage.creates == 0);
    });
    test("short passphrase performs no storage operations", () -> {
      FakeStorage storage = new FakeStorage();
      expect("PASSPHRASE_REQUIRED", () -> new WalletSession(storage).create("short"));
      check(storage.creates == 0 && !storage.exists);
    });
    test("balance is unavailable before a wallet is open", () -> {
      FakeStorage storage = new FakeStorage();
      expect("WALLET_NOT_OPEN", () -> new WalletSession(storage).balance());
      check(storage.wallet.balanceReads == 0);
    });
    test("balance reads do not unlock keys, show phrase or derive another address", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password);
      int phrases = storage.wallet.phraseReads, addresses = storage.wallet.addressReads;
      session.balance();
      check(storage.wallet.locked && storage.wallet.unlocks == 0);
      check(storage.wallet.phraseReads == phrases && storage.wallet.addressReads == addresses);
    });
    test("all Long digits survive conversion to NEXA text", () -> {
      WalletBalance balance = new WalletBalance(Long.MAX_VALUE, 9007199254740993L,
          Long.MIN_VALUE, true, true, Long.MAX_VALUE, Long.MAX_VALUE);
      check(balance.total.equals("92233720368547758.07"));
      check(balance.confirmed.equals("90071992547409.93"));
      check(balance.unconfirmed.equals("-92233720368547758.08"));
      check(balance.syncedHeight.equals("9223372036854775807"));
    });
    test("minor units convert exactly at the Nexa 100-sat scale", () -> {
      WalletBalance balance = new WalletBalance(1, 100, -1, false, false, -1, 0);
      check(balance.total.equals("0.01") && balance.confirmed.equals("1.00"));
      check(balance.unconfirmed.equals("-0.01") && balance.syncedHeight.equals("-1"));
    });
    test("SDK totals are not recomputed from other readings", () -> {
      WalletBalance balance = new WalletBalance(700, 500, 50, true, true, 1, 1);
      check(balance.total.equals("7.00") && balance.confirmed.equals("5.00"));
      check(balance.unconfirmed.equals("0.50"));
    });
    test("offline and unsynced zero retains its incomplete-state flags", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); WalletBalance balance = session.balance();
      check(balance.total.equals("0.00") && !balance.connected && !balance.synced);
      check(balance.chainHeight.equals("-1"));
    });
    test("repeated balance calls read fresh backend values", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); WalletBalance first = session.balance();
      storage.wallet.reading = new WalletBalance(12345, 12000, 345, true, true, 10, 10);
      WalletBalance second = session.balance();
      check(first.total.equals("0.00") && second.total.equals("123.45"));
      check(second.synced && second.connected && storage.wallet.balanceReads == 2);
    });
    test("balance failure never becomes a fabricated zero or a new wallet", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); storage.wallet.balanceFailure = new IllegalStateException("read failure");
      try { session.balance(); throw new AssertionError(); }
      catch (IllegalStateException expected) { check(storage.creates == 1 && storage.wallet.locked); }
    });
    test("failed unlock also revokes access to balances", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); expect("INVALID_PASSPHRASE", () -> session.open("wrong-passphrase"));
      expect("WALLET_NOT_OPEN", session::balance); check(storage.wallet.balanceReads == 0);
    });
    test("fast sync retains unlock until SDK completion, then relocks", () -> {
      FastFixture f = new FastFixture(); f.start(); f.callbacks.drain();
      check(f.session.fastSyncRunning() && !f.storage.wallet.locked);
      f.finish(true, 1000);
      check(!f.session.fastSyncRunning() && f.storage.wallet.locked);
      check(f.listener.results.size() == 1 && f.watchdog.cancelled);
      check(f.listener.results.get(0).advanced && f.listener.results.get(0).synced);
      check(f.storage.wallet.phraseReads == 1);
    });
    test("finished without a jump or sync is not reported as successful", () -> {
      FastFixture f = new FastFixture(); f.start(); f.finish(false, 100);
      check(!f.listener.results.get(0).advanced && !f.listener.results.get(0).synced);
      check(f.storage.wallet.locked);
    });
    test("normal SPV progress can advance height while fast sync remains unsynced", () -> {
      FastFixture f = new FastFixture(); f.start(); f.finish(false, 110);
      check(f.listener.results.get(0).advanced && !f.listener.results.get(0).synced);
    });
    test("already synced fast sync does not unlock or start an SDK search", () -> {
      FastFixture f = new FastFixture(); f.storage.wallet.reading = new WalletBalance(0, 0, 0, true, true, 1000, 1000);
      f.start(); check(f.storage.wallet.fastStarts == 0 && f.storage.wallet.unlocks == 0);
      check(f.listener.results.get(0).synced && !f.session.fastSyncRunning());
    });
    test("unknown chain tip never starts or unlocks fast sync", () -> {
      FastFixture f = new FastFixture(); f.storage.wallet.reading = new WalletBalance(0, 0, 0, false, false, 0, 0);
      expect("CHAIN_TIP_UNAVAILABLE", f::start);
      check(f.storage.wallet.fastStarts == 0 && f.storage.wallet.unlocks == 0);
    });
    test("wrong fast sync passphrase cannot start SDK and keys stay locked", () -> {
      FastFixture f = new FastFixture();
      expect("INVALID_PASSPHRASE", () -> f.session.fastSync("incorrect-passphrase", f.callbacks, f.watchdog, f.listener));
      check(f.storage.wallet.fastStarts == 0 && f.storage.wallet.locked && !f.session.fastSyncRunning());
    });
    test("duplicate sync and wallet reopen are rejected during an active search", () -> {
      FastFixture f = new FastFixture(); f.start();
      expect("FAST_SYNC_RUNNING", f::start);
      expect("FAST_SYNC_RUNNING", () -> f.session.open(password));
      expect("FAST_SYNC_RUNNING", () -> f.session.create(password));
      check(f.storage.wallet.fastStarts == 1 && !f.storage.wallet.locked);
      f.finish(true, 1000);
    });
    test("SDK startup exception relocks and quarantines a possibly launched job", () -> {
      FastFixture f = new FastFixture(); f.storage.wallet.fastStartFailure = new IllegalStateException("SDK failed");
      expect("FAST_SYNC_START_FAILED", f::start);
      check(f.storage.wallet.locked && f.session.fastSyncRunning());
      expect("FAST_SYNC_RUNNING", () -> f.session.open(password));
      f.storage.wallet.fastProgress.accept("finished"); f.callbacks.drain();
      check(!f.session.fastSyncRunning() && f.listener.results.isEmpty());
    });
    test("cooperative cancel does not falsely announce immediate completion", () -> {
      FastFixture f = new FastFixture(); f.start(); f.session.cancelFastSync();
      check(f.storage.wallet.aborts == 1 && f.session.fastSyncRunning() && f.listener.results.isEmpty());
      f.finish(false, 100);
      check(f.listener.results.get(0).cancelled && f.storage.wallet.locked);
    });
    test("timeout requests abort and relocks, but quarantines the in-flight SDK handle", () -> {
      FastFixture f = new FastFixture(); f.start(); f.watchdog.fire();
      check(f.storage.wallet.aborts == 1 && f.storage.wallet.locked && f.session.fastSyncRunning());
      check(f.listener.failures.equals(java.util.List.of("FAST_SYNC_TIMEOUT")));
      expect("FAST_SYNC_RUNNING", f::start); f.watchdog.fire();
      check(f.listener.failures.size() == 1);
      f.finish(false, 100);
      check(!f.session.fastSyncRunning() && f.listener.results.isEmpty());
    });
    test("duplicate terminal callbacks are delivered only once", () -> {
      FastFixture f = new FastFixture(); f.start(); f.finish(true, 1000); f.finish(true, 1000);
      check(f.listener.results.size() == 1 && f.listener.failures.isEmpty());
    });
    test("late callbacks from an old search cannot finish a new search", () -> {
      FastFixture f = new FastFixture(); f.start(); java.util.function.Consumer<String> old = f.storage.wallet.fastProgress;
      f.finish(false, 101); f.start(); old.accept("finished"); f.callbacks.drain();
      check(f.session.fastSyncRunning() && !f.storage.wallet.locked);
      f.finish(true, 1000); check(f.listener.results.size() == 2);
    });
    test("inline terminal callback before abort handle assignment is safe", () -> {
      FastFixture f = new FastFixture(); f.storage.wallet.finishInline = true;
      f.session.fastSync(password, Runnable::run, f.watchdog, f.listener);
      check(!f.session.fastSyncRunning() && f.storage.wallet.locked && f.watchdog.timeout == null);
      check(f.listener.results.size() == 1);
    });
    test("null progress is ignored and balance reads remain available during sync", () -> {
      FastFixture f = new FastFixture(); f.start(); f.storage.wallet.fastProgress.accept(null); f.callbacks.drain();
      check(f.listener.progress.equals(java.util.List.of("start")));
      check(f.session.balance().syncedHeight.equals("100") && f.session.fastSyncRunning());
      f.finish(true, 1000);
    });
    test("cancel without an active search reports the explicit error", () -> {
      FastFixture f = new FastFixture(); expect("FAST_SYNC_NOT_RUNNING", f.session::cancelFastSync);
    });
    test("cold encrypted reopen unlocks before chain needs private-key derivation", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession first = new WalletSession(storage);
      first.create(password); first.confirmBackup();
      WalletSession restarted = new WalletSession(storage); restarted.open(password);
      check(storage.wallet.connects == 1 && storage.wallet.connected && storage.wallet.locked);
      check(restarted.address().equals("FAKE TEST ADDRESS") && storage.creates == 1);
    });
    test("cold wrong-password retry reuses disconnected handle and never connects early", () -> {
      FakeStorage storage = new FakeStorage(); new WalletSession(storage).create(password);
      WalletSession restarted = new WalletSession(storage);
      expect("INVALID_PASSPHRASE", () -> restarted.open("incorrect-passphrase"));
      check(storage.wallet.connects == 0 && storage.wallet.locked);
      restarted.open(password);
      check(storage.opens == 1 && storage.creates == 1 && storage.wallet.connected);
    });
    test("chain-attachment failure relocks, reports its stage and never creates keys", () -> {
      FakeStorage storage = new FakeStorage(); new WalletSession(storage).create(password);
      storage.wallet.connectFailure = new IllegalStateException("sensitive SDK text");
      WalletSession restarted = new WalletSession(storage);
      try { restarted.open(password); throw new AssertionError(); }
      catch (WalletSession.Failure expected) {
        check(expected.getMessage().contains("CONNECT_CHAIN"));
        check(!expected.getMessage().contains("sensitive SDK text"));
      }
      check(storage.wallet.locked && !restarted.isOpen() && storage.creates == 1);
    });
    test("background invalidates access before asynchronous key lock completes", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); long ticket = session.invalidateAccess();
      check(!session.isOpen()); expect("WALLET_NOT_OPEN", session::balance);
      session.completeLock(ticket); check(storage.wallet.locked);
      session.open(password); session.completeLock(ticket); check(session.isOpen());
    });
    test("pause during unlock relocks and cannot publish an open session", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); storage.wallet.afterUnlock = session::invalidateAccess;
      expect("SESSION_LOCKED", () -> session.open(password));
      check(storage.wallet.locked && !session.isOpen() && storage.wallet.connects == 0);
    });
    test("pause during chain attachment relocks without replacing wallet", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); storage.wallet.afterConnect = session::invalidateAccess;
      expect("SESSION_LOCKED", () -> session.open(password));
      check(storage.wallet.locked && !session.isOpen() && storage.creates == 1);
    });
    test("interrupted creation persists encryption then suppresses secret result", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      storage.wallet.afterSave = session::invalidateAccess;
      expect("SESSION_LOCKED", () -> session.create(password));
      check(storage.wallet.encrypted && storage.wallet.locked && !session.isOpen());
      expect("WALLET_EXISTS", () -> session.create(password));
    });
    test("unconfirmed SDK lock fails closed and prohibits another unlock", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password); storage.wallet.ignoreLock = true;
      expect("LOCK_NOT_CONFIRMED", () -> session.open(password));
      int unlocks = storage.wallet.unlocks;
      expect("LOCK_NOT_CONFIRMED", () -> session.open(password));
      check(!session.isOpen() && storage.wallet.unlocks == unlocks);
    });
    test("background fast sync requests abort and retains guard until terminal", () -> {
      FastFixture f = new FastFixture(); f.start();
      f.session.completeLock(f.session.invalidateAccess());
      check(f.storage.wallet.locked && f.storage.wallet.aborts == 1 && !f.session.isOpen());
      expect("FAST_SYNC_RUNNING", () -> f.session.open(password));
      f.finish(false, 100); check(!f.session.fastSyncRunning() && f.listener.results.isEmpty());
      f.session.open(password); check(f.session.isOpen());
    });
    test("unfinished backup can be recovered only after password authentication", () -> {
      FakeStorage storage = new FakeStorage(); WalletSession session = new WalletSession(storage);
      session.create(password);
      expect("INVALID_PASSPHRASE", () -> session.showRecoveryPhrase("incorrect-passphrase", () -> true));
      check(storage.wallet.locked);
      check(session.showRecoveryPhrase(password, () -> true).equals("FAKE TEST PHRASE"));
      check(storage.wallet.locked); session.confirmBackup();
      expect("BACKUP_ALREADY_CONFIRMED", () -> session.showRecoveryPhrase(password, () -> true));
    });
    test("cancelled queued credential is released and never executed", () -> {
      ForegroundGate gate = new ForegroundGate(); int[] runs = {0};
      GuardedRequest request = new GuardedRequest(gate, password, r -> runs[0]++, r -> { });
      check(request.retainsSecret()); gate.pause(); request.cancel(); gate.resume(); request.run();
      check(!request.retainsSecret() && runs[0] == 0 && !request.valid());
    });
    test("pause and resume do not revive old callback generations", () -> {
      ForegroundGate gate = new ForegroundGate(); long token = gate.capture();
      check(gate.current(token)); gate.pause(); gate.resume(); check(!gate.current(token));
      GuardedRequest fresh = new GuardedRequest(gate, password, r -> check(r.secret().equals(password)), r -> { });
      fresh.run(); check(!fresh.retainsSecret());
    });
    test("revocation during FastSync unlock never hands keys to SDK search", () -> {
      FastFixture f = new FastFixture(); f.storage.wallet.afterUnlock = f.session::invalidateAccess;
      expect("SESSION_LOCKED", f::start);
      check(f.storage.wallet.locked && f.storage.wallet.fastStarts == 0 && !f.session.isOpen());
    });
    System.out.println("Passed: " + passed + "; Android/Nexa execution is not covered by these tests.");
  }
}
