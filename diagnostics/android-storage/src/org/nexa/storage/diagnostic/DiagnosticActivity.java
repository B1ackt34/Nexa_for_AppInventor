package org.nexa.storage.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Process;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** No user credentials, imported keys, receive addresses or sending controls. */
public final class DiagnosticActivity extends Activity {
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
  private static final AtomicBoolean BUSY = new AtomicBoolean();
  private static DiagnosticEngine engine;
  private static volatile java.lang.ref.WeakReference<DiagnosticActivity> foreground = new java.lang.ref.WeakReference<>(null);
  private final List<Button> controls = new ArrayList<>();
  private TextView output;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    if (engine == null) engine = new DiagnosticEngine(this);
    ScrollView scroll = new ScrollView(this);
    LinearLayout column = new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    int padding = (int) (16 * getResources().getDisplayMetrics().density);
    column.setPadding(padding, padding, padding, padding);
    scroll.setFitsSystemWindows(true);
    scroll.addView(column);
    TextView heading = new TextView(this);
    heading.setText("Nexa Storage Diagnostic 0.1.0\nTESTNET — solo dati usa-e-getta\n\n"
        + "App separata dal tuo wallet. Non inserire password o frasi personali. "
        + "Non inviare fondi: questo test lascia intenzionalmente un wallet diagnostico non cifrato prima del passo 2.\n\n"
        + "Test normale: 1 → 2 → Chiudi processo → riapri l’app → 3. "
        + "Per ogni prova di interruzione, riparti con dati vuoti SOLO di questa app diagnostica.\n");
    column.addView(heading);
    operation(column, "1. Crea e controlla prima della cifratura", "CREATE", false);
    operation(column, "2. Cifra e controlla i residui", "ENCRYPT", false);
    operation(column, "3. Riapri dopo riavvio del processo", "REOPEN", false);
    operation(column, "Interrompi durante creazione (circa 25 ms)", "CRASH_DURING_CREATE", true);
    operation(column, "Interrompi subito dopo creazione", "CRASH_AFTER_CREATE", true);
    operation(column, "Interrompi durante cifratura (circa 25 ms)", "CRASH_DURING_ENCRYPT", true);
    Button exit = button(column, "Chiudi processo diagnostico");
    exit.setOnClickListener(v -> { if (!BUSY.get()) Process.killProcess(Process.myPid()); });
    Button copy = button(column, "Copia rapporto senza segreti");
    copy.setOnClickListener(v -> {
      if (!BUSY.get()) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Nexa diagnostic metadata", engine.lastReport()));
      }
    });
    output = new TextView(this);
    output.setText(engine.lastReport());
    output.setTextIsSelectable(true);
    column.addView(output);
    setContentView(scroll);
    enable(!BUSY.get());
  }

  private Button button(LinearLayout column, String label) {
    Button button = new Button(this);
    button.setText(label);
    column.addView(button);
    controls.add(button);
    return button;
  }
  private void operation(LinearLayout column, String label, String operation, boolean interrupts) {
    Button button = button(column, label);
    button.setOnClickListener(v -> {
      if (interrupts) new AlertDialog.Builder(this).setTitle("Interruzione diagnostica")
          .setMessage("L’app diagnostica si chiuderà intenzionalmente. Il wallet principale non viene toccato. "
              + "Il timer non garantisce l’istruzione SDK esatta dell’interruzione.")
          .setNegativeButton("Annulla", null).setPositiveButton("Esegui test", (d, w) -> run(operation)).show();
      else run(operation);
    });
  }
  private void run(String operation) {
    if (!BUSY.compareAndSet(false, true)) return;
    enable(false);
    output.setText("Operazione diagnostica in corso…");
    WORKER.execute(() -> {
      String report = engine.run(operation);
      BUSY.set(false);
      DiagnosticActivity current = foreground.get();
      if (current != null) current.runOnUiThread(() -> {
        if (!current.isDestroyed()) { current.output.setText(report); current.enable(true); }
      });
    });
  }
  private void enable(boolean value) { for (Button button : controls) button.setEnabled(value); }
  @Override protected void onResume() {
    super.onResume();
    foreground = new java.lang.ref.WeakReference<>(this);
    if (output != null && !BUSY.get()) { output.setText(engine.lastReport()); enable(true); }
  }
}
