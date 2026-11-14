# Database Nexa in noBackupFilesDir: studio di fattibilità

Data: 2026-11-14. Baseline 0.0.10, SDK0.7.5 dal commit
914879c7574b7d785127206667c96d474b81395a. Solo analisi: nessun cambiamento
all'estensione, ai database, all'APK installato o alla configurazione cloud.

## Conclusione

È una strada tecnicamente fondata per escludere i file sensibili gestiti
dall'estensione dall'Auto Backup Android senza modificare il manifest finale.
La catena dei sorgenti e dei binari esaminati supporta un percorso assoluto del
database sotto Context.getNoBackupFilesDir(). Non è stata ancora verificata
eseguendo il wallet Android in quella directory: non è una funzione già pronta.

La precedente affermazione che servisse necessariamente modificare il manifest
era troppo categorica. Rimane vera per applicare una policy di esclusione
dell'intera app tramite manifest; non è l'unico modo di proteggere file specifici.

## Evidenza Android ufficiale

Sorgenti AOSP nel mirror ufficiale aosp-mirror/platform_frameworks_base,
tag android-16.0.0_r1, consultati direttamente. Non è stato possibile leggere
la pagina developer.android.com dalla rete dell'ambiente; non viene presentata
come una pagina recuperata in questa verifica.

1. core/java/android/content/Context.java, getNoBackupFilesDir: directory
   persistente simile a filesDir, esclusa dal backup automatico remoto; nessun
   nuovo permesso. Il percorso può cambiare se cambia lo storage dell'app:
   ricalcolarlo dal Context a ogni avvio, non persistere il prefisso assoluto.
2. core/java/android/app/backup/BackupAgent.java, onFullBackup e
   getExtraExcludeDirsIfAny: aggiunge noBackupFilesDir alle esclusioni di
   attraversamento, sia nel contesto credential-protected sia device-protected.
   fullBackupFile ignora i file di tali directory. La logica esaminata riguarda
   l'infrastruttura full backup AOSP, con la destinazione di backup selezionata.
3. core/java/android/database/sqlite/SQLiteOpenHelper.java: passa mName a
   Context.getDatabasePath prima di aprire il database.
4. core/java/android/app/ContextImpl.java, getDatabasePath: gestisce un nome
   assoluto senza anteporre la directory databases convenzionale.

Esempio di riferimento verificato:
https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/app/backup/BackupAgent.java

Non equivale a protezione contro root, accesso al processo, condivisione
esplicita dei file o tutti i meccanismi proprietari OEM. Per il D2D occorrono
prove sul sistema di trasferimento effettivamente usato. Non viene promesso il
blocco universale di ogni migrazione fra dispositivi.

## Evidenza Nexa e dipendenze realmente incluse

Fonte primaria: Nexa AI Skills commit3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24,
skills/nexa-wallet-lifecycle-and-chain/SKILL.md, pattern1/2/7 per newWallet,
recoverWallet e openDisconnectedWallet. Nessuna nuova API Nexa viene usata in
questo studio. Il dettaglio di instradamento del percorso è verificato nel
sorgente ufficiale SDK e nei bytecode delle dipendenze congelate, non attribuito
come garanzia Android esplicita ai Skills.

- SDK init.kt: nameAndFilepath separa nome logico e percorso fisico e conserva
  il percorso assoluto. I tre helper passano tale percorso all'overload Path di
  openWalletDB. Il nome logico può restare nexa_ai2_testnet_v1.
- SDK persist.kt: openWalletDB(Path,name,chain) passa pathAndName.toString() a
  createDbDriver; non usa l'overload String con cancellazione e ricreazione su
  errore. Un cambio di directory non deve introdurre fallback su quel percorso.
- SDK androidMain/platformAndroid.kt: createDbDriver passa dbname al costruttore
  AndroidSqliteDriver. Il globale JVM dataDirectory descritto nei Skills non
  viene proposto per Android.
- android-driver-2.4.1.aar: javap mostra che il costruttore passa il nome a
  SupportSQLiteOpenHelper.Configuration.Builder.name. Il parametro
  noBackupDirectory esiste, ma il default verificato è false e Nexa non lo
  imposta. Non assumere quindi che il wallet attuale sia già in no_backup.
- sqlite-framework.aar: FrameworkSQLiteOpenHelper, quando il flag è false,
  inoltra il nome al suo SQLiteOpenHelper, che usa la risoluzione Android sopra.
  Non serve attivare direttamente il flag SQLDelight o modificare il binario SDK
  per la prima prova con percorso assoluto.

L'AIX e l'APK correnti conservano la posizione convenzionale del database.
Questa analisi non ne cambia retroattivamente l'esposizione al backup.

## Inventario da coprire

| Dato | Posizione attuale nel codice dell'estensione | Obiettivo proposto |
| --- | --- | --- |
| Database wallet | getDatabasePath(NAME + .db) | File in una sottodirectory di noBackupFilesDir. |
| WAL, SHM, rollback journal SQLite | File associati al database | Verificarne sul dispositivo la collocazione accanto al database, senza copie altrove. |
| Pagamento approvato, byte firmati e metadati | SharedPreferences NAME + _send | Journal privato nella stessa radice no_backup; scrittura affidabile prima della consegna all'SDK. |
| Conferma del backup | SharedPreferences NAME | Metadato sotto no_backup per mantenere coerente il ciclo di vita con il wallet. |
| Database blockchain, log e file ausiliari SDK | Gestiti separatamente dall'SDK | Inventariare prima di dichiarare protetti tutti i dati Nexa; non presumere che cambino posizione insieme al wallet. |
| Copie in TinyDB, export, altri componenti o Form | Gestiti dall'app che usa l'estensione | Fuori dal controllo del solo spostamento del database; evitarli per i segreti. |

Il journal non contiene seed/password/chiavi private ma contiene una transazione
firmata e lo stato di autorizzazione; la sua coerenza è necessaria per non
perdere la protezione contro un nuovo pagamento dopo un esito incerto.

Le SharedPreferences ordinarie non si spostano insieme al database. Non basta
cambiare NAME o passare un percorso come nome delle preferenze. Per questi
metadati va progettato uno storage privato locale, per esempio con AtomicFile
Android, validazione del formato e verifica degli errori di scrittura. La
durabilità richiesta dal pagamento va verificata; non si presume da una semplice
scrittura JSON. Nessun segreto in messaggi diagnostici o nuovi eventi.

## Architettura della prima candidata di prova

1. Calcolare una radice canonica sotto getNoBackupFilesDir(), creare una
   sottodirectory privata e usare solo nomi costanti. Nessun percorso arbitrario
   fornito dall'utente e nessun symlink che porti fuori dalla radice verificata.
2. Centralizzare in un resolver database, sidecar e metadati; aggiornare tutti
   i controlli exists/create/open/restore, non solo il costruttore del wallet.
3. Conservare le firme pubbliche dei blocchi e l'identità logica del wallet;
   cambiare solo l'archiviazione della candidata nuova su un'app di prova distinta.
4. Ricalcolare il percorso a ogni avvio e richiedere cifratura/blocco come prima:
   no_backup non cifra i dati e non sostituisce la password o la frase di backup.
5. Rifiutare ambiguità fra storage vecchio e nuovo, metadati senza database e
   file parziali. Non creare un wallet vuoto se quello precedente non è trovato
   nella nuova directory. Non eliminare o ignorare automaticamente il vecchio.

Per questo primo prototipo non sono necessarie nuove librerie Nexa, nuovi
permessi o un build server modificato. Rimangono da verificare gli stessi
SDK/JNI, il driver effettivo e l'app compilata con il servizio MIT.

## Wallet già esistenti: migrazione rinviata

La prima prova deve partire da un'app testnet separata, senza migrare il wallet
principale. La 0.0.10 resta disponibile e invariata.

Una successiva migrazione deve considerare DB, WAL, journal di pagamento e
metadati come un insieme coerente. Non copiare o spostare il solo .db mentre
SQLite è aperto. pause() documentato ferma il processing, ma non prova chiusura
del driver o completamento del checkpoint WAL; non basta da solo per copiare.
Prima di codificare una migrazione serve un percorso documentato di chiusura
o snapshot consistente, oppure una procedura a processo freddo verificata.

Non proporre una migrazione automatica che, in caso d'errore, rimuove i dati o
torna a newWallet. Prevedere ripresa dopo crash, verifica di indirizzi/saldo/stato
invio e gestione esplicita delle copie legacy. Conservare una copia nella vecchia
directory durante la migrazione mantiene quella copia idonea al backup: non
dichiarare completata la protezione prima di aver gestito tutte le copie.
Né la migrazione né le regole XML rimuovono eventuali backup già esistenti.

## Prove richieste prima di adottarla

- Controller: scelta del percorso, assenza di fallback, file vecchi/nuovi
  concorrenti, journal assente/corrotto, errori di scrittura e revoca.
- Telefono, APK distinto: percorso reale canonico del DB e dei sidecar dentro
  no_backup, assenza di copie nelle directory databases/shared_prefs ordinarie
  per i dati trasferiti, cifratura e riapertura dopo chiusura del processo.
- Creazione, ripristino da frase testnet, sync/FastSync, ricezione e invio,
  ritrasmissione con idem invariato, protezione dopo riavvio ed esito incerto.
- Backup cloud e restore controllati con dati usa-e-getta; separatamente D2D
  sul sistema effettivo disponibile. Un singolo trasferimento non dimostra
  comportamento identico su ogni OEM/versione Android.
- Diagnostica con soli ruoli, booleani di contenimento e stati; non raccogliere
  seed, password, DB personale o contenuti delle transazioni dell'utente.

La lettura statica del manifest non dimostra in quale directory siano salvati i
dati runtime. Il vecchio gate static_backup_policy verifica una policy
restrittiva di manifest e continuerebbe correttamente a non soddisfarla: per
questa architettura serve una verifica separata dei percorsi e del backup reale,
non un PASS ottenuto indebolendo il controllo senza evidenza.

## Stato e prossima azione

Studio completato con evidenza statica. Nessuna prova Android di scrittura o
backup eseguita e nessuna migrazione iniziata. Prima implementazione consigliata:
una candidata testnet per nuovi wallet in un'app separata, con storage coerente
di database e metadati. Aggiornare entrambe le guide IT/EN se cambia il prodotto.

Evidenza locale: /workspace/scratch/no-backup-storage-study/ con sorgenti AOSP,
SHA256 e disassemblati dei due driver. Le copie SDK e i 53 artefatti congelati
restano quelli registrati nella provenienza della 0.0.10.
