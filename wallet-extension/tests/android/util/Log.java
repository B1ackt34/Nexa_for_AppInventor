package android.util;

/** Test-only logging sink for loading the Android SDK on a desktop JVM.
 * Never compiled into the extension; no Android behavior is emulated.
 */
public final class Log {
  public static int e(String tag, String message) { return 0; }
  public static int w(String tag, String message) { return 0; }
  public static int i(String tag, String message) { return 0; }
}
