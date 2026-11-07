# Baseline testnet confermata dall'utente

Estensione: NexaPackagingProbe0.0.8. SDK: libnexakotlin0.7.5, tag ufficiale
914879c7574b7d785127206667c96d474b81395a, build e provenienza già pubblicate nel
profilo packaging-probe/sdk-upgrade.

Prove riferite dall'utente nella conversazione, successive alla distribuzione:

- APK compilato/installato; LibNexaVersion restituisce0.7.5.
- Creazione wallet testnet, conferma backup, indirizzo/saldo e riapertura riusciti.
- Ritorno dal background e FastSync funzionanti.
- Progetto usa-e-getta aggiornato0.0.7→0.0.8 senza cancellare dati: stessa
  passphrase, stesso indirizzo e saldo atteso.
- App del wallet principale aggiornata: indirizzo e saldo corretti, synced.

Si adotta0.0.8 come baseline funzionale testnet. Questi esiti sono conferme
manuali dell'utente, non prove strumentate eseguite dall'agente o un audit di
sicurezza. I report di build originari restano fotografie del momento precedente
al collaudo e non vengono riscritti per attribuire test che allora non erano stati
eseguiti. Invio, firma e ritrasmissione di pagamenti non erano ancora presenti in
questa versione: SendNexa richiede sviluppo e collaudo dedicati.
