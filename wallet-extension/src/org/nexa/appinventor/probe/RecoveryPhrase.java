package org.nexa.appinventor.probe;

import org.nexa.libnexakotlin.Bip39Kt;

/** Official SDK validator, explicitly authorized by the user on 2026-11-12.
 * Source: libnexakotlin release-0.7.5, commonMain/kotlin/bip39.kt.
 * This is the sole exception to the Nexa AI Skills API constraint.
 */
final class RecoveryPhrase {
  static String validate(String phrase) {
    if (phrase == null || phrase.length() > 2048) throw invalid();
    try {
      return Bip39Kt.bip39ProcessRecoverySecret(phrase, true);
    } catch (IllegalArgumentException error) {
      // SDK messages may contain a secret word. Never propagate text or cause.
      throw invalid();
    }
  }
  private static WalletSession.Failure invalid() {
    return new WalletSession.Failure("INVALID_RECOVERY_PHRASE",
        "Use a valid 12-word English recovery phrase. Words or checksum are invalid.");
  }
}
