# Verifica della versione 0.0.9

Data: 2026-11-07. SDK 0.7.5 dal tag ufficiale, stesso grafo di 53 dipendenze
della 0.0.8. Versioni, commit, hash dei sorgenti Java e SHA256 AIX registrati in
SDK-PROVENANCE-0.0.9.json. Artefatto finale: ../../dist/NexaPackagingProbe-0.0.9.aix.

Controlli completati:

- 49 test di regressione del controller più 30 test di invio: superati.
- Build Java/Rush 2.1.2, riduzione librerie, desugaring, D8 e AIX: riuscita.
- Verifica hash di tutte le 53 dipendenze prima della distribuzione locale.
- Confronto con AIX 0.0.8: tipo e firme precedenti invariati; aggiunti soltanto
  quattro metodi, quattro eventi e la proprietà SendInProgress.
- ZIP integro; tutte le entry sotto 5 MiB (massima: 3.642.622 byte).
- DEX, manifest Startup e librerie registrate presenti; nomi JNI originali
  conservati; otto .so identici alla distribuzione SDK abbinata, quattro ABI.
- Parser Java ZipInputStream e org.json del checkout MIT: riusciti.
- Il riconfezionamento verifica che DEX, payload runtime e binari nativi non
  siano modificati dalla divisione dei JAR per il limite MIT.

I test di invio impiegano un backend fittizio: importi esatti e overflow,
autenticazione, backup, sincronizzazione, limite commissione, annullamento,
scadenza monotona, timer obsoleto, revoca durante preparazione, persistenza prima
della chiamata SDK, errore di scrittura, doppia conferma, esito incerto,
background dopo consegna, riavvio e ritrasmissione degli stessi byte, stati
confermato/non confermato/rimosso, corruzione record e fallimento della pulizia.

## Collaudo manuale riferito dall'utente

Riepilogo aggiornato il 2026-11-12 sulla base delle conferme nella conversazione.
Estensione 0.0.9, SDK 0.7.5, rete NEXATESTNET. Le prove sono state eseguite
dall'utente sul telefono; non sono test strumentati eseguiti dalla cloud.

| Prova | Esito riferito |
| --- | --- |
| Invio con SendNexa | Nuova versione funzionante; fondi ricevuti anche nel wallet destinatario. |
| GetLastSendStatus nel controllo richiesto dopo riavvio | `CONFIRMED`, altezza `1201833`. |
| Password errata | `SendNexa - INVALID_PASSPHRASE - The passphrase is incorrect or the wallet is not encrypted`. |
| Annullamento nel dialogo | Confermato l'esito atteso `SendNexaCancelled`, `USER_CANCELLED`. |
| Indirizzo valido della rete mainnet | Confermato il rifiuto atteso `WRONG_NETWORK`. |
| Importo superiore al saldo | `WalletNotEnoughBalanceException`. |
| Nuova bozza dopo fondi insufficienti | Preparazione e annullamento riusciti. |
| Limite commissione 0,01 NEXA per invio di 100 NEXA | FEE_LIMIT_EXCEEDED; nuova bozza con limite abituale preparabile e annullabile, saldo corretto. |
| Password corretta dopo password errata, senza riaprire | Nuova bozza preparabile e annullabile, saldo corretto. |
| Dialogo lasciato aperto oltre due minuti | Restituito `EXPIRED`; successiva preparazione e annullamento riusciti, saldo corretto. |
| Home prima della conferma, ritorno e riapertura wallet | Nuova bozza preparabile e annullabile, saldo corretto. |

L'altezza di conferma è un dato storico di quella prova, non un'altezza attuale.
Non sono stati raccolti seed, password, database o transazioni firmate dell'utente.
Questi risultati verificano i casi elencati sul dispositivo utilizzato; non
costituiscono un audit di sicurezza o una garanzia per tutti i dispositivi Android.

Restano senza conferma manuale: ritrasmissione con RetryLastSendNexa, esito
incerto durante consegna all'SDK, record corrotto
e arresto forzato del processo con bozza aperta. Per i limiti noti vedere README.
Il passaggio in background non equivale all'arresto forzato del processo.

Nessuna transazione è stata inviata dalla cloud. Il servizio di compilazione APK
utilizzato non è stato identificato nel resoconto e non viene attribuito al MIT.
Il collaudo dell'utente sulla 0.0.8 è registrato separatamente in
../../docs/SDK-0.7.5-USER-VALIDATION.md e non viene attribuito alla 0.0.9.

Pubblicazione sul ramo GitHub dedicato autorizzata dall’utente dopo la verifica
locale. Configurazione cloud invariata; rapporti privati esclusi dagli artefatti.
Istruzioni di prova, fonti delle API e limiti residui nel README del modulo.
