package org.nexa.storage.diagnostic;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexa.libnexakotlin.Bip44Wallet;
import org.nexa.libnexakotlin.ChainSelector;
import org.nexa.libnexakotlin.InitKt;
import org.nexa.libnexakotlin.LibnexaandroidKt;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Dedicated application sandbox only. Never accepts a user wallet name/password. */
final class DiagnosticEngine {
  static final String PACKAGE = "org.nexa.storage.diagnostic";
  static final String NAME = "nexa_storage_diagnostic_v1";
  static final String VERSION = "0.1.0";
  private final Context context;
  private final SharedPreferences prefs;
  private final ProbeProtocol protocol;
  private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
  private boolean initialized;

  DiagnosticEngine(Context source) {
    context = source.getApplicationContext();
    if (!PACKAGE.equals(context.getPackageName())) throw new IllegalStateException("Wrong diagnostic package");
    prefs = context.getSharedPreferences("diagnostic_metadata", Context.MODE_PRIVATE);
    protocol = new ProbeProtocol(new ProbeProtocol.Backend() {
      public boolean exists() { return database().exists(); }
      public ProbeProtocol.Handle create() {
        // Nexa AI Skills: nexa-wallet-lifecycle-and-chain/SKILL.md pattern 1.
        return new SdkHandle(InitKt.newWallet(NAME, ChainSelector.NEXATESTNET, null));
      }
      public ProbeProtocol.Handle open() {
        // Same skill pattern 7; recoveryFlow.md: open disconnected, no chain attach.
        return new SdkHandle(InitKt.openDisconnectedWallet(NAME));
      }
    });
  }

  String lastReport() { return prefs.getString("report", "Nessun rapporto. Inizia dal test normale."); }
  private File database() { return context.getDatabasePath(NAME + ".db"); }

  String run(String operation) {
    JSONObject report = new JSONObject();
    try {
      report.put("diagnosticVersion", VERSION);
      report.put("sdkVersion", "0.5.69");
      report.put("androidApi", Build.VERSION.SDK_INT);
      report.put("network", "NEXATESTNET");
      report.put("operation", operation);
      report.put("phaseBeforeOperation", prefs.getString("phase", "NO_TEST"));
      report.put("personalWalletAccess", false);
      report.put("seedAndPrivateKeyScan", "NOT_PERFORMED");
      report.put("negativeSearchProvesNoSecrets", false);
      report.put("snapshotsAreAtomic", false);
      JSONArray observations = new JSONArray(prefs.getString("observations", "[]"));
      report.put("observations", observations);
      if (!initialized) {
        // Wallet lifecycle skill, Setup and versions: initialize the native layer first.
        if (LibnexaandroidKt.initializeLibNexa("") == null) throw new IllegalStateException();
        initialized = true;
      }
      ProbeProtocol.Observer observer = new ProbeProtocol.Observer() {
        public void mark(String phase) {
          if (!prefs.edit().putString("phase", phase).commit()) throw new ProbeProtocol.Failure("METADATA_WRITE_FAILED");
          put(report, "phase", phase);
        }
        public void state(String key, String value) { put(report, key, value); }
        public void armInterruption() {
          // Timing is a request, not proof of a crash at a particular SDK instruction.
          timers.schedule(() -> Process.killProcess(Process.myPid()), 25, TimeUnit.MILLISECONDS);
        }
        public void stopNow() { Process.killProcess(Process.myPid()); }
        public void observe(String phase, String phrase) {
          if (phrase == null || phrase.length() < 12) throw new ProbeProtocol.Failure("REFERENCE_UNAVAILABLE");
          JSONObject observation = collect(phase, phrase);
          observations.put(observation);
          while (observations.length() > 12) observations.remove(0);
          String serialized = report.toString();
          // Defense against accidentally adding reference secrets to a future report field.
          if (serialized.contains(phrase) || serialized.contains(ProbeProtocol.TEST_PASSWORD)) {
            throw new ProbeProtocol.Failure("REPORT_REJECTED");
          }
          if (!prefs.edit().putString("observations", observations.toString()).commit()) {
            throw new ProbeProtocol.Failure("METADATA_WRITE_FAILED");
          }
        }
      };
      switch (operation) {
        case "CREATE": protocol.create(observer, false, false); break;
        case "ENCRYPT": protocol.encrypt(observer, false); break;
        case "REOPEN": protocol.reopen(observer); break;
        case "CRASH_DURING_CREATE": protocol.create(observer, true, false); break;
        case "CRASH_AFTER_CREATE": protocol.create(observer, false, true); break;
        case "CRASH_DURING_ENCRYPT": protocol.encrypt(observer, true); break;
        default: throw new ProbeProtocol.Failure("UNKNOWN_OPERATION");
      }
      report.put("operationCompleted", true);
    } catch (Exception | LinkageError failure) {
      // SDK messages/stack traces are deliberately excluded: they can contain inputs.
      put(report, "operationCompleted", false);
      put(report, "errorCode", failure instanceof ProbeProtocol.Failure
          ? ((ProbeProtocol.Failure) failure).code : failure.getClass().getSimpleName());
      put(report, "automaticDatabaseReplacementAttempted", false);
    }
    try {
      String result = report.toString(2);
      if (result.contains(ProbeProtocol.TEST_PASSWORD)) return "REPORT_REJECTED";
      if (!prefs.edit().putString("report", result).commit()) return "METADATA_WRITE_FAILED";
      return result;
    } catch (Exception failure) { return "REPORT_SERIALIZATION_FAILED"; }
  }

  private JSONObject collect(String phase, String phrase) {
    JSONObject result = new JSONObject();
    put(result, "phase", phase);
    byte[] words = phrase.getBytes(StandardCharsets.UTF_8);
    byte[] password = ProbeProtocol.TEST_PASSWORD.getBytes(StandardCharsets.UTF_8);
    try {
      JSONArray files = new JSONArray();
      boolean phraseInFile = false;
      for (String suffix : new String[] {"", "-journal", "-wal", "-shm"}) {
        JSONObject item = new JSONObject();
        put(item, "fileRole", suffix.isEmpty() ? "DATABASE" : suffix.substring(1).toUpperCase(java.util.Locale.ROOT));
        File file = new File(database().getPath() + suffix);
        try {
          if (!file.getCanonicalFile().getParentFile().equals(database().getCanonicalFile().getParentFile())) {
            throw new IllegalStateException();
          }
          if (!file.exists()) put(item, "status", "ABSENT_AT_CHECK");
          else {
            long length = file.length(), changed = file.lastModified();
            try (InputStream input = new FileInputStream(file); NeedleScanner scanner = new NeedleScanner(words, password)) {
              NeedleScanner.Result reading = scanner.scan(input, 64L * 1024 * 1024);
              boolean stable = length == file.length() && changed == file.lastModified() && length == reading.bytes;
              fill(item, reading);
              phraseInFile |= reading.found[0];
              put(item, "metadataStable", stable);
              put(item, "status", reading.complete && stable ? "SEARCH_COMPLETED" : "INCOMPLETE_OR_CHANGED");
            }
          }
        } catch (Exception failure) { put(item, "status", "READ_FAILED"); }
        files.put(item);
      }
      put(result, "files", files);
      put(result, "phraseUtf8ObservedInAtLeastOneFile", phraseInFile);
      put(result, "interpretation", phraseInFile ? "PLAINTEXT_REFERENCE_OBSERVED"
          : "NO_MATCH_DOES_NOT_PROVE_ERASURE");
      if ("AFTER_CREATE_BEFORE_ENCRYPT".equals(phase)) {
        put(result, "positiveControl", phraseInFile ? "DETECTED" : "NOT_DETECTED_TEST_INCONCLUSIVE");
      }
      put(result, "currentProcessLogcat", scanLogs(words, password));
      return result;
    } finally {
      Arrays.fill(words, (byte) 0);
      Arrays.fill(password, (byte) 0);
    }
  }

  private JSONObject scanLogs(byte[] phrase, byte[] password) {
    JSONObject result = new JSONObject();
    java.lang.Process capture = null;
    ScheduledFuture<?> deadline = null;
    try {
      capture = new ProcessBuilder("logcat", "-d", "--pid=" + Process.myPid(), "-v", "brief").start();
      final java.lang.Process current = capture;
      deadline = timers.schedule(current::destroy, 3, TimeUnit.SECONDS);
      try (InputStream stream = capture.getInputStream(); NeedleScanner scanner = new NeedleScanner(phrase, password)) {
        NeedleScanner.Result reading = scanner.scan(stream, 4L * 1024 * 1024);
        fill(result, reading);
        boolean ended = capture.waitFor(1, TimeUnit.SECONDS);
        put(result, "status", ended && capture.exitValue() == 0 && reading.complete ? "SEARCH_COMPLETED" : "PARTIAL_OR_UNAVAILABLE");
      }
    } catch (Exception failure) { put(result, "status", "UNAVAILABLE"); }
    finally {
      if (deadline != null) deadline.cancel(false);
      if (capture != null) capture.destroy();
    }
    put(result, "scope", "CURRENT_PROCESS_ONLY_AVAILABLE_RING_BUFFER");
    return result;
  }

  private static void fill(JSONObject report, NeedleScanner.Result result) {
    put(report, "phraseUtf8Found", result.found[0]);
    put(report, "knownTestPasswordUtf8Found", result.found[1]);
    put(report, "bytesSearched", result.bytes);
    put(report, "completeRead", result.complete);
  }
  private static void put(JSONObject object, String name, Object value) {
    try { object.put(name, value); } catch (Exception failure) { throw new IllegalStateException(); }
  }

  private static final class SdkHandle implements ProbeProtocol.Handle {
    private final Bip44Wallet wallet;
    SdkHandle(Bip44Wallet wallet) {
      this.wallet = wallet;
    }
    // SKILL.md pattern 6; recoveryFlow.md Create flow and Lock/unlock.
    public Boolean lockedState() { return wallet.lockedState(); }
    public Boolean unlock(String password) { return wallet.unlock(password); }
    public String phrase() { return wallet.getRecoverySecret(); }
    public void encrypt(String password) { wallet.encrypt(password); }
    public void save() { wallet.saveBip44Wallet(); }
    public void lock() { wallet.lock(); }
  }
}
