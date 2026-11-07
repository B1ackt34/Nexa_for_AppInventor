package org.nexa.appinventor.probe;

/** Invalidates queued work and UI deliveries across pause/resume/manual lock. */
final class ForegroundGate {
  private volatile boolean active = true;
  private volatile long generation;
  synchronized long capture() { return active ? generation : -1; }
  synchronized boolean pause() {
    if (!active) return false;
    active = false;
    generation++;
    return true;
  }
  synchronized void resume() { active = true; generation++; }
  synchronized void invalidate() { generation++; }
  boolean current(long token) { return active && token >= 0 && generation == token; }
  boolean active() { return active; }
}
