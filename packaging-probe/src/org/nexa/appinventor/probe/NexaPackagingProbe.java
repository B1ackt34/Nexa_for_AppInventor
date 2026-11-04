package org.nexa.appinventor.probe;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.Extension;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import org.nexa.libnexakotlin.LibnexaandroidKt;

/** Packaging experiment only: no wallet, keys, chain connection or transactions. */
@Extension(description = "Nexa 0.5.69 native initialization packaging probe.")
@DesignerComponent(version = 1, description = "Nexa Android packaging probe",
    category = ComponentCategory.EXTENSION, nonVisible = true, androidMinSdk = 29)
@SimpleObject(external = true)
public final class NexaPackagingProbe extends AndroidNonvisibleComponent {
  private static final Object INITIALIZATION_LOCK = new Object();
  private static volatile boolean initialized;
  private boolean initializing;

  public NexaPackagingProbe(ComponentContainer container) {
    super(container.$form());
  }

  @SimpleProperty(description = "True after native initialization succeeds in this process.")
  public boolean Initialized() {
    return initialized;
  }

  @SimpleFunction(description = "Initialize Nexa off the UI thread. Reports completion by event.")
  public void Initialize() {
    if (initializing) {
      InitializationFailed("BUSY", "Initialization is already running.");
      return;
    }
    initializing = true;
    new Thread(() -> {
      try {
        synchronized (INITIALIZATION_LOCK) {
          if (!initialized) {
            // Nexa source: nexa-wallet-lifecycle-and-chain/SKILL.md, Setup and versions.
            // Corpus commit: 3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24.
            // Java signature verified with javap on the official Android 0.5.69 AAR.
            // Empty string selects the documented default native variant.
            if (LibnexaandroidKt.initializeLibNexa("") == null) {
              throw new IllegalStateException("Nexa initialization returned no native library.");
            }
            initialized = true;
          }
        }
        form.runOnUiThread(() -> {
          initializing = false;
          InitializationSucceeded();
        });
      } catch (RuntimeException | LinkageError failure) {
        final String code = failure.getClass().getSimpleName();
        final String message = failure.getMessage() == null ? code : failure.getMessage();
        form.runOnUiThread(() -> {
          initializing = false;
          InitializationFailed(code, message);
        });
      }
    }, "NexaPackagingProbe").start();
  }

  @SimpleEvent(description = "Nexa native initialization completed; does not imply wallet readiness.")
  public void InitializationSucceeded() {
    EventDispatcher.dispatchEvent(this, "InitializationSucceeded");
  }

  @SimpleEvent(description = "Native initialization failed, or another call is in progress.")
  public void InitializationFailed(String code, String message) {
    EventDispatcher.dispatchEvent(this, "InitializationFailed", code, message);
  }
}
