# NexaPackagingProbe 0.0.4 — wallet testnet sperimentale

Scaricare **NexaPackagingProbe-0.0.4.aix** e aggiornare l'estensione nel progetto
App Inventor. Le versioni precedenti restano solo come riferimento.

## Funzioni

- Initialize(): inizializzazione nativa, già provata dall'utente nella versione precedente.
- CreateWallet(passphrase): crea e cifra un nuovo wallet testnet; rifiuta DB esistenti.
- OpenWallet(passphrase): riapre il wallet salvato, senza ricrearlo su errore.
- ConfirmBackup(): conferma esplicita di aver registrato la frase di recupero.
- GetAddress(): evento AddressReceived(address), dopo conferma backup.
- GetBalance(): BalanceReceived con saldi NEXA esatti, sincronizzazione, connessione e altezze.

In WalletCreated(recoveryPhrase) mostrare la frase soltanto sul dispositivo e
registrarla privatamente; poi confermare il backup e cancellarla dalla Label.
La passphrase richiede almeno 12 caratteri, non viene salvata dall'estensione.
I successivi avvii usano OpenWallet con la stessa passphrase.
Errori: WalletFailed(operation, code, message).

## Stato delle verifiche

21 test desktop sulle regole di persistenza/non sovrascrittura superati. Build
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
