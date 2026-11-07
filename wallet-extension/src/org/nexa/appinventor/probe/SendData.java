package org.nexa.appinventor.probe;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Application-owned immutable payment data; no secret keys or passwords. */
final class SendData {
  static long amount(String value) {
    if (value == null || value.length() > 64 || !value.trim().matches("[0-9]+(?:\\.[0-9]{1,2})?"))
      throw failure("INVALID_AMOUNT", "Use positive NEXA text with a decimal point and at most two decimals.");
    try {
      long sats = new BigDecimal(value.trim()).movePointRight(2).longValueExact();
      if (sats <= 0) throw new ArithmeticException();
      return sats;
    } catch (ArithmeticException error) {
      throw failure("INVALID_AMOUNT", "Amount must be positive and fit in exact satoshi units.");
    }
  }
  static String text(long sats) { return BigDecimal.valueOf(sats, 2).toPlainString(); }
  static WalletSession.Failure failure(String code, String message) { return new WalletSession.Failure(code, message); }

  static final class Draft {
    final String destination, idem, hex;
    final long amount, fee;
    final Object backend;
    Draft(String destination, long amount, long fee, String idem, String hex, Object backend) {
      this.destination = destination; this.amount = amount; this.fee = fee;
      this.idem = idem; this.hex = hex; this.backend = backend;
      validate(destination, amount, fee, idem, hex);
    }
  }
  static final class Preview {
    final String id = UUID.randomUUID().toString();
    final Draft draft;
    final Record retry;
    final long created;
    Preview(Draft draft, Record retry, long created) { this.draft = draft; this.retry = retry; this.created = created; }
  }
  static final class Record {
    final String requestId, destination, idem, hex, state;
    final long amount, fee;
    Record(String requestId, String destination, long amount, long fee, String idem, String hex, String state) {
      validate(destination, amount, fee, idem, hex);
      if (requestId == null || requestId.length() > 80 || requestId.isEmpty()
          || !("APPROVED".equals(state) || "SDK_SUBMITTED".equals(state) || "UNKNOWN".equals(state)))
        throw failure("SEND_RECORD_INVALID", "Payment record is invalid; no automatic replacement or retry.");
      this.requestId = requestId; this.destination = destination; this.amount = amount;
      this.fee = fee; this.idem = idem; this.hex = hex; this.state = state;
    }
    Record(Preview p) { this(p.id, p.draft.destination, p.draft.amount, p.draft.fee, p.draft.idem, p.draft.hex, "APPROVED"); }
    Record state(String next) { return new Record(requestId, destination, amount, fee, idem, hex, next); }
    String checksum() {
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        for (String value : new String[] {"1", requestId, destination, Long.toString(amount), Long.toString(fee), idem, hex, state}) {
          byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
          data.writeInt(encoded.length); data.write(encoded);
        }
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
      } catch (Exception failure) { throw new IllegalStateException("Payment metadata checksum failed"); }
    }
  }
  static final class Status {
    final String idem, state, height;
    Status(String idem, String state, String height) { this.idem = idem; this.state = state; this.height = height; }
  }
  private static void validate(String destination, long amount, long fee, String idem, String raw) {
    try {
      if (destination == null || destination.isEmpty() || destination.length() > 4096 || amount <= 0 || fee <= 0
          || idem == null || !idem.matches("[0-9a-fA-F]{64}") || raw == null || raw.length() < 200
          || raw.length() > 2 * 1024 * 1024 || (raw.length() & 1) != 0 || !raw.matches("[0-9a-fA-F]+")) throw new ArithmeticException();
      Math.addExact(amount, fee);
    } catch (ArithmeticException failure) {
      throw failure("SEND_RECORD_INVALID", "Invalid payment data; no transaction was rebuilt.");
    }
  }
  static String hex(byte[] value) {
    char[] chars = "0123456789abcdef".toCharArray(); char[] out = new char[value.length * 2];
    for (int i = 0; i < value.length; i++) { out[i * 2] = chars[(value[i] >>> 4) & 15]; out[i * 2 + 1] = chars[value[i] & 15]; }
    return new String(out);
  }
  static byte[] bytes(String value) {
    if (value == null || value.length() > 2 * 1024 * 1024 || (value.length() & 1) != 0)
      throw failure("SEND_RECORD_INVALID", "Invalid signed transaction encoding.");
    byte[] out = new byte[value.length() / 2];
    for (int i = 0; i < out.length; i++) {
      int a = Character.digit(value.charAt(i * 2), 16), b = Character.digit(value.charAt(i * 2 + 1), 16);
      if (a < 0 || b < 0) throw failure("SEND_RECORD_INVALID", "Invalid signed transaction encoding.");
      out[i] = (byte) ((a << 4) | b);
    }
    return out;
  }
}
