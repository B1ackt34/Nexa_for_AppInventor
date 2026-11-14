# Candidata testnet 0.0.11 — verifica storage no_backup

Data: 2026-11-14. SDK0.7.5 e otto JNI invariati. Provenienza, dipendenze e hash
sorgenti in SDK-PROVENANCE.json; AIX/ZIP in ../dist con SHA256SUMS aggiornato.
Questa candidata non migra né apre dati legacy: solo nuove app testnet distinte.

## Verifiche completate

- 102 test del controller: 49 originali, 30 pagamenti, 23 ripristino. Backend fittizio.
- 36 nuovi test su file temporanei reali: inizializzazione e riapertura, conferma
  backup, importi Long esatti e byte approvati, stati del journal, rifiuto di
  database senza metadati e viceversa, sidecar orfani, residui temporanei,
  corruzione/checksum/tipi/versione/dimensione, errori fsync directory, symlink,
  percorso non valido, riconoscimento di otto artefatti legacy senza modificarli.
  Integrazione controller/file: journal non confermato impedisce SDK handoff;
  errore dopo handoff conserva il pagamento e non rilascia gli input.
- Questi test eseguono scritture/sync/rinomine reali sulla filesystem JVM Linux,
  con directory sync JDK. Non provano android.system.Os o SQLite sul telefono.
- 7 test del validatore SDK con frasi pubbliche, SHA256 JDK e sink Log di test.
- Totale: 145 test superati; nessun nuovo test strumentato Android eseguito.
- Build Rush2.1.2/JDK21, desugaring, D8 e AIX riuscita.
- Firme dei blocchi 0.0.10 invariate: aggiunti solo GetStorageStatus e StorageStatus.
- Archivio integro; entry sotto 5MiB; DEX, Startup e nomi JNI conservati;
  otto .so identici al JNI congelato su quattro ABI.
- Riconfezionamento preserva payload DEX/classi/binari; parsing Java ZipInputStream
  e org.json MIT riuscito.
- Guide IT/EN aggiornate con stesso inventario API, errori e limiti della candidata.

## Ancora da eseguire

APK in progetto distinto: fsync directory Android, risoluzione effettiva del
percorso SDK/driver, presenza del DB/sidecar/metadati sotto no_backup, assenza di
copie legacy dei dati trasferiti, creazione/recupero, riapertura e sync, pagamento
con journal riaperto e ritrasmissione. Prima prova in NO-BACKUP-TEST.md.

GetStorageStatus osserva percorsi e file, non esegue backup. Un indicatore true
per un file assente riguarda il percorso previsto; non prova la sua creazione.
La diagnostica è priva di segreti, non è uno snapshot atomico e non certifica
la durabilità dopo un errore I/O. Backup cloud e D2D richiedono prove separate.
Non sono garantiti tutti i trasferimenti proprietari dei produttori.

## Perimetro

Nessuna modifica al manifest del servizio MIT, nessuna nuova libreria Nexa,
nessun permesso aggiuntivo e nessuna migrazione del wallet principale. La prima
scrittura SDK prima della cifratura resta non atomica. File parziali e scritture
non confermate bloccano operazioni senza cancellazioni o fallback. La directory
no_backup non cifra i dati e non rimuove backup già esistenti.

Database blockchain, log e altri dati SDK/app non sono dichiarati spostati o
protetti. Il precedente gate della policy di manifest mantiene il proprio
significato: non è stato alterato per ottenere un PASS tramite questo cambiamento.
Le prove manuali della baseline restano in history/VALIDATION-0.0.10.md; non sono
attribuite alla 0.0.11. Nessuna frase o database personale usato nelle nuove prove.
