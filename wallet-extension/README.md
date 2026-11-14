# Candidata testnet 0.0.11: wallet in noBackupFilesDir

**Solo app testnet separata, senza wallet precedente. Non aggiornare l'app principale.**
La 0.0.10 resta la baseline collaudata. Questa candidata conserva le firme dei
blocchi e il nome logico del wallet, ma cambia l'archiviazione: database/sidecar,
conferma backup e journal dei pagamenti sotto la directory privata no_backup.
Rifiuta database o preferenze legacy; nessuna migrazione, reset o sostituzione.

SDK0.7.5/JNI e 53 dipendenze invariati, minSdk29. Non richiede modifiche al server
MIT o nuovi permessi. La collocazione e il backup effettivo devono ancora essere
verificati sul telefono; non copre tutti i file SDK/app o ogni trasferimento OEM.

Nuovi blocchi: GetStorageStatus() → StorageStatus(report), JSON senza percorsi
personali o segreti. Leggere [NO-BACKUP-TEST.md](NO-BACKUP-TEST.md) prima della prova.
Protocollo e fonti in ../docs/NO-BACKUP-CANDIDATE-DESIGN.md e
../docs/NO-BACKUP-STORAGE-STUDY.md. Esiti automatici in VALIDATION.md;
collaudo precedente conservato in history/VALIDATION-0.0.10.md.

## Ripristino aggiunto nella 0.0.10

`RestoreWallet(recoveryPhrase, passphrase)` e `WalletRestored()` recuperano una
frase valida di 12 parole solo se non esiste già un wallet. Nessun reset.
La password cifra la nuova copia locale. Attendere sync prima di verificare fondi.
Istruzioni: [prova separata](RESTORE-TEST.md). Piano e fonti: ../docs/RESTORE-WALLET-DESIGN.md.
Validatore SDK ufficiale bip39ProcessRecoverySecret autorizzato come eccezione
ai Nexa AI Skills dall’utente il 2026-11-12; sorgente ufficiale bip39.kt, release0.7.5.

Collaudo manuale confermato dall'utente: recupero dei fondi precedenti dopo sync,
riapertura con nuova password, rifiuto di wallet già presente con saldo corretto
e rifiuto della frase incompleta, del checksum errato, della parola fuori
dizionario e della password troppo corta. Verificati anche riapertura dopo
background e invio dei fondi recuperati, ricevuti dal destinatario e poi CONFIRMED.
La tempistica del background durante il recupero non è stata accertata.
Riepilogo della baseline aggiornato al 2026-11-13 in
[history/VALIDATION-0.0.10.md](history/VALIDATION-0.0.10.md), con verifiche automatiche e casi ancora aperti.
Verificata anche la ritrasmissione in attesa di conferma: stesso idem, un solo
movimento al destinatario e successivo CONFIRMED; ALREADY_CONFIRMED su pagamento
già confermato. Guida ai blocchi: [APP-INVENTOR-GUIDE.md](APP-INVENTOR-GUIDE.md).
English guide: [APP-INVENTOR-GUIDE.en.md](APP-INVENTOR-GUIDE.en.md).

## Blocchi di invio già presenti

- `SendNexa(address, amountNexa, maxFeeNexa, passphrase)`: prepara e firma una
  transazione, riblocca le chiavi e mostra un dialogo Android protetto. Solo il
  pulsante **Invia** nel dialogo autorizza il pagamento. Gli importi sono testo
  in NEXA, punto decimale e massimo due decimali; niente conversioni Double.
  `maxFeeNexa` è il limite totale di commissione accettabile, non una tariffa.
- `CancelSendNexa()`: annulla esclusivamente la bozza prima della conferma.
- `GetLastSendStatus()`: legge l'ultimo pagamento approvato e la storia SDK.
- `RetryLastSendNexa()`: propone una nuova conferma nativa per ritrasmettere
  esattamente i byte precedentemente approvati, senza ricostruire il pagamento.
- `SendInProgress`: preparazione, anteprima o chiamata SDK in corso. Per un
  pagamento precedente irrisolto usare GetLastSendStatus.

Eventi: `SendNexaPrepared(requestId,address,amount,fee,total)` è informativo;
`SendNexaSubmitted(idem)` indica solo la richiesta all'SDK;
`SendNexaCancelled(requestId,reason)` segnala annullamento/scadenza;
`SendNexaStatus(idem,state,confirmedHeight)` riporta lo stato.
Gli errori passano attraverso `WalletFailed(operation,code,message)`.

## Prova sul telefono

1. Importare il nuovo AIX in un progetto separato senza wallet precedente,
   compilare e installare un APK distinto senza toccare l’app principale. Per questa prova usare
   un wallet testnet con fondi di prova e backup verificato.
2. Verificare `LibNexaVersion = 0.7.5`, aprire il wallet e attendere saldo
   sincronizzato e connessione. Sono utilizzabili solo input con almeno una
   conferma, come scelta prudenziale dell'estensione.
3. Aggiungere campi per indirizzo NEXATESTNET, importo e limite commissione
   (entrambi testo), oltre al campo password già esistente. Non salvare password
   o frase in TinyDB, etichette persistenti, log o servizi esterni.
4. Chiamare SendNexa con un piccolo importo e un limite commissione scelto
   consapevolmente. Leggere indirizzo completo, importo, commissione reale e
   totale nel dialogo. Nella prima prova scegliere **Annulla** e verificare
   l'evento USER_CANCELLED, senza invio.
5. Ripetere e confermare **Invia** solo dopo aver verificato il destinatario.
   Registrare privatamente l'idem pubblico restituito, poi richiamare
   GetLastSendStatus e verificare anche il wallet destinatario.
6. `LOCAL_UNCONFIRMED` può provenire dall'inserimento locale dell'SDK e non
   dimostra accettazione in rete. `CONFIRMED` con altezza >=0 deriva dalla
   storia SPV dell'SDK, senza garanzia di finalità irreversibile.
7. Chiudere e riaprire l'app, riaprire il wallet e verificare nuovamente stato,
   indirizzo e saldo. In caso di esito incerto non creare un altro pagamento:
   leggere lo stato; se necessario RetryLastSendNexa ritrasmette lo stesso.

L'utente ha confermato sul telefono invio e ricezione testnet, stato CONFIRMED
nel controllo dopo riavvio, rifiuto di password errata/rete sbagliata/fondi
insufficienti, annullamento, scadenza e recupero della bozza dopo background.
Il saldo è risultato corretto nelle ultime prove. Esiti e limiti del collaudo
manuale, distinti dai 79 test del controller, sono registrati in history/VALIDATION-0.0.9.md
(aggiornamento 2026-11-12).

## Stati ed errori

`NO_SEND`: nessuna autorizzazione salvata. `APPROVED`: autorizzazione persistita,
possibile interruzione prima/durante la consegna all'SDK. `SDK_SUBMITTED`: chiamata
SDK terminata senza eccezioni, senza ACK remoto. `UNKNOWN`: errore dopo possibile
consegna all'SDK. `LOCAL_UNCONFIRMED`: storia locale senza conferma.
`CONFIRMED`: altezza SDK >=0. `REMOVED_OR_INVALID`: sentinella SDK Long.MIN_VALUE.

Un solo pagamento irrisolto blocca nuovi pagamenti (`SEND_PENDING`), anche dopo
riavvio. Non si sblocca automaticamente se respinto o rimosso; serve diagnosi.
Nessun blocco cancella una transazione già approvata o aumenta la commissione.
Il journal contiene byte firmati e metadati pubblici nel file privato sotto
no_backup, senza password, seed o chiavi private. Il checksum rileva corruzione
accidentale, non un attaccante con accesso al processo.

Prima del consenso, annullamento, scadenza dopo due minuti o background rilasciano
solo la bozza corrente tramite abortTransaction. Dopo il consenso nessuna pulizia
automatica libera gli input. Un arresto forzato prima del consenso può lasciare
prenotazioni SDK: la candidata non implementa un recupero mirato dopo tale crash.
Non usare cleanReserved/cleanUnconfirmed indiscriminatamente, non cancellare il
database e non ricreare il wallet per risolverlo. `SEND_CLEANUP_FAILED` richiede
diagnosi prima di riprovare; il riavvio non prova che le prenotazioni siano libere.

## Fonti Nexa

Corpus Nexa AI Skills commit 3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24:

| API / regola | File sorgente nel corpus |
| --- | --- |
| 1 NEXA = 100 sat; Long | skills/nexa-transaction-construction/SKILL.md, mental model |
| PayAddress, type.isValid, chainSelector, lockingScript | skills/nexa-identity-and-addresses/validateHelpers.kt |
| txFor, txOutputFor, FUND_NATIVE, SIGN, txCompleter, send, abortTransaction | skills/nexa-transaction-construction/SKILL.md, pattern 6 |
| Parametri completi txCompleter e minConfirms | skills/nexa-transaction-construction/txCompletionReference.md |
| dust(chain), txOutputFor | skills/nexa-transaction-construction/simpleapi-cheatsheet.md |
| txFor con BCHserialized/NETWORK | skills/nexa-transaction-construction/SKILL.md, pattern 5 |
| getTx, confirmedHeight | skills/nexa-transaction-construction/SKILL.md, pattern 4 |
| broadcastTransaction(byte[]) | skills/nexa-transaction-construction/SKILL.md, pattern 3 |

Firme Java controllate compilando contro 0.7.5. La mancanza di ACK remoto è
verificata anche nell'implementazione ufficiale wallet.kt/cnxnmgr.kt del tag.
Conferma nativa, journal, checksum, scadenza, limite commissione e blocco di
pagamenti successivi sono regole applicative, non API o garanzie Nexa.
Architettura e piano precedenti al codice: ../docs/SEND-NEXA-DESIGN.md.

## Ricostruzione

Rush, JDK21 e dipendenze congelate. Per ricostruire le dipendenze SDK vedere
../packaging-probe/sdk-upgrade: non sostituire gli AAR con versioni Maven diverse.
Con una directory runtime già preparata:

```sh
python3 tools/restore_deps.py /percorso/dipendenze-congelate
tools/test_wallet_session.sh
python3 tools/test_recovery_sdk.py
JSON_JAR=/percorso/appinventor/lib/json/json.jar bash tools/test_no_backup_store.sh
rush build
python3 tools/compat_aix.py
python3 tools/inspect_aix.py out/NexaPackagingProbe-0.0.11.aix \
  --baseline-aix ../dist/NexaPackagingProbe-0.0.10.aix \
  --jni-aar deps/libnexajni-release.aar
```

Le dipendenze e gli output intermedi sono ignorati da Git. Il modulo precedente
packaging-probe conserva il suo profilo di ricostruzione storico.
