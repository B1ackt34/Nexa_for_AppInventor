# NexaPackagingProbe 0.0.8 — candidata con SDK 0.7.5

Scaricare **NexaPackagingProbe-0.0.8.aix** per provare il nuovo SDK su un progetto
testnet separato. La 0.0.7 resta la baseline già verificata dall'utente sul telefono.

La candidata usa libnexakotlin0.7.5 compilato dal tag ufficiale, con JNI abbinato e
quattro ABI. Build Rush/D8, 49 test controller e controlli packaging superati.
Compilazione APK sul servizio MIT e runtime Android con questo SDK ancora da
verificare. Il SDK upstream dichiara compileSdk37; non è garantita dalla sola AIX
la compatibilità con il server MIT compileSdk36.

Mostrare la nuova proprietà **LibNexaVersion** per verificare il valore `0.7.5`.
Firme dei blocchi precedenti conservate; nessun invio/importazione chiavi aggiunto.
Dettagli, fonti e prove: [profilo SDK0.7.5](../packaging-probe/sdk-upgrade/CANDIDATE-0.0.8.md).

## Funzioni

- Initialize(): inizializzazione nativa, già provata dall'utente nella versione precedente.
- CreateWallet(passphrase): crea e cifra un nuovo wallet testnet; rifiuta DB esistenti.
- OpenWallet(passphrase): riapre il wallet salvato, senza ricrearlo su errore.
- ConfirmBackup(): conferma esplicita di aver registrato la frase di recupero.
- GetAddress(): evento AddressReceived(address), dopo conferma backup.
- GetBalance(): BalanceReceived con saldi NEXA esatti, sincronizzazione, connessione e altezze.
- FastSync(passphrase): avvio manuale della ricerca Electrum, progressi ed esito osservato.
- CancelFastSync(): richiesta di arresto cooperativo.
- FastSyncRunning: stato di un tentativo in corso o in attesa di arresto confermato.

In WalletCreated(recoveryPhrase) mostrare la frase soltanto sul dispositivo e
registrarla privatamente; poi confermare il backup e cancellarla dalla Label.
La passphrase richiede almeno 12 caratteri, non viene salvata dall'estensione.
I successivi avvii usano OpenWallet con la stessa passphrase.
Errori: WalletFailed(operation, code, message).

## Stato delle verifiche

39 test desktop sulle regole di persistenza/non sovrascrittura superati. Build
Rush, D8, controlli ZIP/JSON, descrittori e JNI superati. L'utente ha confermato creazione, indirizzo e riapertura della 0.0.3 sul
dispositivo. I valori reali e la sincronizzazione della nuova GetBalance richiedono
la prova della 0.0.4.
Solo Android 10+ e APK compilato, non Companion. Non è un wallet di produzione.

Aggiornare lo stesso progetto App Inventor e installare l'APK come aggiornamento,
senza disinstallare o cancellare i dati. Dopo OpenWallet, chiamare GetBalance.
BalanceReceived(total, confirmed, unconfirmed, synced, connected, syncedHeight,
chainHeight) usa testo in NEXA con due decimali per i tre saldi, booleani per i
flag e testo per le altezze. Offline o prima della sincronizzazione il saldo
può essere precedente o incompleto. Errori in WalletFailed, mai uno zero fittizio.

## Packaging

Ogni file interno resta sotto 5 MiB per compatibilità con server MIT precedenti
alla correzione b097193903890d6aa729ac52464ae342d3a600e2. La 0.0.2 è stata
importata con successo dall'utente. Tutte e quattro le ABI native sono conservate.
Nessuna nuova API Nexa non documentata: citazioni nei sorgenti e nel README.

Istruzioni complete e fonti: ../packaging-probe/README.md.
Checksum: SHA256SUMS.
Questo è un ramo dedicato, non una GitHub Release: le API Releases dell'ambiente
sono bloccate. Il ramo main non è stato modificato.


## Prova FastSync 0.0.5

Mantenere lo stesso progetto e i dati dell'app. Aprire il wallet esistente,
attendere una chainHeight positiva, poi chiamare FastSync con la stessa passphrase.
Questa scelta aumenta la fiducia nel server Electrum rispetto alla scansione SPV.
Durante il tentativo le chiavi sono temporaneamente sbloccate; al termine viene
chiamato lock prima dell'evento. La passphrase non viene salvata.

Mostrare FastSyncProgress(message). In FastSyncFinished(advanced, synced,
cancelled, beforeHeight, afterHeight, chainHeight) mostrare synced e le altezze,
poi richiamare GetBalance. advanced può includere il progresso SPV contemporaneo;
cancelled registra una richiesta, non un rollback. "finished" non prova successo.

Mostrare WalletFailed. Il watchdog best effort di cinque minuti richiede abort e
riblocco; se FastSyncRunning resta vero dopo un errore di avvio/timeout, arrestare
completamente il processo dell'app e riaprire senza cancellare dati.
Non si avvia una seconda ricerca senza conferma di fine della prima.

Build e 39 test locali superati. La ricerca Electrum reale su Android richiede
ancora prova della nuova versione; non sono promessi velocità o esito garantiti.


## Correzione OpenWallet dopo una ricezione — 0.0.6

Sostituisce l'apertura con collegamento automatico prima dello sblocco con:
openDisconnectedWallet → unlock → blockchainFor/addBlockchain → indirizzo → lock.
L'API pubblica OpenWallet(passphrase) non cambia. Nome DB, rete testnet e chiavi
esistenti restano gli stessi; nessun fallback di creazione o reset aggiunto.

Aggiornare estensione e APK dello stesso progetto senza disinstallare/cancellare
dati. Chiamare OpenWallet, non CreateWallet. Da WalletOpened controllare
GetAddress e GetBalance. L'indirizzo corrente può ruotare dopo una ricezione.
Se fallisce, riportare operation/code/message: ora indica la fase precisa.
Non inviare passphrase, frase di recupero o database.

39 test locali e build verificati. La correzione mira al collegamento prematuro
che nel codice SDK può richiedere derivazioni a wallet locked. Il punto esatto
sul telefono non è dimostrato da stack/DB e richiede la prova dell'utente.


## Protezioni 0.0.7

FLAG_SECURE sulla Form del wallet, revoca della sessione in background, richieste
in coda annullate e verifica SDK del blocco chiavi. Nuovi blocchi LockWallet,
ShowRecoveryPhrase (solo backup iniziale non confermato), ScreenProtected,
SessionLocked e AuthenticationRequired. Al ritorno usare OpenWallet con la
passphrase; FastSync interrotto resta occupato fino alla conferma SDK.

49 test controller superati; richiede prove reali su smartphone. Limiti e fonti
nel README del progetto. Non corregge backup del manifest finale, prima
scrittura SDK in chiaro o residui in memoria/SQLite. Rimane testnet sperimentale.
Aggiornare lo stesso progetto e installare l'APK senza disinstallare/cancellare
dati. Nessun wallet viene ricreato. Verificare screenshot, Home/ritorno e riapertura.


## App diagnostica separata — Nexa Storage Diagnostic 0.1.0

`NexaStorageDiagnostic-0.1.0.apk` si installa direttamente come app distinta
(package org.nexa.storage.diagnostic), senza aggiornare l'estensione 0.0.7 o il
wallet personale. Crea soltanto un wallet testnet usa-e-getta con nome fisso e
password diagnostica pubblica. Non inserire credenziali personali o inviare fondi.

Prima prova: 1 Crea → 2 Cifra → Chiudi processo diagnostico → riaprire l'app →
3 Riapri. Poi Copia rapporto senza segreti. Rimandare i test di interruzione a
dopo la verifica del rapporto normale. Istruzioni e fonti in
../diagnostics/android-storage/README.md.

18 test desktop scanner/protocollo passati, APK compilato, firma v3, allineamento,
backup escluso staticamente e identità JNI verificati. Esecuzione Android ancora
da verificare sul telefono. Una ricerca negativa non prova bonifica dei segreti.
La diagnostica non applica correzioni SDK o backup al wallet esistente.
