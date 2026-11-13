# Prova RestoreWallet — 0.0.10

Solo NEXATESTNET. La 0.0.10 è la baseline funzionale per i casi confermati
dall'utente; riepilogo del collaudo e casi aperti in VALIDATION.md. Questa versione aggiunge
`RestoreWallet(recoveryPhrase, passphrase)` e l'evento `WalletRestored()`.
Richiede 12 parole inglesi valide; la password è la nuova password locale di
almeno 12 caratteri, non una passphrase BIP39 aggiuntiva.

## Preparare l'app di prova

1. Conservare l'app principale e i suoi dati. Creare un progetto App Inventor
   **separato con un nome diverso**, importare l'AIX 0.0.10 e compilare un APK
   installato come app distinta. Per questa prova usare l'APK, non Companion,
   che può condividere i dati fra progetti.
2. Nell'app di prova predisporre Initialize, RestoreWallet, ConfirmBackup,
   OpenWallet, GetAddress, GetBalance e, se desiderato, FastSync. Usare campi
   protetti per frase/password, non TinyDB, log o servizi esterni. Dopo la
   chiamata cancellare il testo dei campi. Non incollare la frase in chat.
3. La frase deve provenire da un wallet testnet usa-e-getta creato con
   l'estensione. Se non esiste già tale backup, crearlo in una seconda app di
   prova con package distinto. Conservare la frase privatamente e trasferire
   pochi fondi testnet per poter verificare il recupero dei fondi storici.

## Prima prova: controllare il rifiuto della frase errata

Nell'app nuova senza wallet, chiamare RestoreWallet con una frase palesemente
incompleta e una password di almeno 12 caratteri. Attendere WalletFailed con
operation RestoreWallet e code INVALID_RECOVERY_PHRASE. Non deve essere emesso
WalletRestored né creato un wallet. Non usare mai frasi pubbliche di esempio per
ricevere fondi, neppure quando superano il checksum.

## Recupero con il backup corretto

1. Chiamare Initialize e attendere InitializationSucceeded.
2. Chiamare RestoreWallet con le 12 parole private del wallet di prova e la
   nuova password scelta per questa installazione. Può differire da quella
   dell'app originale: protegge il database locale e non cambia le chiavi.
3. Attendere WalletRestored. Significa recupero locale, cifratura, salvataggio
   e blocco delle chiavi completati, **non** saldo sincronizzato.
4. Chiamare ConfirmBackup, poi GetAddress e GetBalance. Prima del completamento
   della sincronizzazione un saldo basso/zero non prova un recupero fallito.
5. Attendere synced true. FastSync è opzionale e manuale: usa Electrum con il
   compromesso di fiducia già documentato. Non viene avviato automaticamente.
6. Verificare il saldo atteso e che i fondi ricevuti prima del recupero siano
   riconosciuti. L'indirizzo corrente può differire da quello ruotato nell'app
   originale; non usare questa differenza da sola per diagnosticare un errore.
7. Chiudere completamente e riaprire l'app di prova. Usare OpenWallet con la
   nuova password, non RestoreWallet. Verificare di nuovo saldo e stato sync.
8. Con il wallet già presente, chiamare RestoreWallet: deve restituire
   WALLET_EXISTS senza sostituire il wallet. Riaprirlo e verificarne i dati.

Il recupero da frase non ripristina metadati App Inventor, conferma del backup,
journal degli invii della precedente app o password del database originale.
In particolare non assumere che la protezione locale contro un secondo invio
conosca pagamenti approvati su un'altra installazione: verificarne prima l'esito.
Non usare contemporaneamente le due app per inviare gli stessi fondi.

## Limiti della candidata

Nessun reset o sovrascrittura del database. File SQLite parziali o journal di
invio senza database bloccano il recupero e richiedono diagnosi. Errori dopo
l'avvio del recupero conservano gli eventuali file creati; non viene fatto un
nuovo tentativo automatico. Un arresto forzato durante la prima scrittura SDK
può lasciare un database parziale/non ancora cifrato: questa build non rende
atomico tale intervallo. Non cancellare i dati dell'app principale per aggirarlo.

Il checksum rileva errori della frase, non certifica identità, provenienza o
appartenenza al wallet desiderato. Le String in Java/App Inventor non consentono
di garantire la cancellazione del segreto dalla memoria. Il recupero effettivo
dei fondi e la riapertura sono stati confermati dall'utente con la 0.0.10;
gli altri casi manuali ancora aperti sono elencati in VALIDATION.md.
