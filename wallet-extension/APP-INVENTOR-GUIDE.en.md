# Using NexaPackagingProbe 0.0.11 in App Inventor

[Versione italiana](APP-INVENTOR-GUIDE.md)

NEXATESTNET candidate, Android 10/API29 or later. The validated baseline remains
0.0.10. **Use 0.0.11 in a separate testnet app without a previous wallet**:
it does not migrate data and rejects legacy storage with LEGACY_STORAGE_FOUND.
Procedure: [NO-BACKUP-TEST.md](NO-BACKUP-TEST.md) (currently in Italian).

Wallet database and metadata target noBackupFilesDir; verify the actual location
on the phone using GetStorageStatus. Cloud backup and D2D have not yet been
tested, and other SDK/app data is outside the scope of this change.

 Import the .aix file
as an extension and add the non-visible component to the wallet Form. The
component name remains NexaPackagingProbe. LibNexaVersion reports the SDK
version (0.7.5), not the extension version (0.0.11).

This guide describes the blocks actually available in 0.0.11. Signatures and
conditions come from src/org/nexa/appinventor/probe/NexaPackagingProbe.java and
WalletSession.java. Sources for the underlying Nexa APIs are listed in the
README and ../docs/RESTORE-WALLET-DESIGN.md. Test results and limitations are
recorded in VALIDATION.md. These supporting documents are currently in Italian.

## Connecting calls and events

Functions start asynchronous operations: update the interface in event handlers,
rather than assuming the wallet is ready when the function call block finishes.

| Call | Result event | Purpose |
| --- | --- | --- |
| Initialize() | InitializationSucceeded() or InitializationFailed(code,message) | Initialize the native layer; does not create a wallet. |
| CreateWallet(passphrase) | WalletCreated(recoveryPhrase) | Only when no wallet exists; display the phrase privately for backup. |
| RestoreWallet(recoveryPhrase,passphrase) | WalletRestored() | Only when no wallet exists; valid 12-word phrase and a new local password. |
| OpenWallet(passphrase) | WalletOpened() | Open the existing wallet; does not create or restore a wallet on failure. |
| ConfirmBackup() | BackupConfirmed() | Record the user's confirmation; does not compare the backup contents. |
| ShowRecoveryPhrase(passphrase) | RecoveryPhraseReady(recoveryPhrase) | Only with an open session and before the initial backup is confirmed. |
| LockWallet() | SessionLocked(reason) | Immediately revoke session access and request key locking. |
| GetAddress() | AddressReceived(address) | Public address cached when opening, creating or restoring; requires backup confirmation. |
| GetBalance() | BalanceReceived(total,confirmed,unconfirmed,synced,connected,syncedHeight,chainHeight) | Balances as NEXA text, boolean flags and heights as text. |
| FastSync(passphrase) | FastSyncProgress(message), then FastSyncFinished(advanced,synced,cancelled,beforeHeight,afterHeight,chainHeight) | Manual lookup through Electrum; progress text is not a percentage. |
| CancelFastSync() | Terminal result through FastSyncFinished or WalletFailed | Cooperative cancellation, not immediate termination. |
| SendNexa(address,amountNexa,maxFeeNexa,passphrase) | SendNexaPrepared(requestId,address,amount,fee,total), then SendNexaSubmitted(idem) or SendNexaCancelled(requestId,reason) | Prepare, show an Android dialog and hand off to the SDK only after confirmation. |
| CancelSendNexa() | SendNexaCancelled(requestId,reason), if a preview exists | Cancel before confirmation; does not cancel an approved payment. |
| GetStorageStatus() | StorageStatus(report) | JSON with file roles and expected locations, without personal paths or secrets; does not prove actual backup behavior. |
| GetLastSendStatus() | SendNexaStatus(idem,state,confirmedHeight) | Read the last approved payment for this installation. |
| RetryLastSendNexa() | Preview/dialog and the same send events | Confirm retransmission of the exact bytes already approved. |

Always handle `WalletFailed(operation,code,message)`. Success events are not
delivered after request revocation, for example after the app goes into the
background. Events are not direct function return values: add the corresponding
`when ...` event handler block.

## Essential sequences

**First creation:** Initialize → InitializationSucceeded → CreateWallet →
WalletCreated → privately back up the phrase → user confirmation →
ConfirmBackup → BackupConfirmed → GetAddress and GetBalance.

**Starting with an existing wallet:** Initialize → InitializationSucceeded →
OpenWallet → WalletOpened → GetBalance and, if backup is already confirmed,
GetAddress. If no wallet exists, explicitly choose creation or restoration:
do not automatically connect an open failure to CreateWallet.

**Restoration:** Initialize → RestoreWallet → WalletRestored → ConfirmBackup →
BackupConfirmed → GetAddress/GetBalance → wait for synchronization. Use apps
with different package names for testing; see RESTORE-TEST.md. WalletRestored
does not mean that all historical funds have already been discovered.

**Returning from the background:** SessionLocked reports revocation. On return,
AuthenticationRequired requests a new OpenWallet call with the password. Do not
save the password to reopen the session automatically. An SDK operation already
in progress may have to finish before keys can actually be locked.

**Sending:** check backup, synchronized balance and connectivity; choose the
fee limit deliberately. SendNexaPrepared provides information, not payment
authorization. Only the button in the native dialog authorizes the transaction.
Expiry after two minutes, cancellation or backgrounding before consent does
not authorize payment. After consent, do not promise cancellation.

## Storage diagnostics

GetStorageStatus can be called without opening the wallet. In StorageStatus,
report is a JSON string. Before creation, databaseExists/metadataExists are
false; after successful creation they should be true, with both insideNoBackup
flags true, legacyArtifactsFound false and layout CONSISTENT. Sidecars may be
absent; insideNoBackup for an absent file describes its expected location.
deviceBackupRestoreTested and oemTransferVerified remain false because this call
does not perform backups or transfers. Observations are not an atomic snapshot.
The report does not guarantee durability after a sync failure.

## Read-only properties

| Property | Meaning |
| --- | --- |
| Initialized | Native initialization has completed in this process. |
| LibNexaVersion | Bundled SDK: 0.7.5. |
| Network | testnet; no mainnet selection in 0.0.11. |
| WalletOpen | Session is open and not revoked; does not certify synchronization or connectivity. |
| FastSyncRunning | A FastSync attempt is still in progress, including after cancellation has been requested. |
| SendInProgress | Current preparation, preview or SDK call; use GetLastSendStatus for an earlier unresolved payment. |
| ScreenProtected | FLAG_SECURE is set on the Form; does not automatically protect other Forms or the entire device. |

## Amounts, fees and status

amountNexa and maxFeeNexa are NEXA text values with a decimal point and at most
two decimal places, for example `100` and `0.01`. The latter is a total fee
limit, not a per-byte fee rate. According to the Nexa documentation,
1 NEXA = 100 sat; the extension uses exact integer quantities without Double
conversions. Inputs selected for new payments require at least one confirmation.

| SendNexaStatus state | Interpretation |
| --- | --- |
| NO_SEND | No approved payment in the local journal. |
| APPROVED | Authorization was persisted; an interruption may have occurred before/during SDK handoff. |
| SDK_SUBMITTED | SDK call completed, without proof of remote acceptance. |
| UNKNOWN | Uncertain outcome after possible SDK handoff. |
| LOCAL_UNCONFIRMED | Local history without confirmation; height is typically -1, not proof of network receipt. |
| CONFIRMED | SDK reports a confirmation height >=0; does not guarantee irreversible finality. |
| REMOVED_OR_INVALID | Special SDK state; do not automatically replace the payment. |

For an unresolved payment, do not repeat SendNexa to create a new payment.
GetLastSendStatus reads the status; RetryLastSendNexa, with a new native
confirmation, retransmits the same payment without rebuilding it or increasing
the fee. Cancelling a retransmission preview does not cancel the original
payment. The journal belongs to this installation, not every payment made by
other wallets or devices. Phrase restoration does not recreate the previous
app's journal.

## Common errors and app responses

| code | Action |
| --- | --- |
| INVALID_PASSPHRASE | Request the password again; do not replace the wallet. |
| PASSPHRASE_REQUIRED | Require at least 12 characters. |
| INVALID_RECOVERY_PHRASE | Privately check the 12 words; do not include them in reports. |
| BACKUP_REQUIRED | Complete the backup and call ConfirmBackup. |
| BACKUP_ALREADY_CONFIRMED | ShowRecoveryPhrase is no longer available after initial confirmation. |
| LEGACY_STORAGE_FOUND | Keep the old app on 0.0.10 and use a separate project/package for this candidate; do not clear its data. |
| STORAGE_INCOMPLETE / STORAGE_METADATA_INVALID / STORAGE_PATH_INVALID / STORAGE_WRITE_UNCONFIRMED | Preserve data and diagnose; do not reset, manually copy the database or create new payments. |
| WALLET_EXISTS | Use OpenWallet; do not attempt restoration over the database. |
| WALLET_NOT_FOUND | Check that the correct app is running; explicitly choose creation/restoration without automatic deletion. |
| WRONG_NETWORK | Use a NEXATESTNET destination. |
| FEE_LIMIT_EXCEEDED | No send authorization; reconsider the limit and prepare a new preview if appropriate. |
| WalletNotEnoughBalanceException | Check confirmed funds, amount and fee. |
| ALREADY_CONFIRMED | The payment is reported as confirmed; do not retransmit it. |
| SEND_PENDING / SEND_OUTCOME_UNKNOWN | Read the previous payment's status; do not create a duplicate. |
| LOCK_NOT_CONFIRMED / SEND_CLEANUP_FAILED / ORPHANED_SEND_RECORD / RESTORE_FAILED | Preserve the data and diagnose the state; do not automatically reset or recreate the wallet. |

For previews, `reason = USER_CANCELLED` or `EXPIRED` arrives in the
SendNexaCancelled event. Other errors are possible: display operation/code/message
without adding the password, phrase or database to a report.

## Secrets and distribution

Keep the password and phrase in appropriate fields only for as long as needed;
clear the fields after use. Do not put them in TinyDB, logs, URLs or messages.
The phrase appears only in WalletCreated and RecoveryPhraseReady events, never
in WalletRestored. Privately preserve the backup before ConfirmBackup:
subsequent ShowRecoveryPhrase calls are intentionally rejected. Secure erasure
of Strings from Java/App Inventor memory cannot be guaranteed.

This guide does not remove the known limitations around crashes with reserved
preview inputs, the wallet's initial write or the final APK's Android backup
configuration. Refer to the README and the specific APK review. Manual tests
cover testnet and do not constitute a mainnet release.
