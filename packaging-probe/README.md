# NexaPackagingProbe 0.0.5 — wallet testnet sperimentale

Estensione Java per MIT App Inventor 2 con Nexa Android 0.5.69. Il nome del
componente resta NexaPackagingProbe per aggiornare i blocchi delle versioni precedenti.
Solo testnet; nessuna funzione mainnet, invio o importazione chiave privata.

## Blocchi disponibili

| Funzione | Risultato asincrono |
| --- | --- |
| Initialize() | InitializationSucceeded oppure InitializationFailed(code, message) |
| CreateWallet(passphrase) | WalletCreated(recoveryPhrase), solo se non esiste già un DB |
| OpenWallet(passphrase) | WalletOpened; non crea mai un nuovo wallet |
| ConfirmBackup() | BackupConfirmed; registra la conferma dell'utente |
| GetAddress() | AddressReceived(address), dopo creazione/apertura e conferma backup |
| GetBalance() | BalanceReceived(total, confirmed, unconfirmed, synced, connected, syncedHeight, chainHeight) |
| FastSync(passphrase) | FastSyncProgress e FastSyncFinished; errori in WalletFailed |
| CancelFastSync() | Richiesta di arresto cooperativo, non completamento immediato |

Gli errori wallet arrivano a WalletFailed(operation, code, message). Ogni richiesta
viene serializzata su un worker; gli eventi sono emessi sul thread UI.
Le funzioni wallet inizializzano automaticamente la libreria se necessario.

Proprietà di sola lettura: Initialized, Network (sempre `testnet`), WalletOpen,
FastSyncRunning.
WalletOpen significa che l'indirizzo pubblico è stato preparato in questo processo:
non implica chiavi sbloccate o sincronizzazione della blockchain completata.

## Prima creazione sul dispositivo

1. Aggiornare l'estensione con `NexaPackagingProbe-0.0.5.aix`.
2. Aggiungere un PasswordTextBox, una Label privata per la frase, una Label per
   l'indirizzo e pulsanti Crea, Conferma backup e Indirizzo.
3. Da Crea.Click chiamare CreateWallet con il testo della PasswordTextBox.
   La passphrase deve avere almeno 12 caratteri: è una regola di questa estensione,
   non una pretesa regola Nexa. Non inserirla come testo fisso nei blocchi.
4. In WalletCreated mostrare recoveryPhrase sul dispositivo e registrarla
   privatamente. Non inviarla in chat, non salvarla in TinyDB, non registrarla nei
   log. La funzione non conserva una copia della frase per mostrarla di nuovo.
5. Solo dopo aver registrato la frase, chiamare ConfirmBackup. L'estensione salva
   una conferma booleana: non verifica se la frase è stata trascritta correttamente.
6. In BackupConfirmed cancellare la Label della frase e il testo della password,
   quindi chiamare GetAddress. In AddressReceived mostrare address.
7. In WalletFailed mostrare operation, code e message per la diagnosi.

Il wallet viene cifrato e salvato prima dell'evento WalletCreated. Le chiavi sono
ribloccate dopo il calcolo dell'indirizzo. Una finestra di creazione non cifrata
esiste all'interno del flusso SDK prima di encrypt; un arresto o errore in quella
fase richiede diagnosi e non provoca una ricreazione automatica al tentativo dopo.
Per questa prova non servono fondi. Le build di test non sono wallet di produzione.

## Riapertura e prova di persistenza

Non disinstallare l'app e non cancellarne i dati durante la prova.

1. Annotare l'indirizzo pubblico mostrato dopo la creazione.
2. Terminare e riaprire lo stesso APK.
3. Inserire la stessa passphrase e chiamare OpenWallet.
4. In WalletOpened chiamare GetAddress. Se il backup era confermato, la conferma
   resta salvata. Il nuovo indirizzo deve corrispondere a quello precedente se
   non ci sono state operazioni che lo abbiano fatto ruotare.
5. Provare anche una passphrase errata: deve arrivare INVALID_PASSPHRASE senza
   sostituire il database. Riprovare con quella corretta.

Il nome fisso del DB è `nexa_ai2_testnet_v1.db`, nello spazio privato dell'app.
CreateWallet su un DB già presente emette WALLET_EXISTS: usare OpenWallet.
Un DB incompleto/corrotto non viene cancellato o sovrascritto dall'estensione.
GetAddress usa l'indirizzo pubblico acquisito all'ultima creazione/apertura;
non chiama getNewAddress e non chiede di ruotarlo a ogni pressione del pulsante.
newWallet/openWallet avviano la connessione blockchain secondo il flusso SDK:
il completamento dell'evento non dimostra che il wallet sia sincronizzato.

## API Nexa e fonti primarie

Corpus fissato al commit `3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24`:

- [Wallet lifecycle, Setup e pattern 1/3/4/6](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-wallet-lifecycle-and-chain/SKILL.md):
  initializeLibNexa, newWallet, openWallet, ChainSelector.NEXATESTNET,
  Bip44Wallet, recoverySecret, getCurrentDestination().address, encrypt, unlock, lock.
- [Create/backup/restore checklist](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-wallet-lifecycle-and-chain/recoveryFlow.md):
  ordine backup/cifratura e saveBip44Wallet, Create flow step 6.
- [Template Android startup](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-wallet-lifecycle-and-chain/walletStartupTemplate.kt):
  controllo getDatabasePath prima di aprire il DB, nessuna ricreazione da catch generico.
- [Validazione indirizzi](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-identity-and-addresses/validateHelpers.kt):
  PayAddress.chainSelector confrontato con la rete attesa.
- [Server integration](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-ktor-server-integration/SKILL.md):
  esempio di conversione PayAddress.toString(). Nessun modulo server viene aggiunto.

Le firme Java sono verificate con javap sugli artefatti ufficiali 0.5.69:
LibnexaandroidKt.initializeLibNexa(String), InitKt.newWallet(String, ChainSelector,
Function1), InitKt.openWallet(String, Function1), getRecoverySecret(),
saveBip44Wallet(), encrypt(String), unlock(String), lock(),
getCurrentDestination().getAddress(), PayAddress.getChainSelector().
Non sono usati metodi privati JNI, setter di contesto extra-corpus o API inventate.
Il provider Startup nel manifest fornisce l'inizializzazione Android prevista
nell'artefatto: il suo funzionamento col database resta da verificare nel nuovo APK.

## Ricompilazione

Requisiti: JDK 21 completo, Gradle 8.14.3, Rush 2.1.2 e Python 3.
Rush v206 usa compile SDK 36; min_sdk=29 (Android 10).

1. In dependency-resolution/: `gradle --no-daemon resolveAndroidGraph`.
   Lock e verifica checksum fissano il grafo di 47 artefatti, copiati in ../deps/.
   Il lock include l'alias metadata kotlin-stdlib-common richiesto da Gradle.
2. In questa directory: `rush deps sync` e `rush build`.
3. `python3 tools/compat_aix.py`
4. `python3 tools/inspect_aix.py out/NexaPackagingProbe-0.0.5.aix`

Usare proxy e CA della sessione. Non disattivare TLS o controlli di integrità.
original_names preserva i nomi Nexa richiesti da JNI. Il postprocessore mantiene
ogni file interno sotto 5 MiB per i server MIT precedenti alla correzione
b097193903890d6aa729ac52464ae342d3a600e2. Mantiene i payload delle classi, DEX e
librerie native invariati, registrando i JAR aggiuntivi nei build metadata MIT.
La 0.0.2 è stata importata con successo e l'utente ha confermato creazione, indirizzo
e riapertura della 0.0.3 sullo smartphone. La lettura saldo della 0.0.4 richiede
nuova compilazione APK e prova dispositivo. Non usare il Companion con AAR.

## Verifiche automatizzate del ciclo di vita

Compilare WalletSession.java, WalletBalance.java e tests/org/nexa/appinventor/probe/WalletSessionTest.java
con javac --release 17 in una directory esterna, poi eseguire
org.nexa.appinventor.probe.WalletSessionTest. I test usano backend finti per provare
le regole di non sovrascrittura, password, backup e blocco chiavi. Non simulano
la correttezza del database Nexa, la cifratura effettiva o la rete Android.


## Saldo e sincronizzazione (0.0.4)

Aggiornare l'estensione nel progetto esistente e ricompilare/installare l'APK come
aggiornamento: non disinstallare e non cancellare i dati. Usare OpenWallet con la
passphrase esistente. Da WalletOpened o da un pulsante chiamare GetBalance.

Nell'evento BalanceReceived:

| Parametro | Tipo | Significato |
| --- | --- | --- |
| total | testo | Saldo totale riportato dall'SDK, in NEXA con due decimali |
| confirmed | testo | Saldo confermato riportato dall'SDK, in NEXA |
| unconfirmed | testo | Saldo non confermato riportato dall'SDK, in NEXA |
| synced | booleano | Esito SDK synced(-1), non garanzia di finalità |
| connected | booleano | Presenza di almeno una connessione P2P al momento della lettura |
| syncedHeight | testo | Altezza sincronizzata del wallet fornita dall'SDK |
| chainHeight | testo | Altezza corrente della catena fornita dall'SDK |

Per la prima prova mostrare total, confirmed, unconfirmed, synced e connected
nelle Label. Esempio: 12345 sat diventano il testo `123.45` NEXA; 1 sat diventa
`0.01`, zero `0.00`. La scala deriva dalla skill Nexa: 1 NEXA = 100 sat.
Non vengono usati double/float e non vengono ricalcolati i totali dell'SDK.
Per mantenere la precisione non convertire questi testi in numeri App Inventor
prima di mostrarli. I valori sono letture successive, non uno snapshot atomico;
non sono una promessa di saldo spendibile o finale.

GetBalance invalida la cache documentata e legge lo stato locale monitorato dal
wallet. Non forza una richiesta remota né attende la sincronizzazione e non
sblocca le chiavi. I valori offline possono essere precedenti alla disconnessione;
quelli durante la scansione possono essere incompleti. Si restituiscono i flag
anche se gli importi sono zero, senza fingere uno stato pronto.
Un errore arriva a WalletFailed, mai come saldo zero inventato.

Per seguire la sincronizzazione si può aggiungere un Clock con intervallo 5000 ms:
se WalletOpen è vero chiamare GetBalance, evitando altre azioni in parallelo.
La coda delle operazioni resta seriale; nessun polling automatico è aggiunto
all'estensione. Gli errori conservano il nome operazione GetBalance.

Fonti aggiuntive:

- nexa-wallet-lifecycle-and-chain/SKILL.md, pattern 4: balance, balanceConfirmed,
  balanceUnconfirmed, synced(-1), syncedHeight, blockchain.curHeight.
- Stessa skill, Connectivity checks: blockchain.net.p2pCnxns.
- [Transaction construction, pattern 4 e Mental model](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-transaction-construction/SKILL.md):
  CommonWallet.clearCachedBalances e scala 100 sat per NEXA.

Firme Java verificate con javap nell'artefatto 0.5.69: clearCachedBalances(),
getBalance(), getBalanceConfirmed(), getBalanceUnconfirmed(), synced(long),
getSyncedHeight(), getBlockchain().getCurHeight(), getNet().getP2pCnxns().
La formattazione BigDecimal è codice Java applicativo, non un'API Nexa nuova.

36 test del controller/formattazione/FastSync superati con backend finti. Cifratura, DB,
rete e valori reali del saldo su Android richiedono la prova del nuovo APK.


## FastSync manuale (0.0.5)

Il comando richiede **la passphrase** del wallet cifrato: non viene conservata per
riutilizzarla dopo OpenWallet. Non si avvia automaticamente all'apertura.
Prima aggiornare lo stesso progetto, ricompilare e installare l'APK come
aggiornamento, mantenendo dati e DB esistenti. Non creare un nuovo wallet.

1. Aprire il wallet con OpenWallet e attendere WalletOpened.
2. Verificare con GetBalance che chainHeight sia maggiore di zero. Serve una
   punta della catena conosciuta, come nel flusso documentato dalla skill.
3. Da un pulsante chiamare FastSync con la stessa passphrase. Questa azione
   sceglie esplicitamente la ricostruzione della storia tramite Electrum, con
   maggiore fiducia nel server rispetto alla scansione SPV ordinaria.
4. In FastSyncProgress(message) mostrare message: è testo SDK, non percentuale.
5. In FastSyncFinished mostrare synced, beforeHeight e afterHeight e richiamare
   GetBalance. Un evento terminale non va etichettato automaticamente "successo".
6. Mostrare sempre WalletFailed(operation, code, message).

FastSyncFinished(advanced, synced, cancelled, beforeHeight, afterHeight,
chainHeight) riporta letture, non una verifica indipendente della storia:

- advanced: l'altezza è aumentata. Può includere il normale avanzamento SPV
  contemporaneo, quindi non dimostra da solo che Electrum abbia completato la ricerca.
- synced: lo stato SDK rilevato alla fine. Anche questo mantiene il compromesso
  di fiducia nella storia restituita da Electrum, non aggiunge una prova SPV.
- cancelled: è stata registrata una richiesta di annullamento. Non significa
  rollback o che l'operazione non abbia già aggiornato lo stato del wallet.
- Le altezze sono testo per evitare perdita di precisione nei blocchi.

Se era già sincronizzato, l'evento terminale restituisce synced=true senza
sbloccare chiavi o avviare un'altra ricerca. Se Electrum non trova nodi, il
callback SDK "finished" può comunque arrivare: synced=false e l'assenza di un
salto non vengono convertiti in un successo fittizio.

### Chiavi, annullamento e timeout

La ricerca deriva indirizzi dal wallet: durante il tentativo le chiavi devono
restare temporaneamente sbloccate. L'estensione non richiede la frase di recupero
né la invia a Electrum e non salva la passphrase. Al callback finale riblocca il
wallet prima di emettere FastSyncFinished. GetBalance resta disponibile durante
la ricerca; CreateWallet, OpenWallet e un secondo FastSync vengono rifiutati.

CancelFastSync imposta il flag di abort documentato. È cooperativo: si attende il
terminale SDK prima di liberare lo stato in corso e ribloccare ordinariamente.
Un watchdog richiede l'arresto e tenta il riblocco dopo cinque minuti. È una
protezione best effort: sospensione del processo Android o operazioni bloccate
possono ritardare il timer. Non è una garanzia assoluta di durata in memoria.

In caso di FAST_SYNC_TIMEOUT, FAST_SYNC_START_FAILED o FAST_SYNC_CLEANUP_FAILED,
FastSyncRunning può restare vero finché non arriva una conferma terminale SDK.
Non si avvia una seconda ricerca su un thread di cui non è noto l'arresto.
Se resta così, arrestare completamente il processo dell'app e riaprirla, senza
cancellare dati. FAST_SYNC_LOCK_FAILED richiede l'arresto del processo.
Non forniamo un blocco hard-kill di thread nativi e non fingiamo che l'annullamento
immediato sia supportato dall'SDK. Nessun reset o cancellazione wallet aggiunto.

### Fonte e compatibilità

[nexa-wallet-lifecycle-and-chain/SKILL.md, Pattern 4 — Fast-forward sync](https://gitlab.com/nexa/nexaaiskills/-/blob/3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24/skills/nexa-wallet-lifecycle-and-chain/SKILL.md)
documenta Bip44Wallet.fastForward(callback), Objectify<Boolean>, aborter.obj=true,
compromesso Electrum e il significato non conclusivo di "finished".
Sblocco/riblocco: stessa skill, pattern 6. Altezze e synced: pattern 4.
Firme Java verificate su 0.5.69: FastforwardKt.fastForward(Bip44Wallet, Function1),
Objectify.setObj(Boolean); Unit.INSTANCE è l'interoperabilità Kotlin standard.
Non si usa l'overload basso livello CommonWallet.fastForward(height, date, hash, txs).

Il sources JAR ufficiale 0.5.69 mostra receive/change nell'helper; non presentiamo
questa vecchia release come una scansione esaustiva di ogni percorso/account o
identità citato genericamente dal corpus corrente. Nessuna derivazione extra
viene inventata. Lacuna registrata in SKILL-UPDATES.md.

36 test locali passati includono callback sincroni/tardivi/duplicati, password
errata, nessun salto, progresso SPV concorrente, annullamento, timeout, guardia
anti-concorrenza e riblocco. Non eseguono Electrum o Android: velocità, esito
reale e persistenza dopo fast-forward richiedono la prova dello smartphone.
