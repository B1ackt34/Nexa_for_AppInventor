# Target aggiornamento SDK: release ufficiale 0.7.5

Questo profilo definisce il riferimento SDK e la build della candidata0.0.8,
distribuita per le prove. La baseline0.0.7 resta ricostruibile con SDK0.5.69.

## Versione scelta

Verificato il 2026-11-07 tramite release GitLab, tag Git e package registry:

- Release ufficiale: 0.7.5, tag release-0.7.5, commit
  914879c7574b7d785127206667c96d474b81395a.
- Ultimo binario Android Maven osservato: 0.7.4. Per 0.7.5, sources JAR e AAR
  con le coordinate standard restituiscono 404; la release GitLab espone sorgenti.
- Non usare main al posto del tag. Le patch successive su secure_delete,
  compattazione e segreti multi-key non sono incluse nella release scelta.

La release include controlli contro lunghezze negative/eccessive in
BCHserialized.debytes e gestione di messaggi CAPD malformati, come documentato
nelle note ufficiali. L'interruzione prima della cifratura è fuori dal perimetro
richiesto per questo aggiornamento e non ne blocca l'adozione.

## Toolchain e dipendenze

Il catalogo del tag richiede JDK21, Gradle9.7.1, compileSdk37 e NDK29.0.14206865.
La minSdk dell'estensione resta da mantenere a 29. SDK Kotlin e JNI devono avere
origine/versione coerenti: non abbinare il codice Kotlin 0.7.5 ai binari JNI 0.7.4
per far passare la build. La procedura ufficiale usa i sorgenti Nexa nativi in una
directory nexalnk adiacente al checkout, quindi richiede di fissarne e registrare
il commit quando si costruiscono artefatti locali.

Nexa AI Skills rimane fonte primaria per API e pattern:

- skills/nexa-project-setup/SKILL.md: pin esatti, compatibilità e verifica firme.
- skills/nexa-wallet-lifecycle-and-chain/SKILL.md: create/open/encrypt/lock e FastSync.
- recoveryFlow.md: gestione della frase e ciclo di vita wallet.

Corpus verificato: 3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24.
Non vengono introdotte nuove API o derivazioni delle chiavi.

## Controlli completati

- Versioni e provenance della release verificate.
- Gradle9.7.1 scaricato dalla distribuzione ufficiale e SHA256 verificato:
  acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a.
- NDK, SDK37, CMake e build tools predisposti in scratch, isolati dalla toolchain
  del progetto distribuito.
- Configurazione Gradle della release e task Android: completata con successo.
- Compilazione Android Kotlin/Java dal tag 0.7.5: completata con successo
  (compileAndroidMain, 16 task eseguiti). Non comprende il link dei binari JNI.
- Adapter Java corrente compilato prima contro la distribuzione Maven 0.7.4 e
  poi contro le classi 0.7.5 appena compilate, usando il classpath esatto esportato
  dal task Kotlin Android 0.7.5: entrambi i controlli di compilazione passano.
  Nessuna sostituzione di dipendenze precedenti nel secondo controllo.

## Stato della candidata e passi rimanenti

Il blocco di rete è risolto nel nuovo ambiente: Boost e SQLite scaricati e
verificati con gli SHA256 upstream. Build JNI/AAR completata su tutte quattro ABI;
grafo runtime esportato con 53 artefatti; candidata0.0.8 compilata con Rush e D8.
Superati 49 test del controller, confronto delle firme con0.0.7, controlli JNI,
ZIP, limite5MiB e parsing JSON con gli strumenti MIT. Dettagli e provenienza in
CANDIDATE-0.0.8.md e SDK-PROVENANCE-0.0.8.json.

I sorgenti baseline e il wallet installato restano invariati. La candidata è
distribuita sul ramo dedicato per la verifica. Restano la compilazione APK del progetto App Inventor
reale e le prove di esecuzione/migrazione con un wallet testnet separato. La
compatibilità con il servizio MIT compileSdk36 non è dimostrata dalla sola build
AIX, benché Rush abbia completato la compilazione della candidata.

La pubblicazione autorizzata riguarda artefatti e profilo di build sul ramo
dedicato. La configurazione cloud non viene pubblicata o modificata in questa
operazione. Rapporti privati e dati personali sono esclusi.

## Checkpoint locale di ripresa

Sorgenti del tag in /workspace/scratch/nexa-sdk-0.7.5; sorgenti nativi richiesti
dalla procedura CI in /workspace/scratch/nexalnk (dev fissato al commit
b0283446b9cecb40da1ba3ead36c9cf7e721200f). Nessun binario ufficiale 0.7.5 è stato
sostituito con una build nativa 0.7.4. Prima di distribuire una build locale andrà
registrata questa provenienza e verificata la compatibilità JNI.

Archivio dei sorgenti esatti del tag preservato in scratch, SHA256
6adf645d2d4024328e44c937663b7005feaef37dd25b0267b28c77b2adb90236.
Log di configurazione, compilazione e classpath in
/workspace/scratch/nexa-security-review/latest-sdk/.
Toolchain in /workspace/scratch/android-sdk-next e /workspace/scratch/gradle-9.7.1.
Non considerare tali directory un contratto di ripristino: se una nuova macchina
non le conserva, ricostruirle dalle versioni/checksum dichiarati e dai sorgenti
ufficiali. Non creare nuovi worktree come requisito di setup.
