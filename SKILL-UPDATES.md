# Lacune Nexa AI Skills riscontrate

## 2026-11-04 — Packaging Android per App Inventor

Stato: open. Corpus: nexaaiskills commit
`3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24`.
Skill interessate: nexa-project-setup, nexa-wallet-lifecycle-and-chain.

Domande non risolte dal corpus: coordinate/inizializzazione Android del driver SQL,
integrazione Startup/JNI in un'estensione App Inventor e firme di interoperabilità
Java. Nessuna API non documentata è stata adottata.

Evidenze esterne al corpus: registry ufficiale progetto GitLab 48545045,
libnexakotlin-android 0.7.4 `.module`, AAR e sources JAR; libnexajni 0.7.4 POM/AAR.
L'AAR dichiara minSdk 28, minCompileSdk 37 e provider AndroidX Startup.
`androidMain/platformAndroid.kt` nel sources JAR definisce Initialiser.create e
createDbDriver; la lettura chiarisce il ruolo del contesto Android ma non rende
queste API autorizzate dal vincolo di usare esclusivamente le skill.

Rush commit `8d9d79cc0573ac01bf56cb676ddf476323e10385`,
`lib/src/agents/skill.dart`, documenta il limite Companion per estensioni con AAR.
Dettagli e stato delle prove in PACKAGING-ASSESSMENT.md.


Aggiornamento: grafo Android 0.7.4 risolto (53 artefatti), conversione D8 9.4.24
superata con due DEX. Runtime Rush v206 runtime.json: androidApi 36; metadata
AAR di libnexakotlin/libnexajni/nexacli: minCompileSdk 37. Serve documentare una
combinazione di toolchain supportata, senza ignorare il requisito. Questo esito
non autorizza API esterne al corpus e non dimostra funzionamento su Android.


Terza verifica: SDK Google 37.0 disponibile e verificato; D8 supera il grafo anche
con android.jar 37. Il checkout MIT appinventor-sources
b097193903890d6aa729ac52464ae342d3a600e2 mantiene android.sdk.version=36 in
appinventor/components/build.xml:15. AAR Nexa Android 0.7.3 e 0.7.1 dichiarano
anch'essi minCompileSdk=37; nessun downgrade o runtime personalizzato applicato.


2026-11-04: individuata candidata Android libnexakotlin 0.5.69 con minCompileSdk
36 e minSdk 29; grafo di 47 artefatti risolto e D8 API36 superato. Riscontrate le
API già documentate nel sources JAR ufficiale (percorsi/righe nel rapporto).
La verifica non autorizza nuove API dal sorgente; restano lacune inizializzazione
Android/interop Java e importazione chiave privata locale.


2026-11-04: prototipo Java .aix compilato con Rush 2.1.2 e Nexa 0.5.69.
Firma documentata initializeLibNexa verificata con javap come
LibnexaandroidKt.initializeLibNexa(String). Nessun setter Android extra-corpus
usato: provider Startup conservato per la futura prova APK. Packaging verificato
staticamente, esecuzione Android non ancora disponibile. I limiti su chiave
privata e integrazione runtime non sono considerati risolti dalla build.


2026-11-04: 0.0.3 implementa le API wallet documentate e verifica le firme Java
con javap. L'inizializzazione del contesto Android rimane affidata al provider
Startup già incluso nell'AAR: nessun setter extra-corpus. L'utente ha confermato
inizializzazione nativa precedente, ma DB e riapertura richiedono nuova prova.
La lacuna sull'importazione della chiave privata nel wallet locale resta aperta.


2026-11-05 — fastForward e release Android 0.5.69

Stato: open (copertura derivazioni per versioni precedenti). Il corpus fissato
3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24, wallet lifecycle pattern 4, descrive
receive/change/identity. Nel sources JAR ufficiale libnexakotlin-android 0.5.69,
commonMain/fastforward.kt, funzione Bip44Wallet.fastForward (circa righe 148-210),
l'helper avvia normal e change per account 0, senza una ricerca identity esplicita.
La nuova estensione usa soltanto l'helper documentato, senza inventare percorsi;
non dichiara copertura completa di tutti gli account/identità. Firma JVM verificata
con javap: FastforwardKt.fastForward(Bip44Wallet, Function1) -> Objectify<Boolean>.
La derivazione legge wallet.secret durante il job: il wallet cifrato viene
sbloccato tramite l'API documentata per la durata dell'operazione, poi ribloccato.
Non vengono usate direttamente secret, funzioni interne o altri overload non
necessari. Il callback finished non viene trattato come prova di successo.


2026-11-06 — riapertura cifrata prima dell'aggancio blockchain

Stato: open. Wallet lifecycle pattern 3 mostra openWallet seguito da unlock in un
passo separato. La 0.5.69 openWallet (sources JAR ufficiale commonMain/init.kt:
450-476) invoca addBlockchain prima del return; commonMain/wallet.kt:6005-6016
invoca prepareDestinations, che può richiedere derivazione a wallet ancora locked.
Sintomo utente dopo ricezione: WalletLockedException in OpenWallet, DB non raccolto.
Il punto esatto non è confermato da stack del dispositivo.

Usate API già nel corpus: openDisconnectedWallet (pattern 7), unlock (pattern 6),
blockchainFor (pattern 5), addBlockchain (mental model, recoveryFlow attach later).
La firma addBlockchain(Blockchain, Long?, Long?) e null/null per la ripresa non
sono spiegate nel corpus: verificate contro init.kt:475 e wallet.kt:5871-6005
(loadChainState prima dei fallback). Nessuna derivazione o accesso interno al DB
inventati. La skill dovrebbe documentare questo ordine per wallet cifrati e la
ripresa dello stato, con prova di riavvio dopo una ricezione reale.
