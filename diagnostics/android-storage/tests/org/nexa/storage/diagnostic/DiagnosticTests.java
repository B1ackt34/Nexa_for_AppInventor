package org.nexa.storage.diagnostic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DiagnosticTests {
  static int passed;
  static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
  static void test(String label, Checked work) throws Exception { work.run(); passed++; System.out.println("PASS " + label); }
  interface Checked { void run() throws Exception; }
  static void expect(String code, Runnable work) {
    try { work.run(); throw new AssertionError("Missing failure"); }
    catch (ProbeProtocol.Failure failure) { check(code.equals(failure.code)); }
  }
  static final class Wallet implements ProbeProtocol.Handle {
    boolean encrypted, locked, failSave, ignoreLock;
    int encrypts, reads;
    public Boolean lockedState() { return encrypted ? locked : null; }
    public Boolean unlock(String password) { if (!encrypted) return null; locked = !password.equals(ProbeProtocol.TEST_PASSWORD); return !locked; }
    public String phrase() { if (encrypted && locked) throw new IllegalStateException(); reads++; return "SYNTHETIC TEST REFERENCE"; }
    public void encrypt(String p) { encrypts++; encrypted = true; locked = false; }
    public void save() { if (failSave) throw new IllegalStateException(); }
    public void lock() { if (!ignoreLock) locked = true; }
  }
  static final class Backend implements ProbeProtocol.Backend {
    boolean exists, corrupt;
    int creates, opens;
    Wallet wallet = new Wallet();
    public boolean exists() { return exists; }
    public ProbeProtocol.Handle create() { creates++; exists = true; return wallet; }
    public ProbeProtocol.Handle open() { opens++; if (corrupt) throw new IllegalStateException(); return wallet; }
  }
  static class Observe implements ProbeProtocol.Observer {
    List<String> events = new ArrayList<>();
    public void mark(String p) { events.add(p); }
    public void observe(String p, String words) { check(words.equals("SYNTHETIC TEST REFERENCE")); events.add(p); }
    public void state(String k, String v) { events.add(k + "=" + v); }
    public void armInterruption() { events.add("TIMER_ARMED"); }
    public void stopNow() { events.add("PROCESS_STOP_REQUEST"); }
  }
  public static void main(String[] args) throws Exception {
    test("binary match across buffer boundary", () -> {
      byte[] input = new byte[16384]; byte[] needle = bytes("TEST-REFERENCE");
      System.arraycopy(needle, 0, input, 8190, needle.length);
      try (NeedleScanner scanner = new NeedleScanner(needle)) {
        NeedleScanner.Result r = scanner.scan(new ByteArrayInputStream(input), input.length);
        check(r.found[0] && r.complete && r.bytes == input.length);
      }
    });
    test("overlapping prefixes are searched correctly", () -> {
      try (NeedleScanner scanner = new NeedleScanner(bytes("ababac"), bytes("never"))) {
        NeedleScanner.Result r = scanner.scan(new ByteArrayInputStream(bytes("ababababac")), 99);
        check(r.found[0] && !r.found[1] && r.complete);
      }
    });
    test("limit never becomes a complete negative search", () -> {
      try (NeedleScanner scanner = new NeedleScanner(bytes("tail"))) {
        NeedleScanner.Result r = scanner.scan(new ByteArrayInputStream(bytes("headtail")), 4);
        check(!r.found[0] && !r.complete && r.bytes == 4);
      }
    });
    test("I/O failure is not a negative result", () -> {
      try (NeedleScanner scanner = new NeedleScanner(bytes("anything"))) {
        try { scanner.scan(new InputStream() { public int read() throws IOException { throw new IOException(); } }, 100); throw new AssertionError(); }
        catch (IOException expected) { }
      }
    });
    test("scanner reuse resets partial match state", () -> {
      try (NeedleScanner scanner = new NeedleScanner(bytes("abc"))) {
        check(!scanner.scan(new ByteArrayInputStream(bytes("ab")), 9).found[0]);
        check(!scanner.scan(new ByteArrayInputStream(bytes("c")), 9).found[0]);
      }
    });
    test("needle inputs are copied and not overwritten", () -> {
      byte[] input = bytes("reference");
      try (NeedleScanner scanner = new NeedleScanner(input)) { check(scanner.scan(new ByteArrayInputStream(input), 100).found[0]); }
      check(Arrays.equals(input, bytes("reference")));
    });
    test("create observes deliberately unencrypted data without implicit encrypt", () -> {
      Backend b = new Backend(); Observe o = new Observe(); new ProbeProtocol(b).create(o, false, false);
      check(b.creates == 1 && b.wallet.encrypts == 0 && o.events.contains("AFTER_CREATE_BEFORE_ENCRYPT"));
    });
    test("existing database is refused before creation", () -> {
      Backend b = new Backend(); b.exists = true;
      expect("DIAGNOSTIC_DATABASE_EXISTS", () -> new ProbeProtocol(b).create(new Observe(), false, false));
      check(b.creates == 0);
    });
    test("encryption locks before post-encryption observation", () -> {
      Backend b = new Backend(); b.exists = true;
      Observe o = new Observe() { public void observe(String p, String words) { check(b.wallet.locked); super.observe(p, words); } };
      new ProbeProtocol(b).encrypt(o, false);
      check(b.wallet.encrypted && b.wallet.locked && o.events.contains("AFTER_ENCRYPT_AND_LOCK"));
    });
    test("save failure still attempts lock", () -> {
      Backend b = new Backend(); b.exists = true; b.wallet.failSave = true;
      try { new ProbeProtocol(b).encrypt(new Observe(), false); throw new AssertionError(); }
      catch (IllegalStateException expected) { check(b.wallet.locked && b.creates == 0); }
    });
    test("lock postcondition failure cannot report completed encryption", () -> {
      Backend b = new Backend(); b.exists = true; b.wallet.ignoreLock = true; Observe o = new Observe();
      expect("LOCK_NOT_CONFIRMED", () -> new ProbeProtocol(b).encrypt(o, false));
      check(!o.events.contains("AFTER_ENCRYPT_AND_LOCK"));
    });
    test("reopen never falls back to creation on corruption", () -> {
      Backend b = new Backend(); b.exists = true; b.corrupt = true;
      try { new ProbeProtocol(b).reopen(new Observe()); throw new AssertionError(); }
      catch (IllegalStateException expected) { check(b.creates == 0 && b.opens == 1); }
    });
    test("missing reopen does not create an empty database", () -> {
      Backend b = new Backend(); expect("DIAGNOSTIC_DATABASE_ABSENT", () -> new ProbeProtocol(b).reopen(new Observe()));
      check(b.creates == 0 && b.opens == 0);
    });
    test("encrypted reopen unlocks then relocks without rekey", () -> {
      Backend b = new Backend(); b.exists = true; b.wallet.encrypted = true; b.wallet.locked = true;
      new ProbeProtocol(b).reopen(new Observe());
      check(b.wallet.locked && b.wallet.reads == 1 && b.wallet.encrypts == 0);
    });
    test("same-process reopen is refused instead of duplicating DB handles", () -> {
      Backend b = new Backend(); ProbeProtocol p = new ProbeProtocol(b); p.create(new Observe(), false, false);
      expect("RESTART_DIAGNOSTIC_PROCESS_FIRST", () -> p.reopen(new Observe())); check(b.opens == 0);
    });
    test("already encrypted test database is not rekeyed", () -> {
      Backend b = new Backend(); b.exists = true; b.wallet.encrypted = true;
      expect("ALREADY_ENCRYPTED_USE_REOPEN", () -> new ProbeProtocol(b).encrypt(new Observe(), false)); check(b.wallet.encrypts == 0);
    });
    test("interruption is armed after durable stage marker", () -> {
      Backend b = new Backend(); Observe o = new Observe(); new ProbeProtocol(b).create(o, true, false);
      check(o.events.indexOf("CREATING") < o.events.indexOf("TIMER_ARMED"));
      check(o.events.indexOf("TIMER_ARMED") < o.events.indexOf("CREATED"));
    });
    test("stop after create does not encrypt or expose reference", () -> {
      Backend b = new Backend(); Observe o = new Observe(); new ProbeProtocol(b).create(o, false, true);
      check(b.wallet.reads == 0 && b.wallet.encrypts == 0 && o.events.contains("PROCESS_STOP_REQUEST"));
    });
    System.out.println("Passed " + passed + "; Android and real SDK runtime not covered.");
  }
}
