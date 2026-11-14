package org.nexa.appinventor.probe;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Real temporary-file tests of the app protocol; no Android, SDK wallet or funds. */
public final class NoBackupStoreTest {
  interface Work { void run() throws Exception; }
  static int passed;
  static final String NAME = "nexa_ai2_testnet_v1";
  static void check(boolean value) { if (!value) throw new AssertionError(); }
  static void test(String name, Work work) throws Exception { work.run(); passed++; System.out.println("PASS " + name); }
  static void expect(String code, Runnable work) {
    try { work.run(); throw new AssertionError("Missing " + code); }
    catch (WalletSession.Failure error) { check(error.code.equals(code)); check(error.getCause() == null); }
  }
  static final class Sync implements NoBackupStore.DirectorySync {
    boolean failNext;
    int calls;
    public void sync(File directory) throws Exception {
      calls++;
      if (failNext) { failNext = false; throw new java.io.IOException("synthetic failure"); }
      try (FileChannel channel = FileChannel.open(directory.toPath(), StandardOpenOption.READ)) { channel.force(true); }
    }
  }
  static final class Fixture {
    final File base;
    final Sync sync = new Sync();
    final NoBackupStore store;
    Fixture() throws Exception { base = Files.createTempDirectory("nexa-no-backup-test-").toFile(); store = new NoBackupStore(base, NAME, sync); }
    void database() throws Exception { store.initialize(); Files.writeString(Path.of(store.pathForCreate()), "SYNTHETIC DATABASE ONLY"); }
    NoBackupStore reopened() { return new NoBackupStore(base, NAME, sync); }
  }
  static SendData.Record record(String state) {
    return new SendData.Record("synthetic-request", "SYNTHETIC DESTINATION", Long.MAX_VALUE - 10, 10,
        "01".repeat(32), "ab".repeat(128), state);
  }
  static final class StoreBridge implements WalletSession.Storage {
    final Fixture f;
    final SendSessionTest.Backend backend = new SendSessionTest.Backend();
    int paymentWrites, failPaymentWrite;
    StoreBridge(Fixture f) { this.f = f; }
    public boolean exists() { f.store.checkLayout(); return f.store.database.exists(); }
    public WalletSession.Handle create() {
      try { Files.writeString(Path.of(f.store.pathForCreate()), "SYNTHETIC DATABASE ONLY"); }
      catch (Exception error) { throw new IllegalStateException(error); }
      return backend;
    }
    public WalletSession.Handle open() { f.store.pathForOpen(); return backend; }
    public void resetBackup() { f.store.initialize(); }
    public boolean backupConfirmed() { return f.store.backupConfirmed(); }
    public void confirmBackup() { f.store.confirmBackup(); }
    public SendData.Record approvedSend() { return f.store.approvedSend(); }
    public void saveApprovedSend(SendData.Record r) {
      paymentWrites++; if (paymentWrites == failPaymentWrite) f.sync.failNext = true;
      f.store.saveApprovedSend(r);
    }
  }
  public static void main(String[] args) throws Exception {
    test("empty inspection creates no wallet storage directory", () -> {
      Fixture f = new Fixture(); f.store.checkLayout(); check(!f.store.root.exists()); check(f.store.contained(f.store.database));
    });
    test("metadata is initialized durably before SDK database creation", () -> {
      Fixture f = new Fixture(); f.store.initialize(); check(f.sync.calls == 2 && f.store.metadata.exists());
      check(f.store.pathForCreate().equals(f.store.database.getAbsolutePath()));
      expect("STORAGE_INCOMPLETE", f.store::checkLayout);
    });
    test("new database reopens with same path and backup not confirmed", () -> {
      Fixture f = new Fixture(); f.database(); NoBackupStore next = f.reopened(); next.checkLayout();
      check(next.pathForOpen().equals(f.store.database.getAbsolutePath()) && !next.backupConfirmed());
    });
    test("backup confirmation persists across fresh store instance", () -> {
      Fixture f = new Fixture(); f.database(); f.store.confirmBackup(); check(f.reopened().backupConfirmed());
    });
    test("approved record preserves exact large integer amounts and bytes", () -> {
      Fixture f = new Fixture(); f.database(); f.store.confirmBackup(); SendData.Record r = record("APPROVED");
      f.store.saveApprovedSend(r); SendData.Record saved = f.reopened().approvedSend();
      check(saved.amount == r.amount && saved.fee == r.fee && saved.hex.equals(r.hex) && saved.checksum().equals(r.checksum()));
      check(f.reopened().backupConfirmed());
    });
    test("submission state updates preserve authorization and backup", () -> {
      Fixture f = new Fixture(); f.database(); f.store.confirmBackup(); f.store.saveApprovedSend(record("APPROVED"));
      f.store.saveApprovedSend(record("SDK_SUBMITTED"));
      check(f.reopened().approvedSend().state.equals("SDK_SUBMITTED") && f.reopened().backupConfirmed());
    });
    test("second initialization cannot reset an existing wallet", () -> {
      Fixture f = new Fixture(); f.database(); f.store.confirmBackup();
      byte[] before = Files.readAllBytes(f.store.metadata.toPath()); expect("WALLET_EXISTS", f.store::initialize);
      check(java.util.Arrays.equals(before, Files.readAllBytes(f.store.metadata.toPath())));
    });
    test("database without metadata is rejected without writing replacement", () -> {
      Fixture f = new Fixture(); Files.createDirectory(f.store.root.toPath()); Files.writeString(f.store.database.toPath(), "KEEP");
      expect("STORAGE_INCOMPLETE", f.store::checkLayout); check(!f.store.metadata.exists());
    });
    test("initialized metadata without database cannot silently create on restart", () -> {
      Fixture f = new Fixture(); f.store.initialize(); expect("STORAGE_INCOMPLETE", f.reopened()::checkLayout);
      expect("STORAGE_INCOMPLETE", f.reopened()::initialize);
    });
    for (String suffix : new String[] {"-wal", "-shm", "-journal"}) {
      test("orphan database sidecar rejected " + suffix, () -> {
        Fixture f = new Fixture(); Files.createDirectory(f.store.root.toPath()); Files.writeString(Path.of(f.store.database + suffix), "KEEP");
        expect("STORAGE_INCOMPLETE", f.store::checkLayout); check(!f.store.metadata.exists());
      });
    }
    test("temporary metadata after interruption blocks reopen without deleting it", () -> {
      Fixture f = new Fixture(); f.database(); Files.writeString(f.store.temporary.toPath(), "PARTIAL");
      expect("STORAGE_INCOMPLETE", f.reopened()::checkLayout); check(Files.readString(f.store.temporary.toPath()).equals("PARTIAL"));
    });
    test("corrupt metadata is not reset", () -> {
      Fixture f = new Fixture(); f.database(); Files.writeString(f.store.metadata.toPath(), "BROKEN");
      expect("STORAGE_METADATA_INVALID", f.reopened()::checkLayout); check(Files.readString(f.store.metadata.toPath()).equals("BROKEN"));
    });
    test("wrong JSON types rejected", () -> {
      Fixture f = new Fixture(); f.database(); Files.writeString(f.store.metadata.toPath(), "{\"version\":1,\"backup_confirmed\":\"true\"}");
      expect("STORAGE_METADATA_INVALID", f.reopened()::checkLayout);
    });
    test("fractional metadata version rejected", () -> {
      Fixture f = new Fixture(); f.database(); Files.writeString(f.store.metadata.toPath(), "{\"version\":1.5,\"backup_confirmed\":false}");
      expect("STORAGE_METADATA_INVALID", f.reopened()::checkLayout);
    });
    test("record checksum mismatch blocks reads instead of replacing payment", () -> {
      Fixture f = new Fixture(); f.database(); f.store.saveApprovedSend(record("APPROVED"));
      String raw = Files.readString(f.store.metadata.toPath()); Files.writeString(f.store.metadata.toPath(), raw.replace("\"fee\":10", "\"fee\":11"));
      expect("STORAGE_METADATA_INVALID", f.reopened()::checkLayout);
    });
    test("oversized metadata rejected", () -> {
      Fixture f = new Fixture(); f.database(); Files.writeString(f.store.metadata.toPath(), "x".repeat(3 * 1024 * 1024 + 1));
      expect("STORAGE_METADATA_INVALID", f.reopened()::checkLayout);
    });
    test("directory sync failure after rename is not reported as committed", () -> {
      Fixture f = new Fixture(); f.database(); f.sync.failNext = true;
      expect("STORAGE_WRITE_UNCONFIRMED", () -> f.store.saveApprovedSend(record("APPROVED")));
      expect("STORAGE_WRITE_UNCONFIRMED", f.store::checkLayout);
      check(f.reopened().approvedSend() != null); // bytes observed now, not proof of power-loss durability
    });
    test("failed root directory sync cannot authorize database creation", () -> {
      Fixture f = new Fixture(); f.sync.failNext = true; expect("STORAGE_WRITE_UNCONFIRMED", f.store::initialize);
      expect("STORAGE_INCOMPLETE", f.store::pathForCreate); check(!f.store.database.exists());
    });
    for (String role : new String[] {"database", "metadata", "temporary", "root"}) {
      test("symbolic link rejected for " + role, () -> {
        Fixture f = new Fixture(); File target = Files.createTempDirectory("outside-synthetic-").toFile();
        if (role.equals("root")) Files.createSymbolicLink(f.store.root.toPath(), target.toPath());
        else {
          Files.createDirectory(f.store.root.toPath()); File link = role.equals("database") ? f.store.database : role.equals("metadata") ? f.store.metadata : f.store.temporary;
          Files.createSymbolicLink(link.toPath(), new File(target, "outside").toPath());
        }
        expect("STORAGE_PATH_INVALID", f.store::checkLayout); check(target.list().length == 0);
      });
    }
    test("path traversal in wallet name rejected", () -> {
      Fixture f = new Fixture(); expect("STORAGE_PATH_INVALID", () -> new NoBackupStore(f.base, "../elsewhere", f.sync));
    });
    for (String name : new String[] {NAME + ".db", NAME + ".db-wal", NAME + ".db-shm", NAME + ".db-journal", NAME + ".xml", NAME + ".xml.bak", NAME + "_send.xml", NAME + "_send.xml.bak"}) {
      test("legacy artifact detected and preserved " + name, () -> {
        Fixture f = new Fixture(); File prefs = Files.createDirectory(new File(f.base, "shared_prefs").toPath()).toFile();
        File file = new File(name.contains(".db") ? f.base : prefs, name); Files.writeString(file.toPath(), "KEEP");
        check(NoBackupStore.hasLegacyArtifacts(new File(f.base, NAME + ".db"), prefs, NAME));
        check(Files.readString(file.toPath()).equals("KEEP") && !f.store.root.exists());
      });
    }
    test("unrelated preference file does not claim an existing Nexa wallet", () -> {
      Fixture f = new Fixture(); File prefs = Files.createDirectory(new File(f.base, "shared_prefs").toPath()).toFile();
      Files.writeString(new File(prefs, "other.xml").toPath(), "UNRELATED");
      check(!NoBackupStore.hasLegacyArtifacts(new File(f.base, NAME + ".db"), prefs, NAME));
    });
    test("journal sync failure prevents SDK handoff and aborts only the draft", () -> {
      Fixture f = new Fixture(); StoreBridge storage = new StoreBridge(f); WalletSession session = new WalletSession(storage);
      session.create(SendSessionTest.PASSWORD); session.confirmBackup();
      SendData.Preview p = session.preparePayment("SYNTHETIC DEST", "100", "1", SendSessionTest.PASSWORD, () -> true);
      storage.failPaymentWrite = 1;
      expect("STORAGE_WRITE_UNCONFIRMED", () -> session.confirmPayment(p.id, () -> true));
      check(storage.backend.submits == 0 && storage.backend.aborts == 1);
    });
    test("post-handoff write failure retains the payment and never aborts it", () -> {
      Fixture f = new Fixture(); StoreBridge storage = new StoreBridge(f); WalletSession session = new WalletSession(storage);
      session.create(SendSessionTest.PASSWORD); session.confirmBackup();
      SendData.Preview p = session.preparePayment("SYNTHETIC DEST", "100", "1", SendSessionTest.PASSWORD, () -> true);
      storage.failPaymentWrite = 2;
      expect("SEND_OUTCOME_UNKNOWN", () -> session.confirmPayment(p.id, () -> true));
      check(storage.backend.submits == 1 && storage.backend.aborts == 0 && f.reopened().approvedSend().idem.equals(p.draft.idem));
    });
    System.out.println("Passed: " + passed + "; real temporary files and JDK directory sync; no Android SDK wallet or backup execution.");
  }
}
