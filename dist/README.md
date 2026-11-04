# NexaPackagingProbe 0.0.3 — wallet testnet sperimentale

Scaricare **NexaPackagingProbe-0.0.3.aix** e aggiornare l'estensione nel progetto
App Inventor. Le versioni 0.0.1 e 0.0.2 restano solo come riferimento.

## Funzioni

- Initialize(): inizializzazione nativa, già provata dall'utente nella versione precedente.
- CreateWallet(passphrase): crea e cifra un nuovo wallet testnet; rifiuta DB esistenti.
- OpenWallet(passphrase): riapre il wallet salvato, senza ricrearlo su errore.
- ConfirmBackup(): conferma esplicita di aver registrato la frase di recupero.
- GetAddress(): evento AddressReceived(address), dopo conferma backup.

In WalletCreated(recoveryPhrase) mostrare la frase soltanto sul dispositivo e
registrarla privatamente; poi confermare il backup e cancellarla dalla Label.
La passphrase richiede almeno 12 caratteri, non viene salvata dall'estensione.
I successivi avvii usano OpenWallet con la stessa passphrase.
Errori: WalletFailed(operation, code, message).

## Stato delle verifiche

12 test desktop sulle regole di persistenza/non sovrascrittura superati. Build
Rush, D8, controlli ZIP/JSON, descrittori e JNI superati. Il DB/cifratura/rete reali
su Android richiedono ancora la prova della versione 0.0.3 sul dispositivo.
Solo Android 10+ e APK compilato, non Companion. Non è un wallet di produzione.

## Packaging

Ogni file interno resta sotto 5 MiB per compatibilità con server MIT precedenti
alla correzione b097193903890d6aa729ac52464ae342d3a600e2. La 0.0.2 è stata
importata con successo dall'utente. Tutte e quattro le ABI native sono conservate.
Nessuna nuova API Nexa non documentata: citazioni nei sorgenti e nel README.

Istruzioni complete e fonti: ../packaging-probe/README.md.
Checksum: SHA256SUMS.
Questo è un ramo dedicato, non una GitHub Release: le API Releases dell'ambiente
sono bloccate. Il ramo main non è stato modificato.
