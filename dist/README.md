# NexaPackagingProbe 0.0.2 — prototipo sperimentale

Scarica **NexaPackagingProbe-0.0.2.aix** in questa cartella usando il pulsante
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


## Variante 0.0.2 per server con limite di 5 MiB

La 0.0.1 è conservata solo come riferimento. La 0.0.2 mantiene ogni file interno
sotto 5 MiB. Classi, DEX e file nativi sono invariati; i JAR suddivisi sono
registrati nei metadata delle librerie App Inventor. Il contenitore completo
resta circa 14 MB: il limite qui affrontato è quello dei singoli file interni.

MIT ha corretto il limite da 5 a 15 MiB nel commit
b097193903890d6aa729ac52464ae342d3a600e2. Non è verificato se il servizio ai2a
abbia quella correzione. L'errore di importazione null è compatibile con questa
causa, ma non la dimostra. La nuova importazione richiede ancora una prova utente.
