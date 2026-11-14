package org.nexa.appinventor.probe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONObject;

/** App-owned storage protocol. No new Nexa APIs. All names are internal constants. */
final class NoBackupStore {
  interface DirectorySync { void sync(File directory) throws Exception; }
  final File base, root, database, metadata, temporary;
  private final DirectorySync directorySync;
  private boolean writeUnconfirmed;
  private static final int LIMIT = 3 * 1024 * 1024;

  NoBackupStore(File noBackupDirectory, String walletName, DirectorySync directorySync) {
    if (!walletName.matches("[a-zA-Z0-9_]+")) throw failure("STORAGE_PATH_INVALID");
    this.base = noBackupDirectory;
    this.root = new File(base, "nexa_wallet_v1");
    this.database = new File(root, walletName + ".db");
    this.metadata = new File(root, "state.json");
    this.temporary = new File(root, "state.json.new");
    this.directorySync = directorySync;
    validatePaths();
  }

  static WalletSession.Failure failure(String code) {
    return new WalletSession.Failure(code, "Wallet storage needs attention. No data was moved, reset or automatically replaced.");
  }

  static boolean hasLegacyArtifacts(File database, File preferences, String walletName) {
    for (String suffix : new String[] {"", "-wal", "-shm", "-journal"}) {
      File file = new File(database + suffix);
      if (file.exists() || Files.isSymbolicLink(file.toPath())) return true;
    }
    for (String name : new String[] {walletName + ".xml", walletName + ".xml.bak",
        walletName + "_send.xml", walletName + "_send.xml.bak"}) {
      File file = new File(preferences, name);
      if (file.exists() || Files.isSymbolicLink(file.toPath())) return true;
    }
    return false;
  }

  boolean contained(File file) {
    try {
      return !Files.isSymbolicLink(root.toPath()) && !Files.isSymbolicLink(file.toPath())
          && root.getCanonicalFile().getParentFile().equals(base.getCanonicalFile())
          && file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile());
    } catch (IOException error) { return false; }
  }

  private void validatePaths() {
    for (File file : files()) {
      if (!contained(file) || (file.exists() && !file.isFile())) throw failure("STORAGE_PATH_INVALID");
    }
    if (root.exists() && !root.isDirectory()) throw failure("STORAGE_PATH_INVALID");
  }

  File[] files() {
    return new File[] {database, new File(database + "-wal"), new File(database + "-shm"),
        new File(database + "-journal"), metadata, temporary};
  }

  boolean hasDatabaseArtifacts() {
    for (int i = 0; i < 4; i++) if (files()[i].exists()) return true;
    return false;
  }

  void checkLayout() {
    validatePaths();
    if (writeUnconfirmed) throw failure("STORAGE_WRITE_UNCONFIRMED");
    if (temporary.exists()) throw failure("STORAGE_INCOMPLETE");
    if (database.exists()) {
      if (!metadata.exists()) throw failure("STORAGE_INCOMPLETE");
      read();
    } else if (metadata.exists() || hasDatabaseArtifacts()) throw failure("STORAGE_INCOMPLETE");
  }

  void initialize() {
    checkLayout();
    if (database.exists()) throw failure("WALLET_EXISTS");
    try {
      if (!root.exists()) {
        if (!root.mkdir()) throw new IOException();
        directorySync.sync(base);
      }
      validatePaths();
      JSONObject data = new JSONObject();
      data.put("version", 1); data.put("backup_confirmed", false);
      write(data);
    } catch (WalletSession.Failure error) { throw error; }
    catch (Exception error) { writeUnconfirmed = true; throw failure("STORAGE_WRITE_UNCONFIRMED"); }
  }

  String pathForCreate() {
    validatePaths();
    if (writeUnconfirmed || temporary.exists()) throw failure("STORAGE_INCOMPLETE");
    if (hasDatabaseArtifacts()) throw failure("WALLET_EXISTS");
    if (!metadata.exists()) throw failure("STORAGE_INCOMPLETE");
    JSONObject data = read(); // Initialized metadata is required before SDK creation starts.
    try {
      if (data.getBoolean("backup_confirmed") || data.has("approved")) throw failure("STORAGE_INCOMPLETE");
    } catch (WalletSession.Failure error) { throw error; }
    catch (Exception error) { throw failure("STORAGE_METADATA_INVALID"); }
    return database.getAbsolutePath();
  }

  String pathForOpen() {
    checkLayout();
    if (!database.exists()) throw failure("WALLET_NOT_FOUND");
    return database.getAbsolutePath();
  }

  private JSONObject read() {
    try {
      if (!contained(metadata) || metadata.length() > LIMIT) throw new IOException();
      byte[] bytes = Files.readAllBytes(metadata.toPath());
      if (bytes.length > LIMIT) throw new IOException();
      JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
      if (!(data.get("version") instanceof Number) || !data.get("version").toString().equals("1")
          || !(data.get("backup_confirmed") instanceof Boolean)) throw new IOException();
      if (data.has("approved")) decodeRecord(data.getJSONObject("approved"));
      return data;
    } catch (Exception error) { throw failure("STORAGE_METADATA_INVALID"); }
  }

  private void write(JSONObject data) {
    try {
      validatePaths();
      if (writeUnconfirmed || temporary.exists()) throw new IOException();
      byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
      if (bytes.length > LIMIT) throw new IOException();
      if (!temporary.createNewFile()) throw new IOException();
      try (FileOutputStream output = new FileOutputStream(temporary)) {
        output.write(bytes);
        output.getFD().sync();
      }
      // Both files have the same parent. Fail rather than fall back to a non-atomic copy.
      Files.move(temporary.toPath(), metadata.toPath(), StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      directorySync.sync(root);
    } catch (Exception error) {
      writeUnconfirmed = true;
      // Preserve partial files for diagnosis; never authorize a payment on this error.
      throw failure("STORAGE_WRITE_UNCONFIRMED");
    }
  }

  boolean backupConfirmed() {
    checkLayout();
    try { return metadata.exists() && read().getBoolean("backup_confirmed"); }
    catch (Exception error) { throw failure("STORAGE_METADATA_INVALID"); }
  }

  void confirmBackup() {
    checkLayout();
    try {
      JSONObject data = read(); data.put("backup_confirmed", true); write(data);
    } catch (WalletSession.Failure error) { throw error; }
    catch (Exception error) { throw failure("STORAGE_METADATA_INVALID"); }
  }

  SendData.Record approvedSend() {
    checkLayout();
    if (!metadata.exists()) return null;
    try { JSONObject data = read(); return data.has("approved") ? decodeRecord(data.getJSONObject("approved")) : null; }
    catch (Exception error) { throw failure("STORAGE_METADATA_INVALID"); }
  }

  void saveApprovedSend(SendData.Record record) {
    checkLayout();
    try {
      JSONObject data = read(), approved = new JSONObject();
      approved.put("requestId", record.requestId); approved.put("destination", record.destination);
      approved.put("amount", record.amount); approved.put("fee", record.fee);
      approved.put("idem", record.idem); approved.put("hex", record.hex); approved.put("state", record.state);
      approved.put("checksum", record.checksum()); data.put("approved", approved);
      write(data);
    } catch (WalletSession.Failure error) { throw error; }
    catch (Exception error) { throw failure("STORAGE_METADATA_INVALID"); }
  }

  private static SendData.Record decodeRecord(JSONObject data) throws Exception {
    SendData.Record record = new SendData.Record(data.getString("requestId"), data.getString("destination"),
        data.getLong("amount"), data.getLong("fee"), data.getString("idem"), data.getString("hex"), data.getString("state"));
    if (!record.checksum().equals(data.getString("checksum"))) throw new IOException();
    return record;
  }
}
