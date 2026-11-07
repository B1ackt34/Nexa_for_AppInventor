package org.nexa.appinventor.probe;

import java.util.function.Consumer;

/** Does not keep a queued password after cancellation. Strings cannot be zeroized. */
final class GuardedRequest implements Runnable {
  final long token;
  private final ForegroundGate gate;
  private final Consumer<GuardedRequest> work;
  private final Consumer<GuardedRequest> done;
  private volatile String secret;
  private volatile boolean cancelled;

  GuardedRequest(ForegroundGate gate, String secret, Consumer<GuardedRequest> work,
      Consumer<GuardedRequest> done) {
    this.gate = gate;
    this.token = gate.capture();
    this.secret = secret;
    this.work = work;
    this.done = done;
  }
  boolean valid() { return !cancelled && gate.current(token); }
  String secret() {
    if (!valid()) throw new WalletSession.Failure("SESSION_LOCKED", "Return to the app and reopen the wallet.");
    return secret;
  }
  boolean retainsSecret() { return secret != null; }
  void cancel() { cancelled = true; secret = null; }
  public void run() {
    try { if (valid()) work.accept(this); }
    finally { secret = null; done.accept(this); }
  }
}
