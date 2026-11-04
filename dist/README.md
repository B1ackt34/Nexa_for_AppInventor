# NexaPackagingProbe 0.0.1 — prototipo sperimentale

Scarica **NexaPackagingProbe-0.0.1.aix** in questa cartella usando il pulsante
Download del file o il collegamento Raw.

Il file è distribuito in un ramo dedicato perché le API GitHub Releases non sono
raggiungibili dall'ambiente di build. Non è una GitHub Release.

## Stato

- Build Rush 2.1.2 riuscita con Nexa Android 0.5.69.
- Controlli statici superati: descrittori dei blocchi, DEX, provider Startup,
  nomi JNI e librerie native per quattro ABI.
- Richiede Android 10/API 29 o successivo.
- Prova su Android e generazione APK da App Inventor ancora da eseguire.
- Il Companion non supporta questo packaging AAR: usare un APK compilato.
- Non implementa wallet, saldo o invio di fondi. Non usare come wallet reale.

## Prova

Importa la AIX in App Inventor, aggiungi NexaPackagingProbe, un Button e una Label.
Da Button.Click chiama Initialize. Mostra nella Label l'evento
InitializationSucceeded oppure InitializationFailed(code, message).
Compila l'APK, installalo e prova il pulsante. Il successo dimostra soltanto
l'inizializzazione nativa, non il funzionamento del wallet.

Sorgenti e istruzioni: ../packaging-probe/README.md.
Integrità del download: SHA256SUMS.
