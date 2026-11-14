# Candidata 0.0.11: decisioni prima del codice

2026-11-14. Attua lo studio NO-BACKUP-STORAGE-STUDY.md per nuovi wallet testnet.
Baseline 0.0.10 preservata. Stessi SDK0.7.5/JNI e 53 dipendenze; nessuna nuova API
Nexa, nessun permesso Android aggiuntivo o intervento sul compilatore MIT.

Database e sidecar passano nella directory privata restituita da
Context.getNoBackupFilesDir(). I percorsi assoluti vengono calcolati ogni volta;
nome logico del wallet e blocchi esistenti invariati. API Nexa: newWallet,
recoverWallet, openDisconnectedWallet, già documentate nella skill lifecycle,
con instradamento del parametro verificato in init.kt/persist.kt/platformAndroid.kt.

Conferma backup e journal approvato vengono conservati in un unico metadato
locale versionato, non nelle SharedPreferences ordinarie. Protocollo di scrittura:
file temporaneo nella stessa directory, sync del file, rinomina atomica e fsync
della directory. Android Os.open/fsync/close forniscono il sync della directory;
sono API Android, non API Nexa. Errori non autorizzano la consegna del pagamento
all'SDK. Non promettere successo se la scrittura non è confermata.

Database legacy, sidecar o preferenze precedenti bloccano l'accesso con errore
esplicito: nessuna migrazione, eliminazione, copia o fallback. Anche un database
nuovo senza metadati, o metadati inizializzati senza database, sono un errore
da diagnosticare. Un file temporaneo rimasto dopo un'interruzione non viene
eliminato automaticamente per riprendere pagamenti.

GetStorageStatus/StorageStatus restituiscono diagnostica di soli ruoli/booleani
di collocazione, senza percorsi personali, chiavi, frase o byte delle transazioni.
Non dichiarano riuscita una prova di backup che non è stata eseguita.

Verifiche: regressioni controller/validatore; persistenza metadati esatti,
errore dopo rinomina, file parziali e symlink; firme blocchi precedenti e JNI;
build Rush/AIX. Poi APK distinto sul telefono: percorsi, creazione/recupero,
riapertura, invio/ritrasmissione e backup/trasferimento controllato. Aggiornare
entrambe le guide. La protezione vale per i dati spostati, non per ogni file
dell'app, copie TinyDB, log o trasferimenti OEM non verificati.
