# Candidata 0.0.8 — SDK Nexa 0.7.5

Candidata distribuita sul ramo dedicato per le prove. La baseline già verificata
sul telefono resta 0.0.7 con SDK0.5.69; il nuovo runtime non è ancora collaudato.

## Cosa cambia

- libnexakotlin aggiornato al tag ufficiale release-0.7.5,
  914879c7574b7d785127206667c96d474b81395a.
- SDK Kotlin, JNI e nexacli compilati dai sorgenti del tag, perché il Maven
  ufficiale offriva ancora Android0.7.4. Non è un binario ufficiale scaricato da
  Maven, e non usa JNI0.7.4. Sorgente nativo Nexa fissato a
  b0283446b9cecb40da1ba3ead36c9cf7e721200f, come previsto dal layout CI upstream.
- Grafo runtime di 53 artefatti. Versioni e checksum in SDK-PROVENANCE-0.0.8.json.
- Proprietà di sola lettura LibNexaVersion: restituisce "0.7.5" per identificare
  il contenuto della nuova build. È metadato applicativo, non una nuova API Nexa.
- Firme dei blocchi esistenti e codice del controller conservati. Nome DB,
  rete testnet, passphrase e guardie contro sostituzione automatica non cambiano.

Le patch del tag includono controlli sulle lunghezze dei dati serializzati e
messaggi CAPD malformati. Non vengono attribuite a questa release le patch
successive su main. L'interruzione iniziale prima della cifratura è esclusa dal
perimetro di questo aggiornamento secondo la scelta dell'utente.

## Verifiche eseguite

- Build Kotlin/Android e JNI: superata, tutte quattro ABI (arm64-v8a, armeabi-v7a,
  x86, x86_64), con NDK29.0.14206865.
- Build Rush2.1.2, shrinking, rilocazione, desugaring e D8: superata.
- 49 test controller con backend finti: superati. Non eseguono il vero SDK/Android.
- Firme di metodi/eventi/proprietà confrontate con AIX0.0.7: conservate; aggiunta
  soltanto LibNexaVersion.
- Provider Startup, nomi JNI e otto file nativi: presenti; bytes delle librerie
  uguali a quelli prodotti dalla build nativa fissata.
- Tutte le voci interne inferiori a 5MiB; ZIP integro e descrittori JSON letti
  anche con ZipInputStream e parser org.json usato da MIT.

File: dist/NexaPackagingProbe-0.0.8.aix, 14.964.417 byte.
SHA256: c4af001a0841fa164cf0100299872f6a117060c1e0f99bde26666ee89a5bfb3c.

## Prove ancora da fare

Non è stata eseguita la build finale del progetto App Inventor dell'utente né
l'app sul telefono con questo SDK. Il SDK upstream dichiara compileSdk37, mentre
Rush/MIT corrente usa36: la build della AIX passa, ma non sostituisce la prova del
compilatore APK effettivo. Non dichiarare questa compatibilità già dimostrata.

Prima dell'app personale usare un progetto testnet separato per inizializzazione,
creazione, backup, riapertura, password errata/corretta, saldo, FastSync e ritorno
dal background. Per una prova di riapertura dal vecchio SDK usare un wallet
usa-e-getta creato con0.0.7 e aggiornare lo stesso progetto di prova a0.0.8, senza
cancellare dati. Non usare la migrazione del wallet personale come prima verifica.
La frase di backup deve restare privata; nessun seed/password/database è richiesto
per raccogliere l'esito. Dopo la validazione si potrà aggiornare l'app personale
con la stessa chiave di firma e package, preservando i dati Android.

## Ricostruzione

Il profilo è isolato: il progetto baseline e la diagnostica0.1.0 restano
ricostruibili con il grafo precedente. Non mescolare il nuovo grafo da53 artefatti con quello precedente da47.

1. Preparare il tag e i sorgenti nativi fissati; eseguire libnexa/prepnexa.sh.
   I due archivi Boost/SQLite sono stati verificati con gli SHA256 upstream.
2. Eseguire :libnexajni:assembleRelease e :libnexakotlin:bundleAndroidMainAar.
3. Eseguire :libnexakotlin:exportAndroidRuntimeArtifacts con l'init script
   export-runtime.gradle e -PsdkUpgradeArtifactsOutput=/percorso/artifacts.json.
4. Eseguire prepare_candidate.py --sdk-checkout /percorso/sdk
   --artifacts-json /percorso/artifacts.json --output /percorso/candidata-nuova.
5. Nella candidata: rush build, poi python3 tools/compat_aix.py.
6. Eseguire inspect_candidate.py sulla AIX, passando --baseline-aix /percorso/NexaPackagingProbe-0.0.7.aix e
   --jni-aar /percorso/libnexajni-release.aar; eseguire tools/test_wallet_session.sh nella candidata.

Non usare path esistenti come output della preparazione: il comando li rifiuta
per proteggere file locali. Versioni toolchain e API/documentazione primaria sono
riportate nel README di questo profilo e nei commenti della baseline.
