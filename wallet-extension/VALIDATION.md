# Verifica candidata locale 0.0.9

Data: 2026-11-07. SDK 0.7.5 dal tag ufficiale, stesso grafo di 53 dipendenze
della 0.0.8. Versioni, commit, hash dei sorgenti Java e SHA256 AIX registrati in
SDK-PROVENANCE.json. Artefatto finale: ../dist/NexaPackagingProbe-0.0.9.aix.

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

Non eseguiti dalla cloud: compilazione APK sul servizio MIT con la 0.0.9,
esecuzione del dialogo Android, costruzione/firma SDK con fondi reali testnet,
propagazione e conferma in rete. Nessuna transazione è stata inviata dalla cloud.
Il collaudo dell'utente sulla 0.0.8 è registrato separatamente in
../docs/SDK-0.7.5-USER-VALIDATION.md e non viene attribuito alla 0.0.9.

Pubblicazione sul ramo GitHub dedicato autorizzata dall’utente dopo la verifica
locale. Configurazione cloud invariata; rapporti privati esclusi dagli artefatti.
Istruzioni di prova, fonti delle API e limiti residui nel README del modulo.
