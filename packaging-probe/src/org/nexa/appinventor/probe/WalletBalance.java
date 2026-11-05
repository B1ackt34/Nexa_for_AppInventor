package org.nexa.appinventor.probe;

import java.math.BigDecimal;

/** Immutable readings, not a guarantee of an atomic snapshot or spendable funds. */
final class WalletBalance {
  final String total;
  final String confirmed;
  final String unconfirmed;
  final boolean synced;
  final boolean connected;
  final String syncedHeight;
  final String chainHeight;

  WalletBalance(long totalSat, long confirmedSat, long unconfirmedSat,
      boolean synced, boolean connected, long syncedHeight, long chainHeight) {
    // nexa-transaction-construction/SKILL.md, Mental model: 1 NEXA = 100 sat.
    // Use the SDK totals as supplied; do not infer a sum or reject signed values.
    this.total = BigDecimal.valueOf(totalSat, 2).toPlainString();
    this.confirmed = BigDecimal.valueOf(confirmedSat, 2).toPlainString();
    this.unconfirmed = BigDecimal.valueOf(unconfirmedSat, 2).toPlainString();
    this.synced = synced;
    this.connected = connected;
    this.syncedHeight = Long.toString(syncedHeight);
    this.chainHeight = Long.toString(chainHeight);
  }
}
