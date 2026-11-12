# Verifica candidata 0.0.10 — RestoreWallet

Data: 2026-11-12. SDK ufficiale 0.7.5 compilato dal tag fissato, stesso grafo di
53 dipendenze e JNI della 0.0.9. Provenienza e hash in SDK-PROVENANCE.json.
AIX: ../dist/NexaPackagingProbe-0.0.10.aix, accompagnato da ZIP e SHA256SUMS.

## Controlli completati

- 49 test del controller originale e 30 test di invio: superati.
- 23 test del recupero: ordine validazione/cifratura/salvataggio/blocco, rifiuto
  di database esistente o file parziali e journal orfano, password e frase
  respinte prima delle scritture, revoca durante recupero/salvataggio/lettura,
  errori senza fallback, riapertura, rilascio dei due segreti in coda.
- 7 prove del validatore SDK ufficiale su frasi pubbliche: validità, normalizzazione,
  checksum errato, parola sconosciuta, numero parole errato e input limite.
  Eseguite su JVM con SHA-256 JDK e sink di logging Android di solo test; non
  provano Android JNI. Questi adattatori non sono inclusi nell'estensione.
- Build Rush2.1.2/JDK21, riduzione librerie, desugaring, D8 e AIX: riuscita.
- Firme dei blocchi 0.0.9 invariate; solo RestoreWallet e WalletRestored aggiunti.
- ZIP integro, DEX presente, Startup e classi JNI conservati, otto librerie .so
  identiche all'SDK abbinato su quattro ABI. Tutte le entry sotto 5MiB.
- Riconfezionamento verifica DEX, classi e binari invariati.
- Parsing con Java ZipInputStream e org.json del checkout MIT: riuscito.

102 test del controller e 7 del validatore, 109 complessivi. I test del controller
usano un backend fittizio: non sono esecuzione dell'intero wallet Nexa.

## Ancora da collaudare

Compilazione APK del progetto reale con questa versione, validazione mediante
JNI Android, recupero dal backup di prova con fondi ricevuti prima del ripristino,
sincronizzazione e riapertura sul telefono. Procedura in RESTORE-TEST.md.
Nessuna frase personale ricevuta e nessuna transazione inviata dalla cloud.

Il collaudo manuale della baseline 0.0.9 è conservato in history/VALIDATION-0.0.9.md
insieme alla sua provenienza storica. Non viene attribuito alla nuova candidata.

La funzione ufficiale di validazione non ancora citata nei Nexa AI Skills è
l'eccezione specifica autorizzata dall'utente il 2026-11-12. Non sono introdotte
altre eccezioni, librerie o API di importazione di chiavi private.
Piano, fonti e limiti del ripristino: ../docs/RESTORE-WALLET-DESIGN.md.
