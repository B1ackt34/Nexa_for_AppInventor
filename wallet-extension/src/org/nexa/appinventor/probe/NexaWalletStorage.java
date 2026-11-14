package org.nexa.appinventor.probe;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import org.json.JSONArray;
import org.nexa.libnexakotlin.Bip44Wallet;
import org.nexa.libnexakotlin.ChainSelector;
import org.nexa.libnexakotlin.InitKt;
import org.nexa.libnexakotlin.PayAddress;
import org.nexa.libnexakotlin.FastforwardKt;
import org.nexa.libnexakotlin.Objectify;
import org.nexa.libnexakotlin.Blockchain;
import kotlin.Unit;
import org.json.JSONObject;
import org.nexa.libnexakotlin.PayAddressType;
import org.nexa.libnexakotlin.BlockchainFactoryKt;
import org.nexa.libnexakotlin.BlockchainConstKt;
import org.nexa.libnexakotlin.TxCompletionFlags;
import org.nexa.libnexakotlin.iTransaction;
import org.nexa.libnexakotlin.iTxOutput;
import org.nexa.libnexakotlin.BCHserialized;
import org.nexa.libnexakotlin.SerializationType;
import org.nexa.libnexakotlin.TransactionHistory;
import java.util.function.Consumer;

/** Adapter for APIs documented in Nexa AI Skills, commit 3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24. */
final class NexaWalletStorage implements WalletSession.Storage {
  static final String NAME = "nexa_ai2_testnet_v1";
  private final NoBackupStore store;

  NexaWalletStorage(Context context) {
    Context app = context.getApplicationContext();
    if (legacyArtifacts(app)) throw new WalletSession.Failure("LEGACY_STORAGE_FOUND",
        "This candidate requires a separate test app. Existing wallet data was not moved or replaced. Keep version 0.0.10 for that app.");
    store = files(app);
    store.checkLayout();
  }

  private static NoBackupStore files(Context context) {
    // Android Context.getNoBackupFilesDir; see docs/NO-BACKUP-STORAGE-STUDY.md.
    return new NoBackupStore(context.getNoBackupFilesDir(), NAME, directory -> {
      if (!directory.isDirectory()) throw new java.io.IOException();
      FileDescriptor fd = Os.open(directory.getAbsolutePath(), OsConstants.O_RDONLY, 0);
      try { Os.fsync(fd); } finally { Os.close(fd); }
    });
  }

  private static boolean legacyArtifacts(Context context) {
    return NoBackupStore.hasLegacyArtifacts(context.getDatabasePath(NAME + ".db"),
        new File(context.getDataDir(), "shared_prefs"), NAME);
  }

  static String storageStatus(Context context) {
    try {
      NoBackupStore files = files(context.getApplicationContext());
      JSONObject data = new JSONObject();
      data.put("diagnosticVersion", 1); data.put("extensionVersion", "0.0.11");
      data.put("storageRoot", "ANDROID_NO_BACKUP");
      data.put("legacyArtifactsFound", legacyArtifacts(context));
      data.put("databaseExists", files.database.exists());
      data.put("databaseInsideNoBackup", files.contained(files.database));
      data.put("metadataExists", files.metadata.exists());
      data.put("metadataInsideNoBackup", files.contained(files.metadata));
      data.put("temporaryMetadataExists", files.temporary.exists());
      String layout = "CONSISTENT";
      try { files.checkLayout(); } catch (WalletSession.Failure error) { layout = error.code; }
      data.put("layout", layout);
      JSONArray sidecars = new JSONArray();
      String[] names = {"WAL", "SHM", "JOURNAL"};
      File[] paths = files.files();
      for (int i = 0; i < names.length; i++) {
        JSONObject item = new JSONObject(); item.put("role", names[i]);
        item.put("exists", paths[i + 1].exists()); item.put("insideNoBackup", files.contained(paths[i + 1]));
        sidecars.put(item);
      }
      data.put("sidecars", sidecars);
      data.put("deviceBackupRestoreTested", false); data.put("oemTransferVerified", false);
      data.put("scope", "WALLET_DATABASE_AND_APP_METADATA_ONLY");
      data.put("observationsAreAtomic", false);
      return data.toString();
    } catch (WalletSession.Failure error) { throw error; }
    catch (Exception error) { throw NoBackupStore.failure("STORAGE_DIAGNOSTIC_FAILED"); }
  }

  public boolean exists() { store.checkLayout(); return store.database.exists(); }
  public boolean hasRestoreArtifacts() { store.checkLayout(); return store.hasDatabaseArtifacts(); }
  public boolean hasSendRecord() { return store.approvedSend() != null; }
  public boolean backupConfirmed() { return store.backupConfirmed(); }
  public void resetBackup() { store.initialize(); }
  public void confirmBackup() { store.confirmBackup(); }
  public SendData.Record approvedSend() { return store.approvedSend(); }
  public void saveApprovedSend(SendData.Record record) { store.saveApprovedSend(record); }
  public String validateRecoveryPhrase(String phrase) { return RecoveryPhrase.validate(phrase); }

  public WalletSession.Handle create() {
    // lifecycle/SKILL.md pattern 1. Absolute path flow verified in SDK init.kt,
    // persist.kt and platformAndroid.kt; no JVM dataDirectory override.
    return new Adapter(InitKt.newWallet(store.pathForCreate(), ChainSelector.NEXATESTNET, null), true);
  }
  public WalletSession.Handle open() {
    // lifecycle/SKILL.md pattern 7; never create after an open failure.
    return new Adapter(InitKt.openDisconnectedWallet(store.pathForOpen()), false);
  }
  public WalletSession.Handle restore(String phrase) {
    // lifecycle/SKILL.md pattern 2; recoveryFlow.md: scan historical transactions.
    return new Adapter(InitKt.recoverWallet(store.pathForCreate(), phrase, ChainSelector.NEXATESTNET), true);
  }

  private static final class Adapter implements WalletSession.Handle {
    private final Bip44Wallet wallet;
    private boolean connected;
    private boolean connectionFailed;
    Adapter(Bip44Wallet wallet, boolean connected) {
      this.wallet = wallet;
      this.connected = connected;
    }

    public void connect() {
      if (connected) return;
      if (connectionFailed) {
        throw new WalletSession.Failure("RESTART_REQUIRED",
            "A previous chain attachment failed. Restart the app process without clearing data.");
      }
      // Wallet lifecycle: chain-bound wallet; never attach a different network.
      if (wallet.getChainSelector() != ChainSelector.NEXATESTNET) {
        throw new WalletSession.Failure("WRONG_NETWORK", "This extension requires a testnet wallet.");
      }
      // Pattern 5: blockchainFor is idempotent per chain. Pattern 7/recoveryFlow.md
      // allow attaching after opening disconnected; addBlockchain is documented
      // in the lifecycle mental model. null/null resume semantics verified against
      // init.kt openWallet and wallet.kt startChain in the official 0.5.69 sources.
      Blockchain chain = InitKt.blockchainFor(ChainSelector.NEXATESTNET, null);
      connectionFailed = true;
      wallet.addBlockchain(chain, null, null);
      connected = true;
      connectionFailed = false;
    }

    public String recoveryPhrase() {
      // Wallet lifecycle pattern 1 and recoveryFlow.md, Create flow step 3.
      return wallet.getRecoverySecret();
    }

    public void encrypt(String passphrase) {
      // Wallet lifecycle pattern 6.
      wallet.encrypt(passphrase);
    }

    public void save() {
      // recoveryFlow.md, Create flow step 6.
      wallet.saveBip44Wallet();
    }

    public Boolean unlock(String passphrase) {
      // Wallet lifecycle pattern 6: true=correct, false=incorrect, null=unencrypted.
      return wallet.unlock(passphrase);
    }

    public Boolean lockedState() {
      // nexa-wallet-lifecycle-and-chain/SKILL.md, pattern 6; recoveryFlow.md.
      return wallet.lockedState();
    }

    public void lock() {
      // Wallet lifecycle pattern 6: drop the decrypted key when no longer needed.
      wallet.lock();
    }

    public String currentTestnetAddress() {
      // Wallet lifecycle pattern 4; identity-and-addresses/validateHelpers.kt chain check.
      PayAddress address = wallet.getCurrentDestination().getAddress();
      if (address.getChainSelector() != ChainSelector.NEXATESTNET) {
        throw new WalletSession.Failure("WRONG_NETWORK", "This extension requires a testnet wallet.");
      }
      // PayAddress string rendering also appears in nexa-ktor-server-integration/SKILL.md.
      return address.toString();
    }

    public WalletBalance balance() {
      // nexa-transaction-construction/SKILL.md, pattern 4: refresh cached balances.
      wallet.clearCachedBalances();
      // nexa-wallet-lifecycle-and-chain/SKILL.md, pattern 4: Long values in sat.
      long total = wallet.getBalance();
      long confirmed = wallet.getBalanceConfirmed();
      long unconfirmed = wallet.getBalanceUnconfirmed();
      boolean synced = wallet.synced(-1L);
      long syncedHeight = wallet.getSyncedHeight();
      long chainHeight = wallet.getBlockchain().getCurHeight();
      // Same skill, Connectivity checks: a P2P peer is connectivity, not finality.
      boolean connected = !wallet.getBlockchain().getNet().getP2pCnxns().isEmpty();
      return new WalletBalance(total, confirmed, unconfirmed, synced, connected,
          syncedHeight, chainHeight);
    }

    private PayAddress paymentAddress(String text) {
      // nexa-identity-and-addresses/validateHelpers.kt, requireLooksLikeNexaAddress.
      if (text == null || text.length() > 4096 || text.indexOf('?') >= 0 || text.indexOf('#') >= 0)
        throw new WalletSession.Failure("INVALID_ADDRESS", "Use a plain testnet address, not a payment URI.");
      PayAddress address;
      try { address = new PayAddress(text.trim()); }
      catch (Exception failure) { throw new WalletSession.Failure("INVALID_ADDRESS", "Address could not be decoded."); }
      PayAddressType type = address.getType();
      if (!type.isValid() || type == PayAddressType.NONE || type == PayAddressType.P2PUBKEY || type == PayAddressType.GROUP)
        throw new WalletSession.Failure("INVALID_ADDRESS_TYPE", "A payable native-coin destination is required.");
      if (address.getChainSelector() != ChainSelector.NEXATESTNET)
        throw new WalletSession.Failure("WRONG_NETWORK", "Only NEXATESTNET destinations are supported.");
      address.lockingScript();
      return address;
    }

    private void verifyOutput(iTransaction tx, String destination, long amount) {
      // transaction-construction/SKILL.md patterns 5/6: completer does not reorder outputs.
      if (tx.getOutputs().isEmpty()) throw new WalletSession.Failure("SEND_OUTPUT_CHANGED", "Payment output is missing.");
      iTxOutput output = tx.getOutputs().get(0);
      PayAddress actual = output.getScript().getAddress();
      if (output.getAmount() != amount || actual == null || !actual.toString().equals(destination))
        throw new WalletSession.Failure("SEND_OUTPUT_CHANGED", "Completed transaction does not match the requested payment.");
    }

    public SendData.Draft preparePayment(String destination, long amount) {
      PayAddress address = paymentAddress(destination);
      // transaction-construction/simpleapi-cheatsheet.md, dust(chain).
      if (amount < BlockchainConstKt.dust(ChainSelector.NEXATESTNET))
        throw new WalletSession.Failure("AMOUNT_BELOW_DUST", "Amount is below the SDK native output minimum.");
      iTransaction tx = BlockchainFactoryKt.txFor(ChainSelector.NEXATESTNET);
      try {
        // transaction-construction/SKILL.md pattern 6; txCompletionReference.md.
        tx.add(BlockchainFactoryKt.txOutputFor(ChainSelector.NEXATESTNET, amount, address.lockingScript()));
        wallet.txCompleter(tx, 1, TxCompletionFlags.FUND_NATIVE | TxCompletionFlags.SIGN,
            null, null, null, null, null, null);
        verifyOutput(tx, address.toString(), amount);
        long fee = Math.subtractExact(tx.getInputTotal(), tx.getOutputTotal());
        return new SendData.Draft(address.toString(), amount, fee, tx.getIdem().toHex(), tx.toHex(), tx);
      } catch (Exception | LinkageError failure) {
        // Same skill: abandon only the transaction we built, never global purges.
        try { wallet.abortTransaction(tx, null); }
        catch (Exception | LinkageError cleanup) {
          throw new WalletSession.Failure("SEND_CLEANUP_FAILED", "SDK preparation cleanup failed. Stop the process before another attempt.");
        }
        throw failure;
      }
    }

    public void abortPayment(SendData.Draft draft) {
      // transaction-construction/SKILL.md, Abandoning a completed-but-unbroadcast tx.
      wallet.abortTransaction((iTransaction) draft.backend, null);
    }
    public void submitPayment(SendData.Draft draft) {
      // transaction-construction/SKILL.md pattern 6. Return is NOT a remote ACK.
      iTransaction tx = (iTransaction) draft.backend;
      if (!tx.toHex().equals(draft.hex) || !tx.getIdem().toHex().equals(draft.idem))
        throw new WalletSession.Failure("SEND_OUTPUT_CHANGED", "Signed payment changed after preview.");
      wallet.send(tx, true, null);
    }
    private iTransaction storedTransaction(SendData.Record record) {
      // transaction-construction/SKILL.md pattern 5: decode using Nexa serialization.
      iTransaction tx = BlockchainFactoryKt.txFor(ChainSelector.NEXATESTNET,
          new BCHserialized(SendData.bytes(record.hex), SerializationType.NETWORK));
      if (!tx.getIdem().toHex().equals(record.idem))
        throw new WalletSession.Failure("SEND_RECORD_INVALID", "Stored transaction identifier does not match its bytes.");
      PayAddress address = paymentAddress(record.destination);
      verifyOutput(tx, address.toString(), record.amount);
      return tx;
    }
    public void validatePayment(SendData.Record record) { storedTransaction(record); }
    public Long paymentHeight(SendData.Record record) {
      // transaction-construction/SKILL.md pattern 4: -1 can be local history only.
      TransactionHistory history = wallet.getTx(storedTransaction(record).getIdem());
      return history == null ? null : history.getConfirmedHeight();
    }
    public void relayPayment(SendData.Record record) {
      storedTransaction(record);
      // Same skill pattern 3: exact approved bytes. No rebuild, repricing or ACK inference.
      wallet.getBlockchain().getNet().broadcastTransaction(SendData.bytes(record.hex));
    }

    public Runnable startFastSync(Consumer<String> progress) {
      // nexa-wallet-lifecycle-and-chain/SKILL.md, Pattern 4, Fast-forward sync.
      // Exact Java signature verified on the published Android 0.5.69 classes.jar.
      Objectify<Boolean> aborter = FastforwardKt.fastForward(wallet, message -> {
        progress.accept(message);
        return Unit.INSTANCE;
      });
      // Same documented pattern: aborter.obj = true, cooperative cancellation only.
      return () -> aborter.setObj(true);
    }
  }
}
