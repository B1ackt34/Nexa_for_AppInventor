# SendNexa — progetto della prima versione testnet

Baseline funzionale confermata dall'utente: estensione0.0.8, SDK0.7.5, creazione,
riapertura, indirizzo/saldo, FastSync, background e migrazione0.0.7→0.0.8. Solo testnet.

## Fonti e API

Nexa AI Skills commit3c6f3607a02f7fabe2403d3430dbbdb2d7f85d24. Firme Java verificate
con javap sulle classi Android compilate dal tag0.7.5
914879c7574b7d785127206667c96d474b81395a.

| Uso | API documentata | Fonte primaria |
| --- | --- | --- |
| Unità | 1 NEXA=100 sat, Long per sat | nexa-transaction-construction/SKILL.md, Mental model; simpleapi-cheatsheet.md |
| Destinatario | PayAddress(String), type.isValid(), chainSelector, lockingScript() | nexa-identity-and-addresses/validateHelpers.kt |
| Transazione/output | txFor(NEXATESTNET), txOutputFor(chain,amount,script) | nexa-transaction-construction/SKILL.md, pattern6; simpleapi-cheatsheet.md |
| Fondi e firma | txCompleter, FUND_NATIVE/SIGN, minConfirms | Stessa skill pattern6; txCompletionReference.md |
| Commissione effettiva | inputTotal-outputTotal | Stessa skill pattern6c |
| Invio e registrazione SDK | wallet.send(tx, sync=true, note=null) | Stessa skill, simpleapi sugar e pattern6 |
| Rilascio bozza | abortTransaction(tx) | Stessa skill, Abandoning a completed-but-unbroadcast tx |
| Identificativo/stato | tx.idem.toHex(), wallet.getTx(idem), confirmedHeight | Stessa skill pattern4/5 e antipattern identificativi |
| Ritrasmissione identica | net.broadcastTransaction(tx.toByteArray()) | Stessa skill pattern3; templates/broadcast-tx.kt |
| Recupero tx approvata | txFor(chain,BCHserialized(bytes,NETWORK)) | Stessa skill pattern5 |
| Sblocco/riblocco | unlock, lock, lockedState | nexa-wallet-lifecycle-and-chain/SKILL.md pattern6 |

Non aggiungere libnexaapp, un wallet esterno, RPC o nuove librerie Nexa. Riutilizzare
il grafo SDK0.7.5 di53 artefatti e gli otto binari JNI verificati della0.0.8.
Dipendenze Android: AlertDialog, FLAG_SECURE, SharedPreferences private, executor
e timer già disponibili con minSdk29. Nessun nuovo permesso rispetto a INTERNET e
ACCESS_NETWORK_STATE. Nessuna API blockchain estrapolata da Bitcoin/Ethereum.

## Flusso e architettura

1. SendNexa(destinatario, importoNexa, commissioneMassimaNexa, passphrase).
2. Validare testo decimale esatto (punto, massimo due decimali, >0, Long senza
   overflow), indirizzo pagabile e rete NEXATESTNET; rifiutare URI con parametri,
   token GROUP e tipi non pagabili. Verificare wallet aperto, backup confermato,
   sincronizzazione, connessione e assenza di FastSync/preparazioni concorrenti.
3. Usare fondi con almeno1 conferma. È una policy applicativa, non una regola
   universale Nexa. Costruire e firmare con txCompleter, senza trasmettere.
4. Misurare commissione reale e totale; verificare output destinatario/importo e
   tetto massimo indicato. Non sottrarre la commissione all'importo destinato.
5. Ribloccare e verificare le chiavi. Mostrare un dialogo Android protetto con
   destinatario completo, importo, commissione e totale. Nessuna API pubblica che
   aggiri il dialogo. La bozza scade dopo2 minuti con timer monotono.
6. Annullamento, scadenza o background prima della conferma: abortTransaction sulla
   sola bozza, nessun invio. I controlli devono impedire callback tardivi e doppi tap.
7. Dopo conferma salvare privatamente l'esatta transazione firmata e il suo idem
   prima di chiamare send. Un errore nel journal impedisce l'invio. Non conservare
   passphrase, seed o chiavi nel journal. Non esporre il blob firmato nei blocchi.
8. Dopo il punto di consegna al SDK l'operazione non è annullabile; il passaggio in
   background non deve liberare gli input come se l'invio non fosse avvenuto.
9. Mantenere un solo pagamento non risolto. Dopo riavvio non crearne un altro
   automaticamente: interrogare lo stato o ritrasmettere, con nuova conferma,
   soltanto gli stessi byte già approvati. Nessun cambio destinatario/importo/fee.

Componenti: WalletSession governa stato/epoch e blocco chiavi; adapter Nexa per le
sole API citate; modello puro SendData per importi/bozza/record; journal privato;
NexaPackagingProbe gestisce dialogo ed eventi sul thread UI. Un unico worker
serializza tutte le operazioni. Nessun nuovo wallet viene creato dopo un errore.

## Blocchi proposti

- SendNexa(address,amountNexa,maxFeeNexa,passphrase): prepara e apre la conferma.
- CancelSendNexa(): cancella soltanto la bozza non consegnata al SDK.
- GetLastSendStatus(): stato SDK dell'ultimo pagamento approvato.
- RetryLastSendNexa(): propone la ritrasmissione identica, mai un nuovo pagamento.
- SendNexaPrepared(requestId,address,amount,fee,total): informativo; non autorizza.
- SendNexaSubmitted(idem): consegna richiesta al SDK, non accettazione della rete.
- SendNexaCancelled(requestId,reason).
- SendNexaStatus(idem,state,confirmedHeight).
- Errori filtrati in WalletFailed; nessun messaggio SDK grezzo o credenziale.

## Limiti verificati del SDK0.7.5

send(tx,true,null) restituisce void, registra il tx localmente e programma l'invio
P2P. Nel sorgente wallet.kt:3230-3260 e3432-3440, sync=true non equivale a un ACK
remoto. cnxnmgr.kt:640-665 broadcastTransaction può limitarsi a registrare un warning
quando non ci sono peer e cattura errori P2P. Il wrapper dei template Skills non
va usato per dedurre accettazione dal solo ritorno senza eccezione.

confirmedHeight=-1 può riferirsi alla cronologia inserita localmente dallo stesso
send: chiamarlo LOCAL_UNCONFIRMED, non NETWORK_ACCEPTED. CONFIRMED richiede la
lettura SDK>=0; non significa finalità irreversibile. Long.MIN_VALUE indica
rimozione/invalidazione e non autorizza un nuovo pagamento automatico.

Esito incerto/record corrotto: conservare il guard, mostrare l'identificativo se
valido, non cancellare il wallet o usare cleanUnconfirmed/cleanReserved alla cieca.
Il caso di un tx definitivamente rifiutato può richiedere una procedura di recupero
successiva: la prima versione privilegia evitare duplicati e non sblocca il guard
sulla sola assenza del tx dalla cronologia locale.

## Piano di sviluppo e collaudo

1. Congelare la baseline0.0.8 verificata e usare il profilo SDK0.7.5, senza mescolare
   JNI precedenti. Lasciare ricostruibili gli artefatti storici e la diagnostica.
2. Implementare modelli/importi e test di zeri, negativi, precisione, separatori,
   esponenti e overflow; regole di stato e journal con backend finto.
3. Implementare adapter e controlli SDK; verificare firme compilando contro0.7.5.
4. Integrare dialogo e lifecycle: annullamento durante preparazione, scadenza,
   background, errori di lock, doppia richiesta e doppia conferma.
5. Verificare persistenza prima del broadcast, crash prima/dopo la consegna,
   ripresa senza ricostruzione, retry identico e blocco su record corrotto.
6. Test di fondi insufficienti, fee oltre tetto, input non confermati, perdita di
   connessione e stato SDK negativo; regressione del ciclo wallet già verificato.
7. Build Rush/D8 e controlli AIX, metadati/JNI e ABI. Nessun invio di transazioni
   dall'ambiente cloud o uso di credenziali personali durante i test automatici.
8. APK su progetto testnet separato, con piccolo importo deciso dall'utente:
   verificare prima rifiuto/annullamento, poi un invio confermato dall'utente,
   ricezione nel wallet destinatario, commissione effettiva e stato dopo riavvio.

Le API mancanti non verranno inventate. Il requisito di prima scrittura cifrata,
già escluso dall'utente, non viene riaperto in questo lavoro. Il journal non risolve
le impostazioni di backup dell'APK: valgono i limiti della pipeline già documentati.
