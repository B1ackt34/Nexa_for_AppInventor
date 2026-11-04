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
