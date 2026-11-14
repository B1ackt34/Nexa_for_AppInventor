# Prova candidata 0.0.11 — storage no_backup

**Non aggiornare l'app del wallet principale con questa candidata.** La 0.0.10
resta la baseline funzionante. Usare un progetto App Inventor con nome/package
diverso e un APK installato come app distinta, senza wallet esistente. Companion
non è adatto a verificare isolamento e backup dei dati fra progetti.

Questa candidata cambia la posizione di database wallet e metadati, non le
chiavi o la rete: solo NEXATESTNET, SDK0.7.5. Non migra, cancella o sovrascrive
wallet precedenti. Se rileva database o preferenze della vecchia posizione,
restituisce LEGACY_STORAGE_FOUND. Conservare quella app e usare 0.0.10 per essa.
Non cancellarne i dati per superare questo controllo.

## Blocchi da aggiungere

Un pulsante chiama `GetStorageStatus()`. L'evento `StorageStatus(report)` riceve
un testo JSON: mostrarlo in una Label o copiarlo per la verifica. Non contiene
frase, password, percorsi personali o byte di transazioni.

## Prima verifica sul telefono

1. Importare NexaPackagingProbe-0.0.11.aix nel progetto separato, compilare e
   installare il nuovo APK. Controllare che l'app precedente sia ancora presente.
2. Initialize → InitializationSucceeded → GetStorageStatus.
3. Prima di creare il wallet sono attesi legacyArtifactsFound=false,
   databaseExists=false, metadataExists=false, layout=CONSISTENT.
   Gli indicatori insideNoBackup descrivono qui i percorsi previsti, non file
   già esistenti.
4. Creare un wallet testnet usa-e-getta con CreateWallet, conservare privatamente
   la frase prima di ConfirmBackup. Non condividerla in chat o salvarla in TinyDB.
5. Dopo WalletCreated e BackupConfirmed richiamare GetStorageStatus. Sono attesi:
   databaseExists=true, databaseInsideNoBackup=true, metadataExists=true,
   metadataInsideNoBackup=true, legacyArtifactsFound=false, layout=CONSISTENT,
   temporaryMetadataExists=false. I sidecar esistenti devono risultare nella
   stessa radice; alcuni possono essere assenti, a seconda dello stato SQLite.
6. Chiudere e riaprire l'app, usare OpenWallet con la password e ripetere il
   controllo; poi indirizzo, saldo, ricezione di pochi fondi testnet e sincronizzazione.

Se appare STORAGE_WRITE_UNCONFIRMED, STORAGE_METADATA_INVALID,
STORAGE_PATH_INVALID o STORAGE_INCOMPLETE, fermare la prova e riportare
operation/code/message e il report disponibile. Non ripetere CreateWallet,
non cancellare file, non copiare il solo .db e non eliminare i dati dell'app.
Il report esamina i file presenti; non certifica la durabilità dopo perdita
di alimentazione né lo stato interno di ogni operazione già fallita.

## Prove successive

Dopo la prima verifica riuscita: ripristino da frase in un'altra app distinta,
invio e ritrasmissione con idem invariato, riavvio e stato del pagamento.
Il journal approvato deve restare disponibile dopo riapertura. Per evitare
pagamenti doppi non usare contemporaneamente più installazioni per inviare
gli stessi fondi e non sostituire un pagamento irrisolto con un nuovo SendNexa.

L'esclusione dal backup effettivo richiede un test Android controllato con dati
usa-e-getta. Il JSON contiene deviceBackupRestoreTested=false e
oemTransferVerified=false perché osservare i percorsi non esegue quelle prove.
Backup cloud e trasferimento D2D vanno verificati separatamente con un piano
adatto ai dispositivi disponibili; non usare wallet personali per questi test.

## Perimetro e limiti

I dati spostati sono database wallet/sidecar, conferma backup e journal dei
pagamenti. Database blockchain, log SDK, altri file dell'app e copie in TinyDB
non sono dichiarati protetti da questo cambiamento. Il manifest del servizio
MIT rimane quello generato normalmente; il vecchio controllo della policy di
manifest non diventa automaticamente PASS.

no_backup non cifra i dati: resta la cifratura SDK con la password. File parziali,
metadati senza database o residui temporanei bloccano l'accesso, senza pulizia
automatica. La finestra SDK prima della cifratura non è resa atomica. La candidata
non rimuove backup precedenti e non garantisce ogni trasferimento proprietario OEM.

Sorgenti, motivazione e fonti: ../docs/NO-BACKUP-STORAGE-STUDY.md e
../docs/NO-BACKUP-CANDIDATE-DESIGN.md. Esiti automatici in VALIDATION.md;
nessuna prova Android di backup/trasferimento ancora eseguita per questa build.
