# Nexa Storage Diagnostic 0.1.0

APK Android autonomo per esperimenti sullo storage Nexa 0.5.69, separato
dall'estensione e dall'app del wallet. Android 10+ (minSdk29, target/compile36).
Il package è `org.nexa.storage.diagnostic`, il nome DB fisso è
`nexa_storage_diagnostic_v1.db`. Non accetta nomi wallet, credenziali, intent-data,
file o chiavi personali; non mostra indirizzi e non offre invio o importazione.
Non è un prodotto ufficiale MIT/Nexa, né un wallet da usare per fondi.

## Prima prova sul telefono

1. Installare NexaStorageDiagnostic-0.1.0.apk come app distinta; aprire
   **Nexa Storage Diagnostic**. L'app principale con la 0.0.7 resta installata.
2. Premere **1. Crea e controlla prima della cifratura** e attendere il rapporto.
   Il wallet diagnostico viene lasciato intenzionalmente non cifrato in questa
   fase, per osservare il primo salvataggio SDK. Non inserire o inviare fondi.
3. Premere **2. Cifra e controlla i residui** e attendere il nuovo rapporto.
4. Premere **Chiudi processo diagnostico**, poi riaprire questa app dall'icona.
   Non usare soltanto Home: non garantisce il riavvio del processo.
5. Premere **3. Riapri dopo riavvio del processo**.
6. Usare **Copia rapporto senza segreti** e incollare il testo nella chat.
   Le osservazioni dei passi precedenti sono conservate nel rapporto, fino a 12.

Non occorre aspettare la sincronizzazione della catena. `newWallet` può avviare
la normale connessione SPV testnet del SDK, ma questo esperimento non misura
saldi o sincronizzazione. Non esistono endpoint diagnostici di upload.

## Interpretazione

- `phraseUtf8Found=true` indica la presenza dei byte UTF-8 esatti della frase di
  questo wallet usa-e-getta nel file o nel log osservato. Nessuna parola o chiave
  viene restituita nel rapporto.
- `positiveControl=DETECTED` nella prima osservazione conferma che il metodo ha
  rilevato il riferimento prima della cifratura. Se manca, il confronto successivo
  è inconcludente: la rappresentazione può differire oppure il file può cambiare.
- `PLAINTEXT_REFERENCE_OBSERVED` dopo cifratura rileva il riferimento ancora leggibile
  in almeno un file controllato; non significa che il wallet non sia cifrato o che
  ogni altra app possa accedere al sandbox.
- `NO_MATCH_DOES_NOT_PROVE_ERASURE` significa solo nessuna corrispondenza osservata.
  Non dimostra bonifica delle pagine precedenti, flash, seed, chiavi private,
  rappresentazioni alternative o log non disponibili.
- `ABSENT_AT_CHECK` significa che quel file non esisteva in quel momento;
  `INCOMPLETE_OR_CHANGED`, `READ_FAILED`, `UNAVAILABLE` sono controlli incompleti,
  non esiti negativi di sicurezza.
- `encrypted=true` e `locked=true` sono stati SDK, non prove di assenza di residui.

Sono letti solo il DB diagnostico e i suoi suffissi -journal/-wal/-shm, senza
query SQLite, checkpoint, VACUUM, secure_delete o modifiche ai file per il test.
La lettura è bounded (64 MiB per file) e non atomica; anche metadati stabili non
escludono scritture concorrenti con uguale dimensione e timestamp.
Il logcat è limitato al PID attuale e al ring buffer disponibile (4 MiB, timeout).
Nessun permesso READ_LOGS viene richiesto. Dopo un riavvio non copre i log del
processo precedente e non si provocano errori crittografici per forzare log segreti.

La password diagnostica è una costante pubblica nel sorgente, non una credenziale
utente. Serve solo a riaprire il DB usa-e-getta dopo un arresto; non viene scritta
come valore nei report. La frase generata passa solo al lettore binario in memoria;
non viene copiata in UI, clipboard, report o file di riferimento. Le String JVM e
le copie interne SDK non hanno una garanzia di azzeramento: fa parte dei limiti
che stiamo studiando. I buffer del lettore vengono svuotati al termine.

## Prove di interruzione — dopo aver raccolto il test normale

Prima di ogni scenario ripartire con dati vuoti **solo dell'app Nexa Storage
Diagnostic** dalle impostazioni Android. Copiare prima i rapporti da conservare.
Non cancellare dati dell'app del wallet personale. Il codice diagnostico non
elimina mai automaticamente un DB per ripartire.

- **Durante creazione:** da dati vuoti, premere il pulsante corrispondente,
  confermare e riaprire l'app dopo la chiusura; premere 3.
- **Subito dopo creazione:** da dati vuoti, usare questo pulsante; riaprire e
  premere 3. L'interruzione viene richiesta dopo il ritorno di newWallet e prima
  della lettura della frase/cifratura da parte della diagnostica.
- **Durante cifratura:** da dati vuoti, eseguire prima 1, poi il pulsante dedicato;
  riaprire e premere 3.

Le interruzioni temporizzate richiedono killProcess dopo circa 25 ms dall'armamento.
Scheduling e durata delle chiamate SDK possono far terminare l'operazione prima
che il processo muoia. `phaseBeforeOperation` registra l'ultima fase salvata:
non prova l'istruzione SDK esatta interrotta, la consistenza SQLite o un taglio
fisico dell'alimentazione. Ripetere su dati nuovi se necessario.

Se il DB manca, è incompleto o non si apre, il rapporto riporta un codice filtrato.
Non c'è fallback verso un wallet nuovo. Nessuna prova qui chiude automaticamente
la revisione di sicurezza della 0.0.7 o del SDK.

## Architettura e fonti delle API

- ProbeProtocol: sequenza dell'esperimento e guardie applicative, con backend
  sostituibile per test di ordine/errori; non descrive nuove API Nexa.
- DiagnosticEngine: adapter SDK, journal delle fasi in SharedPreferences (solo
  metadati), ricerca binaria limitata e report JSON con campi predefiniti.
- NeedleScanner: ricerca streaming con prefissi, senza restituzione dei byte letti.
- DiagnosticActivity: interfaccia Android standard e FLAG_SECURE, lavoro su executor,
  nessun campo per segreti. La clipboard riceve solo il report filtrato.

Fonte primaria: Nexa AI Skills commit
3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24:

| API SDK usata | Fonte |
| --- | --- |
| initializeLibNexa("") | [wallet lifecycle, Setup and versions](https://gitlab.com/nexa/nexaaiskills/-/blob/main/skills/nexa-wallet-lifecycle-and-chain/SKILL.md) |
| newWallet(name, NEXATESTNET, null), Bip44Wallet | Stessa skill, pattern 1 e mental model; [walletStartupTemplate.kt](https://gitlab.com/nexa/nexaaiskills/-/blob/main/skills/nexa-wallet-lifecycle-and-chain/walletStartupTemplate.kt) |
| openDisconnectedWallet(name) | Stessa skill, pattern 7; [recoveryFlow.md](https://gitlab.com/nexa/nexaaiskills/-/blob/main/skills/nexa-wallet-lifecycle-and-chain/recoveryFlow.md), Connecting the chain |
| recoverySecret / getRecoverySecret() | recoveryFlow.md, Create flow; pattern 6 per accesso dopo unlock |
| encrypt, unlock, lock, lockedState | Skill pattern 6; recoveryFlow.md, Lock/unlock |
| saveBip44Wallet | recoveryFlow.md, Create flow passo 6 |
| Context.getDatabasePath(name + ".db") | walletStartupTemplate.kt, controllo Android prima di aprire |

La creazione impone NEXATESTNET; la riapertura riguarda solo il DB fisso generato
da questa app. Non accetta file wallet esterni né si ricollega a una catena durante
la riapertura.

Le forme Java sono gli accessor compilati del Kotlin 0.5.69 e sono state verificate
compilando contro il JAR ufficiale. Il provider `org.nexa.libnexakotlin.Initialiser`
è registrato come nell'AndroidManifest.xml dell'AAR ufficiale; fornisce il Context
SDK tramite AndroidX Startup, senza setter inventati. R della libreria Startup è
generato da aapt2 insieme alla sua risorsa originale.

Librerie: stesso grafo di 47 artefatti della 0.0.7, fissato in DEPENDENCIES.json e
verificato anche contro i checksum Gradle del repository. Nessuna libreria Nexa
aggiunta o aggiornata. Android Activity, filesystem, logcat, Process e clipboard
sono strumenti della diagnostica Android, non API Nexa. Non si usa un'API di
bonifica, una KDF diversa o una chiamata di lettura seed non documentata.

## Build equivalente e verifiche

La diagnostica non è un'estensione App Inventor: produce direttamente l'APK con
javac17, aapt2/zipalign SDK36, D8 9.4.24 e apksigner, usando JDK21 completo.
Il progetto wallet continua a compilare con Rush, invariato.

Prima risolvere il grafo frozen in packaging-probe/dependency-resolution con
`gradle --no-daemon resolveAndroidGraph`. Poi:

```sh
JAVA_HOME=/percorso/jdk21 tools/test.sh
python3 tools/build.py \
  --java-home /percorso/jdk21 \
  --mit-checkout /percorso/appinventor-sources \
  --r8-jar /percorso/r8.jar \
  --signing-dir /percorso/privato/esterno-al-repository
```

La build controlla i checksum, compila la risorsa Startup e il codice, genera DEX
senza rinominare JNI, incorpora risorse runtime e otto binari nativi originali,
allinea e firma l'APK. La chiave diagnostica generata resta nella directory privata
esterna, con permessi restrittivi; non è la chiave dell'app personale e non viene
pubblicata. La build verifica firma, allineamento e policy statica backup.
Il checkout MIT usato è b097193903890d6aa729ac52464ae342d3a600e2.

18 test desktop scanner/protocollo superati; non eseguono Android o l'SDK reale.
Build, firma v3, package separato, min29/target36, backup escluso staticamente e
identità dei binari nativi verificati. Nessun dispositivo/emulatore disponibile
qui: l'esecuzione reale e i rilievi sui residui restano da verificare sul telefono.
Non verificati con questa build backup/restore reali o compatibilità su dispositivi
con pagine di memoria da 16 KiB; i binari SDK restano quelli già fissati.

Certificato diagnostico SHA256:
329b2389d232d6c7a2c2bf9364ad0e83b3be7db438094c881b3b393409d758ab.
Non equivale al certificato del wallet personale.
