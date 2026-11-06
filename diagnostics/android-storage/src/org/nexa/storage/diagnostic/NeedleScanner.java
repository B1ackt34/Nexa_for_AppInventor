package org.nexa.storage.diagnostic;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Bounded binary search. Never returns source bytes or a representation of the needles. */
public final class NeedleScanner implements AutoCloseable {
  private final byte[][] needles;
  private final int[][] prefix;
  private final int[] position;

  public static final class Result {
    public final boolean[] found;
    public final long bytes;
    public final boolean complete;
    Result(boolean[] found, long bytes, boolean complete) {
      this.found = found; this.bytes = bytes; this.complete = complete;
    }
  }

  public NeedleScanner(byte[]... values) {
    needles = new byte[values.length][];
    prefix = new int[values.length][];
    position = new int[values.length];
    for (int n = 0; n < values.length; n++) {
      if (values[n] == null || values[n].length == 0) throw new IllegalArgumentException("Empty needle");
      needles[n] = values[n].clone();
      prefix[n] = new int[values[n].length];
      int j = 0;
      for (int i = 1; i < values[n].length; i++) {
        while (j > 0 && values[n][i] != values[n][j]) j = prefix[n][j - 1];
        if (values[n][i] == values[n][j]) j++;
        prefix[n][i] = j;
      }
    }
  }

  public Result scan(InputStream input, long limit) throws IOException {
    if (limit < 0) throw new IllegalArgumentException("Invalid limit");
    Arrays.fill(position, 0);
    boolean[] found = new boolean[needles.length];
    byte[] buffer = new byte[8192];
    long total = 0;
    try {
      while (total < limit) {
        int count = input.read(buffer, 0, (int) Math.min(buffer.length, limit - total));
        if (count < 0) return new Result(found, total, true);
        if (count == 0) throw new IOException("No progress");
        total += count;
        for (int i = 0; i < count; i++) {
          for (int n = 0; n < needles.length; n++) {
            if (found[n]) continue;
            int j = position[n];
            while (j > 0 && buffer[i] != needles[n][j]) j = prefix[n][j - 1];
            if (buffer[i] == needles[n][j]) j++;
            if (j == needles[n].length) { found[n] = true; j = prefix[n][j - 1]; }
            position[n] = j;
          }
        }
      }
      return new Result(found, total, input.read() == -1);
    } finally {
      Arrays.fill(buffer, (byte) 0);
    }
  }

  public void close() {
    for (byte[] value : needles) Arrays.fill(value, (byte) 0);
    for (int[] value : prefix) Arrays.fill(value, 0);
    Arrays.fill(position, 0);
  }
}
