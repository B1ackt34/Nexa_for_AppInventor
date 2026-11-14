# Usare NexaPackagingProbe 0.0.11 in App Inventor

[English version](APP-INVENTOR-GUIDE.en.md)

Candidata NEXATESTNET, Android 10/API29 o superiore. La baseline collaudata resta
0.0.10. **Usare la 0.0.11 in un'app testnet separata senza wallet precedente**:
non migra i dati e rifiuta lo storage legacy con LEGACY_STORAGE_FOUND.
Procedura: [NO-BACKUP-TEST.md](NO-BACKUP-TEST.md).

Database wallet e metadati sono indirizzati a noBackupFilesDir; la collocazione
reale va verificata sul telefono con GetStorageStatus. Backup cloud e D2D non
sono ancora collaudati e gli altri dati SDK/app non sono coperti.

 Importare il file
.aix come estensione e aggiungere il componente non visibile alla Form del
wallet. Il nome del componente rimane NexaPackagingProbe. LibNexaVersion indica
la versione SDK (0.7.5), non la versione dell'estensione (0.0.11).

La guida descrive i blocchi realmente presenti nella 0.0.11. Firme e condizioni
sono ricavate da src/org/nexa/appinventor/probe/NexaPackagingProbe.java e
WalletSession.java. Fonti delle API Nexa sottostanti nel README e in
../docs/RESTORE-WALLET-DESIGN.md. Collaudo e limiti in VALIDATION.md.

## Collegare chiamate ed eventi

Le funzioni avviano operazioni asincrone: aggiornare l'interfaccia negli eventi,
non presumere che il wallet sia pronto appena termina il blocco di chiamata.

| Chiamata | Evento di risultato | Uso |
| --- | --- | --- |
| Initialize() | InitializationSucceeded() oppure InitializationFailed(code,message) | Inizializzare il livello nativo; non crea un wallet. |
| CreateWallet(passphrase) | WalletCreated(recoveryPhrase) | Solo se non esiste un wallet; mostrare la frase privatamente per il backup. |
| RestoreWallet(recoveryPhrase,passphrase) | WalletRestored() | Solo senza wallet esistente; frase valida di 12 parole, nuova password locale. |
| OpenWallet(passphrase) | WalletOpened() | Aprire il wallet esistente; non crea né ripristina un wallet in caso d'errore. |
| ConfirmBackup() | BackupConfirmed() | Conferma dichiarata dall'utente; non confronta il contenuto del backup. |
| ShowRecoveryPhrase(passphrase) | RecoveryPhraseReady(recoveryPhrase) | Solo con sessione aperta e backup iniziale non ancora confermato. |
| LockWallet() | SessionLocked(reason) | Revoca subito l'accesso alla sessione e richiede il blocco delle chiavi. |
| GetAddress() | AddressReceived(address) | Indirizzo pubblico memorizzato all'apertura/creazione/recupero; richiede backup confermato. |
| GetBalance() | BalanceReceived(total,confirmed,unconfirmed,synced,connected,syncedHeight,chainHeight) | Saldi in testo NEXA, flag booleani e altezze in testo. |
| FastSync(passphrase) | FastSyncProgress(message), poi FastSyncFinished(advanced,synced,cancelled,beforeHeight,afterHeight,chainHeight) | Ricerca manuale tramite Electrum; il testo di progresso non è una percentuale. |
| CancelFastSync() | Esito terminale tramite FastSyncFinished o WalletFailed | Cancellazione cooperativa, non immediata. |
| SendNexa(address,amountNexa,maxFeeNexa,passphrase) | SendNexaPrepared(requestId,address,amount,fee,total), poi SendNexaSubmitted(idem) o SendNexaCancelled(requestId,reason) | Preparazione, dialogo Android e consegna all'SDK solo dopo conferma. |
| CancelSendNexa() | SendNexaCancelled(requestId,reason), se esiste una bozza | Annullare prima della conferma; non annulla un pagamento approvato. |
| GetStorageStatus() | StorageStatus(report) | JSON con ruoli e collocazione prevista dei file, senza percorsi personali o segreti; non prova il backup reale. |
| GetLastSendStatus() | SendNexaStatus(idem,state,confirmedHeight) | Leggere l'ultimo pagamento approvato di questa installazione. |
| RetryLastSendNexa() | Anteprima/dialogo e stessi eventi di invio | Confermare la ritrasmissione degli stessi byte già approvati. |

Gestire sempre `WalletFailed(operation,code,message)`. Gli eventi di successo
non sono emessi dopo revoca della richiesta, ad esempio in seguito a background.
Nessun evento è la risposta diretta di una funzione: predisporre il blocco
`quando ...` dell'evento corrispondente.

## Sequenze essenziali

**Prima creazione:** Initialize → InitializationSucceeded → CreateWallet →
WalletCreated → conservare privatamente la frase → conferma dell'utente →
ConfirmBackup → BackupConfirmed → GetAddress e GetBalance.

**Avvio con wallet esistente:** Initialize → InitializationSucceeded →
OpenWallet → WalletOpened → GetBalance e, se backup già confermato, GetAddress.
Se manca il wallet, scegliere esplicitamente fra creazione e ripristino:
non collegare automaticamente un errore di apertura a CreateWallet.

**Ripristino:** Initialize → RestoreWallet → WalletRestored → ConfirmBackup →
BackupConfirmed → GetAddress/GetBalance → attendere sincronizzazione. Per la
prova utilizzare app con package distinto; dettagli in RESTORE-TEST.md.
WalletRestored non significa che tutti i fondi storici siano già stati trovati.

**Ritorno dal background:** SessionLocked segnala la revoca. Al rientro,
AuthenticationRequired richiede una nuova OpenWallet con password. Non salvare
la password per riaprire automaticamente la sessione. Un'operazione SDK già
iniziata può dover terminare prima del blocco effettivo delle chiavi.

**Invio:** verificare backup, saldo sincronizzato e connessione; usare un limite
commissione consapevole. SendNexaPrepared serve a mostrare dati, non ad autorizzare
il pagamento. Solo il pulsante nel dialogo nativo autorizza la transazione.
Conferma scaduta dopo due minuti, annullamento o background prima del consenso
non autorizzano il pagamento. Dopo il consenso non promettere annullamento.

## Diagnostica dello storage

GetStorageStatus può essere chiamato senza aprire il wallet. In StorageStatus,
report è un testo JSON. Prima della creazione databaseExists/metadataExists
sono false; dopo creazione riuscita devono essere true, con entrambi gli
insideNoBackup true, legacyArtifactsFound false e layout CONSISTENT. I sidecar
possono essere assenti; insideNoBackup per un file assente descrive il percorso
previsto. deviceBackupRestoreTested e oemTransferVerified restano false perché
questa chiamata non esegue backup né trasferimento. Le osservazioni non sono
uno snapshot atomico. Lo stato non garantisce durabilità dopo un errore di sync.

## Proprietà di lettura

| Proprietà | Significato |
| --- | --- |
| Initialized | Inizializzazione nativa completata nel processo. |
| LibNexaVersion | SDK incluso: 0.7.5. |
| Network | testnet; nessuna selezione mainnet nella 0.0.11. |
| WalletOpen | Sessione aperta e non revocata; non certifica sync o connessione. |
| FastSyncRunning | Tentativo FastSync ancora in corso, anche se è stata richiesta la cancellazione. |
| SendInProgress | Preparazione, anteprima o chiamata SDK corrente; per un pagamento irrisolto precedente usare GetLastSendStatus. |
| ScreenProtected | FLAG_SECURE presente sulla Form; non protegge automaticamente altre Form o l'intero dispositivo. |

## Importi, commissioni e stato

amountNexa e maxFeeNexa sono testi in NEXA con punto e al massimo due decimali:
per esempio `100` e `0.01`. Quest'ultimo è un limite di commissione totale,
non una tariffa per byte. Secondo la documentazione Nexa, 1 NEXA = 100 sat;
l'estensione usa quantità intere esatte, senza conversioni Double.
Gli input selezionati per nuovi invii richiedono almeno una conferma.

| state di SendNexaStatus | Interpretazione |
| --- | --- |
| NO_SEND | Nessun pagamento approvato nel journal locale. |
| APPROVED | Autorizzazione persistita; possibile interruzione prima/durante la consegna all'SDK. |
| SDK_SUBMITTED | Chiamata SDK completata, senza prova di accettazione remota. |
| UNKNOWN | Esito incerto dopo possibile consegna all'SDK. |
| LOCAL_UNCONFIRMED | Storia locale senza conferma; altezza tipicamente -1, non prova di ricezione in rete. |
| CONFIRMED | SDK rileva un'altezza di conferma >=0; non garantisce finalità irreversibile. |
| REMOVED_OR_INVALID | Stato speciale SDK; non sostituire automaticamente il pagamento. |

In caso di pagamento irrisolto non ripetere SendNexa per creare un pagamento
nuovo. GetLastSendStatus legge lo stato; RetryLastSendNexa, con nuova conferma
nativa, ritrasmette lo stesso pagamento senza ricostruirlo o aumentare la fee.
Annullare una bozza di ritrasmissione non annulla il pagamento originale.
Il journal riguarda questa installazione, non tutti gli invii da altri wallet
o dispositivi. Il ripristino da frase non ricrea il journal dell'app precedente.

## Errori comuni e risposta dell'app

| code | Azione |
| --- | --- |
| INVALID_PASSPHRASE | Richiedere di nuovo la password; non sostituire il wallet. |
| PASSPHRASE_REQUIRED | Richiedere almeno 12 caratteri. |
| INVALID_RECOVERY_PHRASE | Controllare privatamente le 12 parole; non includerle nei report. |
| BACKUP_REQUIRED | Completare il backup e chiamare ConfirmBackup. |
| BACKUP_ALREADY_CONFIRMED | ShowRecoveryPhrase non è più disponibile dopo la conferma iniziale. |
| LEGACY_STORAGE_FOUND | Mantenere la vecchia app con la 0.0.10 e usare un progetto/package separato per la candidata; non cancellare i dati. |
| STORAGE_INCOMPLETE / STORAGE_METADATA_INVALID / STORAGE_PATH_INVALID / STORAGE_WRITE_UNCONFIRMED | Conservare i dati e diagnosticare, senza reset, copia manuale del database o nuovi pagamenti. |
| WALLET_EXISTS | Usare OpenWallet, non tentare un ripristino sopra il database. |
| WALLET_NOT_FOUND | Verificare di essere nell'app corretta; scegliere esplicitamente crea/ripristina senza cancellazioni automatiche. |
| WRONG_NETWORK | Usare un destinatario NEXATESTNET. |
| FEE_LIMIT_EXCEEDED | Nessuna autorizzazione d'invio; rivalutare il limite e preparare eventualmente una nuova bozza. |
| WalletNotEnoughBalanceException | Verificare fondi confermati, importo e commissione. |
| ALREADY_CONFIRMED | Il pagamento risulta confermato; non ritrasmetterlo. |
| SEND_PENDING / SEND_OUTCOME_UNKNOWN | Leggere lo stato del pagamento precedente; non creare un duplicato. |
| LOCK_NOT_CONFIRMED / SEND_CLEANUP_FAILED / ORPHANED_SEND_RECORD / RESTORE_FAILED | Conservare i dati e diagnosticare lo stato; non applicare reset o ricreazione automatica. |

Per la bozza, `reason = USER_CANCELLED` o `EXPIRED` arriva nell'evento
SendNexaCancelled. Altri errori restano possibili: mostrare operation/code/message
senza aggiungere password, frase o database al rapporto.

## Segreti e distribuzione

Password e frase solo in campi appropriati e per il tempo necessario; svuotare
i campi dopo l'uso. Non inserirle in TinyDB, log, URL o messaggi. La frase compare
solo negli eventi WalletCreated e RecoveryPhraseReady, mai in WalletRestored.
Conservare il backup privatamente prima di ConfirmBackup: il successivo
ShowRecoveryPhrase viene intenzionalmente rifiutato. Non si garantisce la
cancellazione certa delle String dalla memoria Java/App Inventor.

Questa guida non rimuove i limiti noti di crash con bozza prenotata, prima
scrittura del wallet e configurazione del backup Android dell'APK finale.
Fare riferimento al README e alla verifica specifica dell'APK. Le prove
manuali riguardano testnet e non costituiscono un rilascio mainnet.
