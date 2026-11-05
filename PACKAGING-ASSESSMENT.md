# Verifica preliminare packaging Nexa / MIT App Inventor

Data: 2026-11-04. Analisi statica; nessuna estensione generata o eseguita.

## Esito

La compatibilità completa non è dimostrata. La distribuzione Android Nexa usa AAR,
JNI e AndroidX Startup. Rush documenta il packaging AAR, ma dichiara che le
estensioni che includono AAR non funzionano nel Companion. Il primo obiettivo
realistico è pertanto un APK compilato; il supporto Companion resta un limite,
non un risultato acquisito. Non estrarre solo classes.jar scartando JNI e manifest.

## Fonti e riproducibilità

- Corpus Nexa: https://gitlab.com/nexa/nexaaiskills , commit
  `3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24`.
- Skill primaria: `skills/nexa-project-setup/SKILL.md`, sezioni Setup and versions
  e Verifying API signatures before relying on them.
- Ciclo wallet: `skills/nexa-wallet-lifecycle-and-chain/SKILL.md`.
- Rush: https://github.com/ShreyashSaitwal/rush-cli , commit
  `8d9d79cc0573ac01bf56cb676ddf476323e10385`, pubspec versione 2.1.2.
  È un checkout dei sorgenti, non una CLI installata e verificata.
- Rush `lib/src/agents/skill.dart`: limiti Companion/AAR e regole JNI/original_names.
- Rush `lib/src/commands/build/tools/compiler.dart`: javac --release 17.
- Rush `lib/src/commands/build/tools/executor.dart`: passaggi D8/desugaring.
- Rush `lib/src/services/lib_service.dart`: R8 9.4.24.

## Artefatti ispezionati

Registry base: https://gitlab.com/api/v4/projects/48545045/packages/maven/

Il metadata di `org/nexa/libnexakotlin/maven-metadata.xml` dichiara release 0.7.4.
Ispezionati i `.module` del modulo radice e Android, il relativo AAR e sources JAR,
e POM/AAR di `org.nexa:libnexajni:0.7.4`. Download HTTPS con verifica TLS attiva.

| Elemento | Evidenza osservata |
| --- | --- |
| Variante Android | org.nexa:libnexakotlin-android:0.7.4, AAR |
| Bytecode classes.jar | tutte le classi ispezionate hanno major version 65, Java 21 |
| Manifest Android | minSdkVersion 28, INTERNET e ACCESS_NETWORK_STATE |
| Metadata AAR | minCompileSdk 37; coreLibraryDesugaringEnabled=false |
| Inizializzazione | provider AndroidX Startup con metadata org.nexa.libnexakotlin.Initialiser |
| Attributi application | usesCleartextTraffic=true, requestLegacyExternalStorage=true |
| JNI | org.nexa:libnexajni:0.7.4, minSdkVersion 28 |
| ABI JNI | armeabi-v7a, arm64-v8a, x86, x86_64 |
| Librerie per ABI | libnexalight.so e libc++_shared.so |

minCompileSdk è un requisito dichiarato per compilare, non il minimo Android di
esecuzione. Non abbassarlo o ignorarlo senza una verifica supportata.
La presenza dei file .so non verifica caricamento, compatibilità del dispositivo
o supporto delle pagine di memoria da 16 KB.

## Dipendenze dirette Android dichiarate

Dal runtime variant del `.module`, raggruppando i duplicati:

- SQLDelight runtime, android-driver, coroutines-extensions: 2.4.0.
- AndroidX startup-runtime: 1.2.0.
- libnexajni: 0.7.4.
- kotlinx-coroutines-core: 1.11.0; kotlinx-datetime: 0.8.0; atomicfu: 0.33.0.
- Ktor client-core, client-serialization, client-content-negotiation,
  serialization-kotlinx-json, network, client-cio: 3.6.0.
- bignum e bignum-serialization-kotlinx: 0.3.10.
- Okio: 3.18.2.
- org.nexa:mpthreads: 0.5.13; org.nexa:nexacli: 0.2.4.
- kotlin-stdlib, kotlin-stdlib-jdk8, kotlin-reflect: senza versione esplicita
  nelle singole voci della variante; vanno risolti con i vincoli complessivi.

Questo elenco NON è una risoluzione transitiva completata. I metadata KMP
richiedono selezione delle varianti corrette: non assumere che un resolver Maven
POM selezioni automaticamente gli stessi artefatti di Gradle.

## Decisioni e prove successive

1. Risolvere l'intero grafo Android con versioni e varianti fissate, controllando
   disponibilità e conflitti AndroidX/Kotlin con il runtime App Inventor scelto.
2. Verificare la piattaforma Android usata da Rush/App Inventor rispetto a API 37,
   e provare D8 sull'intero grafo Java 21. La compilazione Java dell'estensione
   a release 17 non dimostra né esclude da sola compatibilità delle dipendenze.
3. Conservare manifest, provider Startup, placeholder applicationId e JNI;
   verificare le regole original_names prima di rinominare classi JNI.
4. Ottenere documentazione nelle skill per l'inizializzazione Android e le firme
   Java necessarie. Non aggiungere chiamate Nexa non documentate nel corpus.
5. Solo dopo, costruire una piccola .aix e un APK: caricamento libreria,
   initializeLibNexa, creazione wallet testnet, riapertura dopo riavvio.
6. Provare su dispositivo/emulatore, controllare log e contenuto APK. Documentare
   il limite Companion; non dichiarare test superati senza eseguirli.

## Stato delle verifiche

Eseguiti: lettura metadata ufficiali, ispezione archivi/manifest/bytecode/ABI,
analisi dei passaggi di compilazione e delle limitazioni documentate da Rush.
Non eseguiti: risoluzione transitiva completa, compilazione .aix/APK, prove D8,
inizializzazione Android, database, rete testnet, Companion e dispositivo.
Nessuna modifica alla bozza ambiente e nessuna pubblicazione.


## Seconda verifica — risoluzione e D8

La risoluzione Gradle è ora completata: **53 artefatti**, nessuna dipendenza
irrisolta. Manifesto delle coordinate e SHA-256 in ANDROID-DEPENDENCIES.txt.
Tool di analisi: Gradle 8.14.3, distribuzione verificata con SHA-256 ufficiale,
Java 21, configurazione runtime androidJvm con fallback JVM compatibili.
Il primo tentativo senza tali regole non selezionava correttamente le varianti;
il successivo errore di copia nel task di analisi è stato corretto.
Non si trattava di dipendenze Nexa mancanti.

### Conversione D8: superata

D8/R8 9.4.24 (coordinate usate da Rush) ha convertito tutti i 53 JAR, estraendo
classes.jar dagli AAR, con min-api 28 e android.jar del runtime Rush v206.
Exit status **0**, log diagnostico vuoto, prodotti classes.dex e classes2.dex.
Questa prova dimostra che D8 accetta il bytecode del grafo; non valida JNI,
manifest, provider, risorse, esecuzione o build finale App Inventor. La presenza
 di due DEX deve essere gestita dalla pipeline finale; non è ancora verificata.

### Disallineamento toolchain confermato

Il runtime ufficiale Rush v206 è stato scaricato e verificato con il checksum
fissato dal checkout Rush:
4bf232cd5c1284248556573943f56c3e36f566f4e56b4b210389d63e9aa915ef.
Il suo runtime.json dichiara **androidApi 36**.
Gli AAR libnexakotlin, libnexajni e nexacli selezionati dichiarano invece
**minCompileSdk 37**. Il requisito degli artefatti non è soddisfatto dal runtime
Rush esaminato, anche se D8 termina senza errori. Non sono stati alterati i
metadata per aggirarlo. Non è stata dimostrata una impossibilità tecnica
assoluta: resta necessaria una toolchain supportata che soddisfi il requisito.

### Riproduzione nell'istanza corrente

Area di prova: /workspace/scratch/nexa-packaging.
settings.gradle e build.gradle definiscono il resolver di analisi; eseguire
`bash gradle-8.14.3/bin/gradle --no-daemon -g gradle-cache resolveAndroidGraph`
con le proprietà proxy HTTP/HTTPS ricavate dal proxy della sessione e TLS attivo.
Gli artefatti sono esportati in resolved/, i classes.jar estratti in classes/.
resolution-final.log contiene l'esito positivo; d8.log è vuoto.
Il test D8 ha usato `java -Xmx2g -cp r8.jar com.android.tools.r8.D8 --min-api 28
--lib ai2/libs/android.jar --output dex --release` seguito dai 53 JAR risolti.
Nessuna chiamata wallet o transazione eseguita; nessun segreto richiesto.

### Passo successivo e limiti

Prima di promettere una .aix funzionante occorre una toolchain Android/App
Inventor che soddisfi minCompileSdk 37, più il packaging AAR/JNI e la gestione
multidex. Restano il limite Companion documentato da Rush e la lacuna nelle skill
sull'inizializzazione Android/interop Java. Non è stata scelta arbitrariamente
una versione Nexa precedente, né chiamata un'API non documentata nel corpus.


## Terza verifica — SDK 37 e toolchain MIT ufficiale

- Catalogo SDK Google: https://dl.google.com/android/repository/repository2-3.xml.
  È disponibile platforms;android-37.0, archivio platform-37.0_r02.zip.
  Scaricato via HTTPS e verificato con SHA-1 ufficiale
  ed8ebf7f8822a4de5686d427f237d2fa30ff7410.
- SDK estratto in /workspace/scratch/nexa-packaging/sdk37/android-37.0.
- D8 9.4.24 rieseguito con android.jar SDK 37 e gli stessi 53 JAR: exit 0,
  log vuoto, classes.dex e classes2.dex in dex37/. Test di conversione superato;
  nessun APK o caricamento nativo verificato.
- Sorgenti MIT App Inventor: https://github.com/mit-cml/appinventor-sources,
  commit b097193903890d6aa729ac52464ae342d3a600e2.
  appinventor/components/build.xml, riga 15: android.sdk.version=36.
  Checkout analizzato: non prova la versione dei servizi pubblici in esecuzione.
- Rush Ai2Runtime.install supporta RUSH_AI2_RUNTIME_ZIP per un runtime locale.
  Ciò consente un percorso tecnico personalizzato, ma non aggiorna il buildserver
  pubblico, non certifica una distribuzione SDK 37 e non valida un APK.
  Non è stato modificato alcun runtime né disabilitato un checksum.
- Ispezionati anche gli AAR Android libnexakotlin 0.7.3 e 0.7.1: entrambi
  dichiarano minCompileSdk=37. Nessun downgrade adottato.

Esito: il problema non è disponibilità SDK o accettazione bytecode da parte di
D8. Manca una combinazione App Inventor standard verificata che soddisfi i
requisiti dichiarati degli AAR esaminati. Percorsi possibili da valutare:
una release Nexa precedente con requisito compatibile (e nuove verifiche API),
oppure un buildserver/runtime App Inventor personalizzato con SDK 37. Il secondo
richiede manutenzione e prove end-to-end proprie e non fornisce il supporto
Companion per AAR che Rush dichiara assente. Nessuna API Nexa esterna alle skill
è stata implementata, nessuna pubblicazione eseguita.


## Quarta verifica — candidata compatibile con SDK 36 (2026-11-04)

**La ricerca ha individuato libnexakotlin Android 0.5.69 come candidata**: non è
ancora una versione adottata né una .aix verificata. Supera il disallineamento
SDK degli artefatti 0.6/0.7 esaminati.

| Versioni AAR ispezionate | minCompileSdk |
| --- | --- |
| 0.7.4, 0.7.3, 0.7.1, 0.7.0, 0.6.1, 0.6.0 | 37 |
| 0.5.69, 0.5.68, 0.5.67 | 36 |

La candidata 0.5.69 dichiara minSdkVersion **29 (Android 10)** nel manifest,
non 28. Il bytecode resta Java 21 (major 65). JNI è incorporato direttamente
nell'AAR libnexakotlin-release: libc++_shared.so e libnexalight.so per
armeabi-v7a, arm64-v8a, x86, x86_64. I segmenti ELF PT_LOAD delle librerie
64 bit hanno p_align 16384; questa verifica statica NON sostituisce verifica
allineamento ZIP dell'APK o esecuzione su dispositivi con pagine da 16 KB.

### Dipendenze e D8

Configurazione Gradle di analisi separata in
/workspace/scratch/nexa-packaging/candidate-0.5.69.
Risoluzione completata: **47 artefatti**, nessuna dipendenza irrisolta.
Nessun AAR del grafo dichiara minCompileSdk superiore a 36.
Coordinate e checksum in ANDROID-DEPENDENCIES-0.5.69.txt; il precedente manifesto
ANDROID-DEPENDENCIES.txt resta il riferimento distinto della 0.7.4.

D8 9.4.24 con android.jar API 36 del runtime Rush v206 e min-api 29:
**exit 0**, log vuoto, classes.dex e classes2.dex prodotti. Il risultato supera
la verifica di conversione del bytecode, non packaging JNI, avvio o build APK.

### API già documentate: riscontro nella release candidata

Fonti primarie restano le skill del corpus fissato. Il sources JAR ufficiale
libnexakotlin-android-0.5.69-sources.jar è stato usato soltanto per verificare
che le API già documentate esistano, non per introdurre API extra-corpus.

- initializeLibNexa: skill wallet lifecycle, Setup and versions;
  riscontro commonMain/platform.kt:87, androidMain/libnexaandroid.kt:529.
- newWallet: skill wallet lifecycle, pattern 1;
  commonMain/init.kt:393 e :411 (parametro initializer aggiuntivo con default).
- openWallet: skill wallet lifecycle, pattern 3;
  commonMain/init.kt:450 (initializer opzionale).
- recoverWallet: skill wallet lifecycle, pattern 2;
  commonMain/init.kt:423 e :440. Recupero frase, non importazione chiave singola.
- getCurrentDestination/getNewAddress: skill wallet lifecycle, pattern 4;
  commonMain/iWallet.kt:400 e :436.
- balance/balanceConfirmed/balanceUnconfirmed/synced: stessa skill, pattern 4;
  commonMain/iWallet.kt:731, :734, :740 e :780.
- send: skill transaction construction, simpleapi sugar for paying;
  commonMain/iWallet.kt:483, :495, :508 e seguenti. La presenza della firma non
  dimostra che un pagamento sia riuscito o che i problemi corretti nelle release
  successive siano assenti nella candidata.
- encrypt/lock/unlock: skill wallet lifecycle, pattern 6;
  commonMain/iWallet.kt:328, :332 e :340.

Restano da verificare le firme JVM effettivamente invocabili da Java e i default
Kotlin: nessun nome Java generato è stato inventato. Persistono le lacune del
corpus sull'inizializzazione Android nell'host App Inventor e sull'importazione
chiave privata nel wallet locale. La candidata è una release precedente: prima
 dell'uso reale occorre esaminare le correzioni successive rilevanti per wallet,
fee e transazioni. Nessuna garanzia di sicurezza dedotta dal solo D8.

### Passo successivo aggiornato

Il blocco minCompileSdk è superabile con la candidata verificata 0.5.69; non è
necessario scegliere subito un buildserver SDK 37. Il prossimo test utile è il
packaging Rush con il grafo candidato e un componente minimo basato sulle sole
API documentate, poi un APK con minSdk 29. L'inizializzazione Android non coperta
dalle skill resta da chiarire prima di introdurre chiamate extra-corpus.
Companion/AAR e test su dispositivo rimangono limiti non risolti. Nessuna
modifica a configurazione ambiente o pubblicazione eseguita.


## Quinta verifica — prototipo .aix compilato (2026-11-04)

Creato packaging-probe/, componente Java NexaPackagingProbe. Il prototipo usa
esclusivamente initializeLibNexa come API Nexa, documentata nella skill wallet
lifecycle; la firma Java è stata verificata con javap nella candidata 0.5.69.
Non crea wallet, chiavi, rete o transazioni. Annotazioni DesignerComponent,
SimpleFunction, SimpleProperty e SimpleEvent presenti, insieme a Extension
richiesta da Rush. Inizializzazione su worker con eventi sul thread UI.

### Build e verifiche superate

- Rush 2.1.2, runtime v206 verificato, JDK Temurin 21.0.8+9 completo con checksum
  SHA256 ufficiale verificato. Il compilatore del runtime iniziale non supportava
  --release 17 per assenza delle tabelle di piattaforma; non era un problema Nexa.
- Rush ha richiesto original_names: org.nexa.libnexakotlin per JNI; regola applicata.
- Manifest del componente aggiunto per la fusione con gli AAR.
- Build completa riuscita: javac, manifest merger, R8, relocation delle dipendenze,
  desugaring, D8 e packaging AIX. Nessun warning o controllo disabilitato per JNI.
- AIX: packaging-probe/out/org.nexa.appinventor.probe.aix, 14.101.219 byte.
  SHA256: 3579994a638c40e0ec37990b0dfeafa3fa9fb1cdde4225b06a21f59fc9606f5b.
- tools/inspect_aix.py passa: descrittori dei blocchi, DEX, provider Startup,
  classi JNI con nomi originali, otto file ELF per quattro ABI.
- Risoluzione del progetto dependency-resolution verificata offline con lock e
  verifica checksum attivi: 47 artefatti identici al manifesto candidato.
  Il lock include l'alias metadata kotlin-stdlib-common 2.3.21. Due metadata
  Kotlin mancanti dal primo file di verifica sono stati scaricati da Maven Central,
  confrontati con SHA1 ufficiali e cache, poi registrati con SHA256; nessun checksum
  esistente è stato sostituito, né la verifica disabilitata.

### Limiti ancora aperti

Non disponibili adb o emulator nell'istanza; nessuna esecuzione Android o build
APK dimostrata. Il test sul dispositivo deve importare la AIX in App Inventor,
aggiungere componente/Button/Label, chiamare Initialize e visualizzare l'evento.
InitializationSucceeded dimostra soltanto l'inizializzazione nativa nel processo.
Il provider automatico dipende dal manifest APK: un errore prima dell'avvio
richiede log Android e potrebbe non arrivare all'evento InitializationFailed.
Il limite Companion/AAR documentato da Rush resta valido. Le cinque funzioni
wallet non sono ancora implementate. Nessuna pubblicazione o modifica bozza.


## Importazione MIT fallita e variante 0.0.2 (2026-11-04)

L'utente ha osservato "Import Extension Failed! null" su ai2a. L'importazione
non è quindi verificata nonostante la build riuscita. ZIP e JSON della 0.0.1
sono stati letti con Java ZipInputStream e la libreria org.json dei sorgenti MIT:
nessun errore di parsing. Non disponibili log del servizio o conferma sul metodo
file/URL e integrità del file locale dell'utente.

Individuata correzione ufficiale MIT
b097193903890d6aa729ac52464ae342d3a600e2 in PostgreSQLStorageIo.getMaxJobSizeBytes:
il limite precedente di 5 MiB viene elevato a 15 MiB. La 0.0.1 supera 5 MiB in
due file interni. La versione del servizio live non è nota: questa è una causa
plausibile, non una diagnosi confermata dai log.

Creata variante 0.0.2 con tutti i file interni sotto 5 MiB. tools/compat_aix.py
suddivide AndroidRuntime.jar, registra i nuovi JAR e separa classes.jar dall'AAR
Nexa mantenendo i file JNI e manifest. Verifica identità dei payload originali:
nessuna classe, istruzione DEX o libreria nativa è eliminata o modificata.
Il codice Nexa non è cambiato e non sono introdotte nuove API.

Prove superate: archivio/descrittori/JNI, Java ZipInputStream e org.json MIT,
conversione D8 dell'intero grafo di JAR ripacchettizzato (exit 0, log vuoto).
Dimensione AIX 13.709.487 byte; massimo file interno 3.276.061 byte.
SHA256 a0d3b6e3d2a121eed8fc16d5e92ca594a8dcdc3fe1b143fdb03dddb4cf637d98.
Importazione live e APK restano da provare. La variante viene distribuita nel
ramo dedicato già autorizzato, mantenendo l'originale per confronto.


## Versione 0.0.3 — creazione e riapertura wallet testnet (2026-11-04)

L'utente ha confermato l'importazione della 0.0.2 e riferito "inizializzazione
riuscita". La prova non è stata osservata direttamente dall'agente.
La 0.0.3 aggiunge CreateWallet(passphrase), OpenWallet(passphrase), ConfirmBackup
 e GetAddress, con eventi asincroni e un solo worker per le operazioni wallet.
Un DB fisso testnet privato per app; nessun reset da eccezione. Nuova creazione
cifrata, salvata e ribloccata prima del successo; frase emessa una sola volta.
OpenWallet sblocca per validare password/derivare l'indirizzo e poi riblocca.
GetAddress usa solo il dato pubblico in cache e richiede conferma backup salvata.
Network e WalletOpen sono proprietà di stato, non prove di sincronizzazione.
Il limite di 12 caratteri è una regola dell'estensione, non una regola Nexa.

API e fonti: nexa-wallet-lifecycle-and-chain/SKILL.md pattern 1/3/4/6,
recoveryFlow.md (saveBip44Wallet e backup/cifratura), walletStartupTemplate.kt
(controllo DB Android prima dell'apertura), nexa-identity-and-addresses/
validateHelpers.kt (PayAddress.chainSelector), nexa-ktor-server-integration/
SKILL.md (conversione PayAddress.toString). Corpus fissato invariato. Firme JVM
verificate sull'artefatto 0.5.69; nessun setter di contesto extra-corpus usato.

12 test del controller con backend finti superati: inclusi no sovrascrittura,
riapertura simulata, password errata, file mancante/corrotto, errore salvataggio,
backup, blocco chiavi anche per eccezione checked Kotlin. Non provano SQLite,
cifratura Nexa, rete o ciclo di vita reale Android. Build Rush completa superata,
postprocessore sotto 5 MiB, controlli descrittori/JNI e lettura Java ZIP/JSON passati.
La prova del DB effettivo e della riapertura dopo arresto resta sul dispositivo.
Importazione wallet da chiave, saldo e invio non implementati.

AIX 0.0.3: 13.729.088 byte; SHA256
 afce4ea5d0d77d28a78fbe76a27188cc88e47d07dbda460349224ba641898ead.


## Versione 0.0.4 — GetBalance testnet (2026-11-05)

L'utente ha confermato il funzionamento della 0.0.3 sullo smartphone, inclusa
riapertura e persistenza. La 0.0.4 conserva DB, nome componente e blocchi esistenti.
Aggiunge GetBalance e BalanceReceived(total, confirmed, unconfirmed, synced,
connected, syncedHeight, chainHeight). Lettura seriale su worker, evento UI.

Fonti fissate al corpus 3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24:
wallet lifecycle pattern 4 per balance/balanceConfirmed/balanceUnconfirmed,
synced(-1), syncedHeight e blockchain.curHeight; stessa skill Connectivity checks
per net.p2pCnxns. Transaction construction pattern 4 per clearCachedBalances,
Mental model per 1 NEXA=100 sat. Tutte le firme Java verificate con javap su
0.5.69. Nessuna nuova libreria o API extra-corpus.

Saldi convertiti da Long a BigDecimal scala 2 e poi testo, senza double/float;
altezze in testo per conservare precisione e sentinelle dell'SDK. Non si assume
che letture successive costituiscano uno snapshot atomico né che il totale
sia ricostruibile dagli altri due campi. Non vengono sbloccate chiavi o forzate
richieste remote. Flag offline/unsynced conservati anche con zero. Le eccezioni
restano WalletFailed, non sono convertite in saldo zero.

21 test controller/formattazione con backend finti superati, compresi precisione
oltre 2^53, estremi Long, unità minime, segni, flags, letture aggiornate, errori e
assenza di unlock/derivazione/frase nel percorso saldo. Non provano i valori reali
Nexa su Android. Build Rush completa (javac, R8, D8) e controlli ZIP/JSON/JNI,
blocchi e limite 5 MiB superati. Per la funzione nuova rimane la prova dispositivo.

AIX 0.0.4: 13.733.623 byte, massimo file interno 3.276.061 byte.
SHA256 43c491760fb966ddd868b63845ef9791bcd2c65269cf6a3c8c2296cefaf620fc.
SendNexa e importazione chiave privata rimangono non implementate.
