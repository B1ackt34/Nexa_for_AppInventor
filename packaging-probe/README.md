# NexaPackagingProbe 0.0.3 — wallet testnet sperimentale

Estensione Java per MIT App Inventor 2 con Nexa Android 0.5.69. Il nome del
componente resta NexaPackagingProbe per aggiornare i blocchi delle versioni precedenti.
Solo testnet; nessuna funzione mainnet, saldo, invio o importazione chiave privata.

## Blocchi disponibili

| Funzione | Risultato asincrono |
| --- | --- |
| Initialize() | InitializationSucceeded oppure InitializationFailed(code, message) |
| CreateWallet(passphrase) | WalletCreated(recoveryPhrase), solo se non esiste già un DB |
| OpenWallet(passphrase) | WalletOpened; non crea mai un nuovo wallet |
| ConfirmBackup() | BackupConfirmed; registra la conferma dell'utente |
| GetAddress() | AddressReceived(address), dopo creazione/apertura e conferma backup |

Gli errori wallet arrivano a WalletFailed(operation, code, message). Ogni richiesta
viene serializzata su un worker; gli eventi sono emessi sul thread UI.
Le funzioni wallet inizializzano automaticamente la libreria se necessario.

Proprietà di sola lettura: Initialized, Network (sempre `testnet`), WalletOpen.
WalletOpen significa che l'indirizzo pubblico è stato preparato in questo processo:
non implica chiavi sbloccate o sincronizzazione della blockchain completata.

## Prima creazione sul dispositivo

1. Aggiornare l'estensione con `NexaPackagingProbe-0.0.3.aix`.
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
4. `python3 tools/inspect_aix.py out/NexaPackagingProbe-0.0.3.aix`

Usare proxy e CA della sessione. Non disattivare TLS o controlli di integrità.
original_names preserva i nomi Nexa richiesti da JNI. Il postprocessore mantiene
ogni file interno sotto 5 MiB per i server MIT precedenti alla correzione
b097193903890d6aa729ac52464ae342d3a600e2. Mantiene i payload delle classi, DEX e
librerie native invariati, registrando i JAR aggiuntivi nei build metadata MIT.
La versione 0.0.2 è stata importata con successo dall'utente; la nuova 0.0.3
richiede nuova compilazione APK e prova dispositivo. Non usare il Companion con AAR.

## Verifiche automatizzate del ciclo di vita

Compilare WalletSession.java e tests/org/nexa/appinventor/probe/WalletSessionTest.java
con javac --release 17 in una directory esterna, poi eseguire
org.nexa.appinventor.probe.WalletSessionTest. I test usano backend finti per provare
le regole di non sovrascrittura, password, backup e blocco chiavi. Non simulano
la correttezza del database Nexa, la cifratura effettiva o la rete Android.
