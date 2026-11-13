# RestoreWallet: architettura e piano prima del codice

Data: 2026-11-12. Baseline testnet 0.0.9; candidata prevista 0.0.10.
SDK pubblicato 0.7.5, commit 914879c7574b7d785127206667c96d474b81395a.
Corpus Nexa AI Skills: 3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24.

## Contratto proposto

`RestoreWallet(recoveryPhrase, passphrase)` ripristina un wallet NEXATESTNET da
una frase di 12 parole inglesi, come quelle create dall'estensione. Passphrase
nuova di almeno 12 caratteri: cifra il wallet locale, non cambia la frase o le
chiavi del wallet originario. Non supporta una passphrase BIP39 aggiuntiva,
singole chiavi WIF/hex, formati di altri wallet o selezione della derivazione.

Solo in assenza del database attuale e di un handle già aperto. Se esiste un
wallet, errore WALLET_EXISTS, anche se è vuoto o non si apre: nessuna sostituzione,
eliminazione o creazione alternativa. Nessun blocco di reset è introdotto.

Evento proposto `WalletRestored()` privo di segreti: significa recupero locale,
cifratura, salvataggio e blocco verificato completati; non sincronizzazione
completata né saldo recuperato. GetBalance mantiene lo stato di sincronizzazione.
ConfirmBackup resta esplicito per abilitare indirizzo e invii. FastSync rimane
manuale con i suoi limiti di fiducia documentati; nessun avvio automatico.

## Fonti e punto da autorizzare

Percorsi della fonte primaria https://gitlab.com/nexa/nexaaiskills:

| API o regola | Fonte |
| --- | --- |
| recoverWallet(name, phrase, ChainSelector.NEXATESTNET) | skills/nexa-wallet-lifecycle-and-chain/SKILL.md, pattern 2 |
| Recupero con scansione storica, diverso da newWallet | stessa skill, mental model; recoveryFlow.md, Restore flow |
| encrypt, saveBip44Wallet, lock, lockedState | stessa skill, pattern 6; recoveryFlow.md, create/lock/restore |
| FastSync opzionale e fiducia Electrum | stessa skill, pattern 4; recoveryFlow.md |
| Verificare frase prima del recupero | stessa skill, pattern 2: correct word count, single-space separated |

Il recupero ufficiale 0.7.5 in init.kt e il costruttore Bip44Wallet in wallet.kt
derivano il seed dalla frase ricevuta senza verificarne parole e checksum.
Una frase errata non deve essere accettata silenziosamente come recupero riuscito.

La funzione ufficiale `bip39ProcessRecoverySecret(recoverySecret, true)` in
libnexakotlin/src/commonMain/kotlin/bip39.kt verifica 12 parole, dizionario e
checksum, normalizza spazi/maiuscole e restituisce la frase canonica. La firma
Java è stata verificata con javap sullo stesso AAR usato dall'estensione:
`Bip39Kt.bip39ProcessRecoverySecret(String, boolean)`.

Eccezione autorizzata dall’utente nella conversazione il 2026-11-12, limitatamente
a questa funzione ufficiale. Questa funzione non compare nel corpus Skills corrente. È un'API ufficiale
esistente, non inventata, ma usarla richiede un'eccezione esplicita al vincolo
dell'utente: «Utilizza esclusivamente le API, le classi e i pattern documentati
nei Nexa AI Skills». Può essere usata nell’implementazione secondo l’eccezione autorizzata. Alternativa: attendere
che il flusso di validazione sia documentato nei Skills. Non implementare un
algoritmo blockchain alternativo o accettare frasi senza controllo per aggirare
il vincolo. Eventuali errori della funzione non vanno inoltrati testualmente:
possono includere parole della frase. Usare INVALID_RECOVERY_PHRASE generico.

## Librerie e architettura

Nessuna dipendenza aggiuntiva: stesso grafo di 53 artefatti SDK 0.7.5, JNI
abbinato, minSdk 29, toolchain Rush/JDK21. Nessun nuovo permesso Android.

- Componente App Inventor: nuovo metodo/evento, gestione asincrona, niente frase
  in eventi, log o dati applicativi persistenti.
- GuardedRequest: custodisce separatamente frase e password per la richiesta
  in coda, rilascia entrambi su annullamento/completamento senza catturarli nelle
  lambda. Le String Java/App Inventor non offrono cancellazione certa in memoria.
- WalletSession: esegue controlli, serializza il recupero, verifica revoca,
  gestisce cifratura/salvataggio/blocco prima di pubblicare il risultato.
- NexaWalletStorage: esegue validazione SDK autorizzata prima di modificare
  metadati o database; ricontrolla l'assenza del database prima di recoverWallet.
- Metadati: backup non confermato inizialmente; presenza di un journal di invio
  preesistente senza database richiede diagnosi, non cancellazione automatica.

## Successione prevista

1. Verificare sessione in primo piano, assenza di FastSync/bozze, database e
   journal di pagamento incompatibile; validare nuova password e frase.
2. Inizializzare lo stato di backup, chiamare recoverWallet sul worker seriale.
3. Al ritorno cifrare e salvare immediatamente, anche se la UI è stata revocata
   durante la chiamata. Non interrompere tale completamento per un cambio Form.
4. Se ancora autorizzati, ottenere l'indirizzo pubblico per la sessione;
   ribloccare le chiavi e verificare lockedState prima di WalletRestored.
5. Se la richiesta è revocata, non emettere il risultato e non aprire la
   sessione; al ritorno usare OpenWallet con la nuova password.
6. Errori dopo la creazione lasciano il database al suo posto; nessun fallback
   di creazione/ripristino, cancellazione o tentativo automatico successivo.

L'SDK può scrivere prima che recoverWallet ritorni. Un arresto forzato in quella
finestra non è reso atomico da questo piano; non si promette recupero automatico
di un database parziale. Il tema della prima scrittura non cifrata era già stato
escluso dall'utente dai blocchi al progetto; resta un limite documentato.

## Piano e verifiche

1. Risolvere l'eccezione sulla funzione ufficiale di validazione.
2. Implementare controller e custodia dei due segreti, poi adapter e blocchi.
3. Testare rifiuto di database esistente senza modifiche, frase/password errate
   prima della creazione, ordine recupero/cifratura/salvataggio/blocco, errori,
   revoca durante recupero, assenza di eventi con segreti, rilascio della coda,
   backup esplicito, journal orfano e riapertura con nuova password.
4. Verificare il validatore ufficiale con sole frasi pubbliche di test: formato,
   parole sconosciute e checksum errato, senza log della frase.
5. Eseguire regressioni dei 79 test, build Rush/D8, verifica firme precedenti,
   nuove annotazioni, ZIP/JNI e limite MIT per entry; registrare provenienza.
6. Collaudare in un progetto App Inventor separato, con package/dati distinti:
   creare un wallet testnet di prova, conservare privatamente la frase, ricevere
   pochi fondi e ripristinare su un'app di prova diversa. Non cancellare i dati
   dell'app principale. Attendere sync e confrontare fondi/spendibilità; non
   presumere che l'indirizzo corrente coincida con quello ruotato nell'originale.
7. Provare riavvio e protezione di un wallet già presente. Solo dopo la build
   concreta predisporre l'artefatto per il download con il flusso concordato.

Stato: analisi e piano completati; eccezione autorizzata. Implementazione
della candidata 0.0.10 compilata e verificata staticamente; pacchetto 0.0.9 conservato.
Aggiornamento 2026-11-13: l'utente ha confermato sul telefono recupero dei fondi
storici dopo sync, riapertura con nuova password, rifiuto del wallet già presente
con saldo corretto e rifiuto della frase incompleta. La 0.0.10 è adottata come
baseline funzionale testnet per questi casi; esiti e verifiche ancora aperte in
../wallet-extension/VALIDATION.md. Nessuna nuova build richiesta dall'aggiornamento
del riepilogo. Ulteriori esiti riferiti dall'utente: rifiuto di checksum errato,
parola fuori dizionario e password troppo corta; riapertura dopo background
con indirizzo/saldo corretti; spesa dei fondi recuperati, ricevuti dal destinatario
e successivamente CONFIRMED. Non è accertato che il background sia avvenuto
durante recoverWallet: questo scenario resta aperto sul dispositivo.
