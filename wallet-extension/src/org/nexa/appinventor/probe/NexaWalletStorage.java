package org.nexa.appinventor.probe;

import android.content.Context;
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
  private final Context context;

  NexaWalletStorage(Context context) {
    this.context = context.getApplicationContext();
  }

  public boolean exists() {
    // walletStartupTemplate.kt: test the Android database path BEFORE opening it.
    return context.getDatabasePath(NAME + ".db").exists();
  }

  public WalletSession.Handle create() {
    // nexa-wallet-lifecycle-and-chain/SKILL.md, pattern 1; initializer defaults to null.
    return new Adapter(InitKt.newWallet(NAME, ChainSelector.NEXATESTNET, null), true);
  }

  public WalletSession.Handle open() {
    // Wallet lifecycle pattern 7; recoveryFlow.md: open disconnected, attach later.
    // Unlike openWallet(), this does not prepare new addresses before unlock.
    return new Adapter(InitKt.openDisconnectedWallet(NAME), false);
  }

  public boolean backupConfirmed() {
    // App metadata only: contains no password, seed or private key.
    return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("backup_confirmed", false);
  }

  public boolean hasRestoreArtifacts() {
    for (String suffix : new String[] {"", "-wal", "-shm", "-journal"}) {
      if (context.getDatabasePath(NAME + ".db" + suffix).exists()) return true;
    }
    return false;
  }

  public boolean hasSendRecord() {
    return context.getSharedPreferences(NAME + "_send", Context.MODE_PRIVATE).contains("approved");
  }

  public String validateRecoveryPhrase(String phrase) { return RecoveryPhrase.validate(phrase); }

  public WalletSession.Handle restore(String phrase) {
    if (hasRestoreArtifacts()) throw new WalletSession.Failure("WALLET_EXISTS", "Existing wallet data will not be replaced.");
    // Nexa AI Skills: nexa-wallet-lifecycle-and-chain/SKILL.md pattern 2;
    // recoveryFlow.md Restore flow: recoverWallet scans historical transactions.
    return new Adapter(InitKt.recoverWallet(NAME, phrase, ChainSelector.NEXATESTNET), true);
  }

  public void resetBackup() {
    if (!context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        .edit().putBoolean("backup_confirmed", false).commit()) {
      throw new WalletSession.Failure("BACKUP_SAVE_FAILED", "Could not initialize backup confirmation.");
    }
  }

  public void confirmBackup() {
    boolean saved = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        .edit().putBoolean("backup_confirmed", true).commit();
    if (!saved) {
      throw new WalletSession.Failure("BACKUP_SAVE_FAILED", "Could not save the backup confirmation.");
    }
  }

  // App-owned journal of ONE approved signed transaction. No password/seed/private key.
  public SendData.Record approvedSend() {
    String raw = context.getSharedPreferences(NAME + "_send", Context.MODE_PRIVATE).getString("approved", null);
    if (raw == null) return null;
    try {
      if (raw.length() > 3 * 1024 * 1024) throw new IllegalArgumentException();
      JSONObject data = new JSONObject(raw);
      if (data.getInt("version") != 1) throw new IllegalArgumentException();
      SendData.Record record = new SendData.Record(data.getString("requestId"), data.getString("destination"),
          data.getLong("amount"), data.getLong("fee"), data.getString("idem"), data.getString("hex"), data.getString("state"));
      if (!record.checksum().equals(data.getString("checksum"))) throw new IllegalArgumentException();
      return record;
    } catch (Exception failure) {
      throw new WalletSession.Failure("SEND_RECORD_INVALID", "Stored payment is unreadable. New payments are blocked; wallet data are not replaced.");
    }
  }

  public void saveApprovedSend(SendData.Record record) {
    try {
      JSONObject data = new JSONObject();
      data.put("version", 1); data.put("requestId", record.requestId);
      data.put("destination", record.destination); data.put("amount", record.amount); data.put("fee", record.fee);
      data.put("idem", record.idem); data.put("hex", record.hex); data.put("state", record.state);
      data.put("checksum", record.checksum());
      if (!context.getSharedPreferences(NAME + "_send", Context.MODE_PRIVATE).edit().putString("approved", data.toString()).commit())
        throw new IllegalStateException();
    } catch (Exception failure) {
      throw new WalletSession.Failure("SEND_JOURNAL_FAILED", "Could not persist payment authorization. Inspect status before another attempt.");
    }
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
