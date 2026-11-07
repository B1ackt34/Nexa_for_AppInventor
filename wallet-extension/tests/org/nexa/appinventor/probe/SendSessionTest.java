package org.nexa.appinventor.probe;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Protocol tests with an app-owned backend. No real SDK, network or funds. */
public final class SendSessionTest {
  static int passed;
  static final String PASSWORD = "synthetic-test-password";
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
  static void test(String name, Runnable work) { work.run(); passed++; System.out.println("PASS " + name); }
  static void expect(String code, Runnable work) {
    try { work.run(); throw new AssertionError("Missing " + code); }
    catch (WalletSession.Failure failure) { check(code.equals(failure.code)); }
  }
  static final class Backend implements WalletSession.Handle {
    boolean encrypted, locked, synced = true, connected = true, failAbort, failSubmit, invalidRecord;
    int builds, aborts, submits, relays;
    long fee = 10;
    Long height;
    String relayedHex;
    Runnable afterPrepare = () -> { }, duringSubmit = () -> { };
    public String recoveryPhrase() { return "SYNTHETIC WORDS ONLY"; }
    public void encrypt(String password) { encrypted = true; }
    public void save() { }
    public Boolean unlock(String password) { if (!encrypted) return null; locked = !PASSWORD.equals(password); return !locked; }
    public void lock() { locked = true; }
    public Boolean lockedState() { return encrypted ? locked : null; }
    public void connect() { }
    public String currentTestnetAddress() { return "SYNTHETIC RECEIVE ADDRESS"; }
    public WalletBalance balance() { return new WalletBalance(100000, 100000, 0, synced, connected, 100, 100); }
    public Runnable startFastSync(Consumer<String> progress) { return () -> { }; }
    public SendData.Draft preparePayment(String address, long amount) {
      check(!locked); builds++;
      SendData.Draft draft = new SendData.Draft(address, amount, fee, String.format("%064x", builds), "ab".repeat(128), new Object());
      afterPrepare.run(); return draft;
    }
    public void abortPayment(SendData.Draft draft) { aborts++; if (failAbort) throw new IllegalStateException("synthetic sensitive SDK text"); }
    public void submitPayment(SendData.Draft draft) { check(locked); submits++; duringSubmit.run(); if (failSubmit) throw new IllegalStateException("synthetic sensitive SDK text"); }
    public void relayPayment(SendData.Record record) { check(locked); relays++; relayedHex = record.hex; }
    public void validatePayment(SendData.Record record) { if (invalidRecord) throw SendData.failure("SEND_RECORD_INVALID", "Invalid synthetic record"); }
    public Long paymentHeight(SendData.Record record) { return height; }
  }
  static final class Store implements WalletSession.Storage {
    final Backend wallet = new Backend();
    boolean exists, confirmed;
    int writes, failWriteAt;
    SendData.Record record;
    public boolean exists() { return exists; }
    public WalletSession.Handle create() { exists = true; return wallet; }
    public WalletSession.Handle open() { return wallet; }
    public void resetBackup() { confirmed = false; }
    public boolean backupConfirmed() { return confirmed; }
    public void confirmBackup() { confirmed = true; }
    public SendData.Record approvedSend() { return record; }
    public void saveApprovedSend(SendData.Record next) {
      writes++; if (writes == failWriteAt) throw SendData.failure("SEND_JOURNAL_FAILED", "Synthetic I/O failure");
      record = next;
    }
  }
  static final class Fixture {
    final Store store = new Store();
    final long[] now = {0};
    final WalletSession session = new WalletSession(store, () -> now[0]);
    Fixture() { session.create(PASSWORD); session.confirmBackup(); }
    SendData.Preview prepare() { return session.preparePayment("SYNTHETIC DESTINATION", "1.23", "1.00", PASSWORD, () -> true); }
    String confirm(SendData.Preview p) { return session.confirmPayment(p.id, () -> true); }
  }
  public static void main(String[] args) {
    test("NEXA text converts exactly at 100 sat per NEXA", () -> {
      check(SendData.amount("0.01") == 1 && SendData.amount("1.23") == 123);
      check(SendData.text(123).equals("1.23"));
      check(SendData.amount("92233720368547758.07") == Long.MAX_VALUE);
    });
    test("invalid formats and sub-satoshi precision are rejected", () -> {
      for (String value : new String[] {"", "0", "-1", "+1", "1,25", "1e2", "NaN", "1.001", "92233720368547758.08"})
        expect("INVALID_AMOUNT", () -> SendData.amount(value));
    });
    test("preparation relocks keys and never submits or persists authorization", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare();
      check(p.draft.amount == 123 && p.draft.fee == 10 && f.store.wallet.locked);
      check(f.store.wallet.submits == 0 && f.store.record == null && f.session.sendBusy());
    });
    test("wrong password never invokes SDK construction", () -> {
      Fixture f = new Fixture();
      expect("INVALID_PASSPHRASE", () -> f.session.preparePayment("DEST", "1", "1", "incorrect-password", () -> true));
      check(f.store.wallet.builds == 0 && f.store.wallet.locked);
    });
    test("sync and connection gates run before unlocking or funding", () -> {
      Fixture f = new Fixture(); f.store.wallet.synced = false;
      expect("WALLET_NOT_SYNCED", f::prepare); f.store.wallet.synced = true; f.store.wallet.connected = false;
      expect("WALLET_OFFLINE", f::prepare); check(f.store.wallet.builds == 0 && f.store.wallet.locked);
    });
    test("backup confirmation is required", () -> {
      Fixture f = new Fixture(); f.store.confirmed = false; expect("BACKUP_REQUIRED", f::prepare); check(f.store.wallet.builds == 0);
    });
    test("fee above cap aborts the exact draft without submission", () -> {
      Fixture f = new Fixture(); f.store.wallet.fee = 101;
      expect("FEE_LIMIT_EXCEEDED", f::prepare); check(f.store.wallet.aborts == 1 && f.store.wallet.submits == 0 && f.store.record == null);
    });
    test("concurrent preview and wallet mutations are refused", () -> {
      Fixture f = new Fixture(); f.prepare(); expect("SEND_BUSY", f::prepare);
      expect("SEND_BUSY", () -> f.session.open(PASSWORD));
      expect("SEND_BUSY", () -> f.session.fastSync(PASSWORD, Runnable::run, timeout -> () -> { }, new WalletSession.FastSyncListener() {
        public void progress(String value) { } public void finished(WalletSession.FastSyncResult result) { } public void failed(String c, String m) { }
      }));
      check(f.store.wallet.builds == 1);
    });
    test("cancel releases only current unapproved draft", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare();
      check(f.session.cancelPaymentPreview("wrong-id").isEmpty());
      check(f.session.cancelPaymentPreview(p.id).equals(p.id));
      check(f.store.wallet.aborts == 1 && !f.session.sendBusy() && f.store.record == null);
    });
    test("expired preview cannot be confirmed even if timer was delayed", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.now[0] += WalletSession.SEND_TTL;
      expect("SEND_EXPIRED", () -> f.confirm(p)); check(f.store.wallet.aborts == 1 && f.store.wallet.submits == 0);
    });
    test("late timer cannot cancel a later preview", () -> {
      Fixture f = new Fixture(); SendData.Preview old = f.prepare(); f.session.cancelPaymentPreview();
      SendData.Preview next = f.prepare(); f.now[0] += WalletSession.SEND_TTL;
      check(!f.session.expirePayment(old.id) && f.session.sendBusy());
      check(f.session.expirePayment(next.id));
    });
    test("background during preparation aborts and relocks", () -> {
      Fixture f = new Fixture(); f.store.wallet.afterPrepare = f.session::invalidateAccess;
      expect("SESSION_LOCKED", f::prepare);
      check(f.store.wallet.aborts == 1 && f.store.wallet.locked && !f.session.isOpen() && f.store.wallet.submits == 0);
    });
    test("background with preview releases its reservations", () -> {
      Fixture f = new Fixture(); f.prepare(); f.session.completeLock(f.session.invalidateAccess());
      check(f.store.wallet.aborts == 1 && !f.session.sendBusy() && f.store.wallet.locked && f.store.record == null);
    });
    test("journal is durable before the first SDK submit call", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare();
      f.store.wallet.duringSubmit = () -> check(f.store.record != null && f.store.record.hex.equals(p.draft.hex) && f.store.record.state.equals("APPROVED"));
      check(f.confirm(p).equals(p.draft.idem));
      check(f.store.wallet.submits == 1 && f.store.record.state.equals("SDK_SUBMITTED") && f.store.wallet.aborts == 0);
    });
    test("journal failure prevents SDK handoff", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.store.failWriteAt = 1;
      expect("SEND_JOURNAL_FAILED", () -> f.confirm(p)); check(f.store.wallet.submits == 0 && f.store.wallet.aborts == 1);
    });
    test("double confirmation cannot send twice", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.confirm(p);
      expect("SEND_PREVIEW_STALE", () -> f.confirm(p)); check(f.store.wallet.submits == 1);
    });
    test("uncertain submission retains signed bytes and never unreserves", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.store.wallet.failSubmit = true;
      expect("SEND_OUTCOME_UNKNOWN", () -> f.confirm(p));
      check(f.store.record.state.equals("UNKNOWN") && f.store.record.hex.equals(p.draft.hex) && f.store.wallet.aborts == 0);
      expect("SEND_PENDING", f::prepare);
    });
    test("post-submit journal failure is uncertain, never an automatic new payment", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.store.failWriteAt = 2;
      expect("SEND_OUTCOME_UNKNOWN", () -> f.confirm(p)); check(f.store.wallet.submits == 1 && f.store.wallet.aborts == 0);
      expect("SEND_PENDING", f::prepare);
    });
    test("background after handoff cannot abort the approved payment", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.store.wallet.duringSubmit = f.session::invalidateAccess;
      f.confirm(p); f.session.completeLock(f.session.invalidateAccess());
      check(f.store.wallet.submits == 1 && f.store.wallet.aborts == 0 && f.store.record != null);
    });
    test("restart preserves pending guard and retry needs a new preview", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.confirm(p);
      WalletSession restarted = new WalletSession(f.store); restarted.open(PASSWORD);
      expect("SEND_PENDING", () -> restarted.preparePayment("DEST", "2", "1", PASSWORD, () -> true));
      SendData.Preview retry = restarted.prepareRetry(() -> true);
      check(f.store.wallet.relays == 0 && f.store.wallet.builds == 1);
      restarted.confirmPayment(retry.id, () -> true);
      check(f.store.wallet.relays == 1 && f.store.wallet.relayedHex.equals(p.draft.hex) && f.store.wallet.builds == 1);
    });
    test("cancelling retry never cancels or frees original payment", () -> {
      Fixture f = new Fixture(); f.confirm(f.prepare()); SendData.Preview p = f.session.prepareRetry(() -> true);
      f.session.cancelPaymentPreview(p.id); check(f.store.wallet.aborts == 0 && f.store.record != null && f.store.wallet.relays == 0);
    });
    test("local unconfirmed is not network acceptance or permission for a new send", () -> {
      Fixture f = new Fixture(); f.confirm(f.prepare()); f.store.wallet.height = -1L;
      check(f.session.lastSendStatus().state.equals("LOCAL_UNCONFIRMED")); expect("SEND_PENDING", f::prepare);
    });
    test("confirmed payment allows the next payment and blocks re-relay", () -> {
      Fixture f = new Fixture(); f.confirm(f.prepare()); f.store.wallet.height = 100L;
      check(f.session.lastSendStatus().state.equals("CONFIRMED"));
      expect("ALREADY_CONFIRMED", () -> f.session.prepareRetry(() -> true)); f.prepare(); check(f.store.wallet.builds == 2);
    });
    test("removed or invalid payment does not trigger a replacement", () -> {
      Fixture f = new Fixture(); f.confirm(f.prepare()); f.store.wallet.height = Long.MIN_VALUE;
      check(f.session.lastSendStatus().state.equals("REMOVED_OR_INVALID")); expect("SEND_PENDING", f::prepare);
    });
    test("invalid record blocks sends while balances stay readable", () -> {
      Fixture f = new Fixture(); f.confirm(f.prepare()); f.store.wallet.invalidRecord = true;
      expect("SEND_RECORD_INVALID", f::prepare); check(f.session.balance().synced);
    });
    test("failed cancellation quarantines subsequent preparations", () -> {
      Fixture f = new Fixture(); f.prepare(); f.store.wallet.failAbort = true;
      expect("SEND_CLEANUP_FAILED", f.session::cancelPaymentPreview);
      expect("SEND_CLEANUP_FAILED", f::prepare); check(f.store.wallet.submits == 0);
    });
    test("record checksum covers amounts, fees, state and exact bytes", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); SendData.Record r = new SendData.Record(p);
      check(!r.checksum().equals(r.state("UNKNOWN").checksum()));
      SendData.Record changed = new SendData.Record(r.requestId, r.destination, r.amount + 1, r.fee, r.idem, r.hex, r.state);
      check(!r.checksum().equals(changed.checksum()));
      check(SendData.hex(SendData.bytes(r.hex)).equals(r.hex));
    });
    test("revocation before confirmation prevents journal and SDK handoff", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare();
      expect("SESSION_LOCKED", () -> f.session.confirmPayment(p.id, () -> false));
      check(f.store.record == null && f.store.wallet.submits == 0 && f.store.wallet.aborts == 1);
    });
    test("connectivity loss after preview aborts without authorizing", () -> {
      Fixture f = new Fixture(); SendData.Preview p = f.prepare(); f.store.wallet.connected = false;
      expect("WALLET_OFFLINE_OR_UNSYNCED", () -> f.confirm(p));
      check(f.store.record == null && f.store.wallet.submits == 0 && f.store.wallet.aborts == 1);
    });
    test("no journal yields explicit NO_SEND", () -> { check(new Fixture().session.lastSendStatus().state.equals("NO_SEND")); });
    System.out.println("Passed: " + passed + "; no Android, SDK runtime or real transactions executed.");
  }
}
