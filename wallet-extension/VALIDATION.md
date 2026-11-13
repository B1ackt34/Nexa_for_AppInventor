# Verifica 0.0.10 — RestoreWallet

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

## Collaudo manuale riferito dall'utente

Aggiornamento del riepilogo: 2026-11-13. Esiti confermati nella conversazione
dall'utente sul telefono, distinti dai 109 test automatici sopra elencati.

| Prova | Esito riferito |
| --- | --- |
| RestoreWallet nell'app di prova | Nuova versione funzionante e recupero riuscito. |
| Recupero di fondi ricevuti prima del ripristino | Saldo storico atteso riconosciuto dopo la sincronizzazione. |
| Chiusura completa, riavvio e OpenWallet | Riapertura riuscita con la nuova password locale. |
| RestoreWallet con wallet già presente | Rifiuto con WALLET_EXISTS; saldo successivo corretto. |
| Frase incompleta nell'app senza wallet | Confermato l'esito atteso INVALID_RECOVERY_PHRASE, senza WalletRestored. |
| Frase pubblica di 12 parole con checksum errato | Prova confermata superata: INVALID_RECOVERY_PHRASE. |
| Parola fuori dizionario | Confermato il rifiuto atteso, senza riportare la parola nel messaggio di errore. |
| Nuova password con meno di 12 caratteri | PASSPHRASE_REQUIRED: Use a passphrase of at least 12 characters. |
| Rientro dopo background e riapertura del wallet | Dopo ConfirmBackup, indirizzo e saldo corretti. Non è noto se Home sia stato premuto prima o dopo WalletRestored. |
| Invio dei fondi dall'installazione ripristinata | Stato iniziale LOCAL_UNCONFIRMED, altezza -1; fondi ricevuti nel wallet destinatario; successivamente stato CONFIRMED. Altezza finale non comunicata. |
| Ritrasmissione mentre il pagamento era in attesa di conferma | L'utente ha visto il dialogo Ritrasmetti e lo ha confermato; idem invariato prima della conferma, poi stato CONFIRMED. Il wallet destinatario mostra un solo movimento. |
| Ritrasmissione di un pagamento già confermato | Rifiuto con ALREADY_CONFIRMED, come atteso. |

L'invio dall'app ripristinata verifica anche l'utilizzo effettivo delle chiavi
recuperate nella prova testnet. La ricezione e la successiva conferma sono esiti
distinti dalla sola registrazione locale LOCAL_UNCONFIRMED; questa, da sola,
non dimostra accettazione in rete.

La prova manuale della ritrasmissione conferma, per il caso testnet osservato,
stesso identificativo e un solo movimento al destinatario. Non viene interpretata
come garanzia generale di esito di rete, irreversibilità o recupero di ogni errore.

Si adotta la 0.0.10 come baseline funzionale NEXATESTNET per i casi verificati.
I risultati manuali non sono prove strumentate eseguite dalla cloud, un audit
di sicurezza o una garanzia per tutti i dispositivi. Modello del telefono,
versione Android e servizio di compilazione APK non sono stati specificati per
questo collaudo e non vengono dedotti dai test precedenti.
Nessuna frase personale ricevuta e nessuna transazione inviata dalla cloud.

## Verifiche manuali ancora aperte

Il passaggio in background precisamente durante il recupero non è confermato:
l'utente non era sicuro che Home fosse stato premuto prima di WalletRestored.
Si registra quindi solo riapertura dopo background riuscita, senza attribuire
alla prova una tempistica non accertata. L'interruzione durante il recupero
resta coperta dai test automatici del controller, non confermata sul dispositivo.

Arresto forzato del processo e gestione di database parziale/journal orfano non
sono stati confermati manualmente. Procedura e limiti noti in RESTORE-TEST.md.
La nuova prova di spesa dalla 0.0.10 è distinta dalle prove di invio della 0.0.9.

Il collaudo manuale della baseline 0.0.9 è conservato in history/VALIDATION-0.0.9.md
insieme alla sua provenienza storica. Non viene attribuito alla nuova candidata.

La funzione ufficiale di validazione non ancora citata nei Nexa AI Skills è
l'eccezione specifica autorizzata dall'utente il 2026-11-12. Non sono introdotte
altre eccezioni, librerie o API di importazione di chiavi private.
Piano, fonti e limiti del ripristino: ../docs/RESTORE-WALLET-DESIGN.md.
