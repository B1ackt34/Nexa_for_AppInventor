package org.nexa.appinventor.probe;

import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import org.nexa.libnexakotlin.LibNexa;
import org.nexa.libnexakotlin.LibnexaKt;

/** Runs the official SDK validator with public test phrases.
 * SHA-256 is supplied by the JDK through a test-only LibNexa proxy.
 * Does not exercise Android JNI, wallet persistence, derivation or networking.
 */
public final class RecoveryPhraseSdkTest {
  static int passed;
  static final String PUBLIC_VECTOR = "abandon ".repeat(11) + "about";
  static void check(boolean value) { if (!value) throw new AssertionError(); }
  static void test(String name, Runnable work) { work.run(); passed++; System.out.println("PASS " + name); }
  static void invalid(String phrase) {
    try { RecoveryPhrase.validate(phrase); throw new AssertionError("Invalid phrase accepted"); }
    catch (WalletSession.Failure error) {
      check(error.code.equals("INVALID_RECOVERY_PHRASE")); check(error.getCause() == null);
      check(!error.getMessage().contains("abandon") && !error.getMessage().contains("zzsecretword"));
    }
  }
  public static void main(String[] args) {
    LibNexa shaOnly = (LibNexa) Proxy.newProxyInstance(LibNexa.class.getClassLoader(), new Class<?>[] {LibNexa.class},
        (proxy, method, values) -> {
          if (method.getName().equals("sha256")) return MessageDigest.getInstance("SHA-256").digest((byte[]) values[0]);
          throw new AssertionError("Unexpected SDK primitive: " + method.getName());
        });
    LibnexaKt.setLibnexa(shaOnly); // Test harness only; production initializes Android JNI normally.
    test("official SDK accepts a public valid 12-word test vector", () -> check(RecoveryPhrase.validate(PUBLIC_VECTOR).equals(PUBLIC_VECTOR)));
    test("official SDK normalizes case and supported whitespace", () -> {
      String input = "  " + PUBLIC_VECTOR.toUpperCase(java.util.Locale.ROOT).replace(" ", " \n\t ") + "  ";
      check(RecoveryPhrase.validate(input).equals(PUBLIC_VECTOR));
    });
    test("official SDK rejects known words with incorrect checksum", () -> invalid("abandon ".repeat(12).trim()));
    test("unknown secret word is rejected without leaking SDK exception text", () -> invalid("abandon ".repeat(11) + "zzsecretword"));
    test("11 words rejected", () -> invalid("abandon ".repeat(11).trim()));
    test("24 words rejected by current SDK validator", () -> invalid("abandon ".repeat(24).trim()));
    test("empty null and oversized input rejected", () -> { invalid(""); invalid(null); invalid("x".repeat(2049)); });
    System.out.println("Passed: " + passed + "; official SDK validator, JDK SHA-256 test adapter; no Android JNI or real wallet.");
  }
}
