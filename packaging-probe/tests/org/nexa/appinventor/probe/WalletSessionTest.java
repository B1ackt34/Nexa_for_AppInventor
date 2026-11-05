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
    public WalletSession.Handle create() { creates++; exists = true; return wallet; }
    public WalletSession.Handle open() { opens++; if (openFailure != null) throw openFailure; return wallet; }
    public void resetBackup() { confirmed = false; }
    public boolean backupConfirmed() { return confirmed; }
    public void confirmBackup() { confirmed = true; }
  }
  private static final class FakeHandle implements WalletSession.Handle {
    boolean encrypted, locked;
    boolean failSave, checkedFailure;
    int phraseReads;
    int balanceReads, unlocks, addressReads;
    RuntimeException balanceFailure;
    WalletBalance reading = new WalletBalance(0, 0, 0, false, false, -1, -1);
    String password;
    public String recoveryPhrase() { phraseReads++; return "FAKE TEST PHRASE"; }
    public void encrypt(String passphrase) { password = passphrase; encrypted = true; locked = false; }
    public void save() { if (failSave) throw new IllegalStateException("disk failure"); }
    public Boolean unlock(String passphrase) {
      unlocks++;
      if (!encrypted) return null;
      boolean ok = password.equals(passphrase); locked = !ok; return ok;
    }
    public void lock() { locked = true; }
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
      catch (IllegalStateException expected) { check(storage.creates == 0 && storage.opens == 1); }
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
    System.out.println("Passed: " + passed + "; Android/Nexa execution is not covered by these tests.");
  }
}
