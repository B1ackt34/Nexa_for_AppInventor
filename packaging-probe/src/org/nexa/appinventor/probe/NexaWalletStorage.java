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
