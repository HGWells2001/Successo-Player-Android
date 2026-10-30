# Successo Player Android

Player Android non ufficiale dedicato a **“Successo. Storie e voci dal Novecento”**.

## Versione corrente

**v5.3 – Sei miglioramenti per l'ascolto quotidiano**

## Novità della v5.3

- **Mini-player navigabile:** SeekBar trascinabile per cercare una posizione, anteprima temporale durante il movimento, senza modificare Play/Pausa. Un feedback animato di 170 ms conferma il cambio di stato.
- **Download più chiari:** pulsante con icone di download, download in corso, completato e errore; indicatore nelle righe basato sulle registrazioni realmente completate di DownloadManager, ricaricato all'apertura e dopo cancellazioni. Protezione dai doppi download.
- **Dettagli puntata:** icona dedicata in ogni scheda, descrizione leggibile e selezionabile, durata, data, numero, avanzamento e pulsanti Ascolta/Scarica.
- **Ordinamento del catalogo:** più recenti, meno recenti, durata maggiore o più avanzate. Impostazione memorizzata, accessibile dal menu ⋮ senza modificare la coda audio.
- **Iconografia coordinata:** nuovi controlli vettoriali per Precedente, −30, Play/Pausa, +30, Successiva, volume ed espansione mini-player.
- **Schermi più piccoli:** pulsanti da almeno 44–48dp, titoli e contatori troncati in sicurezza, filtri abbreviati quando necessario, layout compatto in orizzontale e per caratteri ingranditi.
- **Non introdotta** la nuova scheda «Riprendi ascolto» richiesta di escludere; restano invariati i normali segnalibri di ascolto.
- **Versione aggiornabile:** build 5.3 con versionCode base 53000, chiave di firma permanente e rilevamento tramite GitHub Releases.

## Novità della v5.2

**v5.2 – Icone vettoriali personalizzate per i comandi**

## Novità della v5.2

- **Tre icone vettoriali originali** al posto dei simboli Unicode/emoji: Download, Libreria degli episodi scaricati e Shuffle.
- Tratto uniforme e arrotondato, stile coerente con i pulsanti verde bosco e rame, senza dipendenza dal font emoji di Android.
- Shuffle segnala l'attivazione tramite superficie verde e contrasto dell'icona, senza aggiungere il testo «ON».
- Dimensione touch di 48dp invariata, accessibilità mantenuta, nessuna modifica alla logica dei download o dell'ascolto.
- APK versione 5.2 firmato con la stessa chiave; aggiornamento nell'app tramite GitHub Releases.

## Novità della v5.1

**v5.1 – Restyling compatto, schede puntate e mini-player raffinato**

## Novità della v5.1

- **Temi giorno/notte rinnovati**: verde bosco, carta chiara, accenti in ottone e contrasti ottimizzati.
- **Elenco puntate a schede** con spazio tra gli episodi, titoli più curati, barre di progresso sottili e indicatore verticale per la puntata selezionata.
- **Mini-player sempre visibile**: icona musicale, pulsante Play/Pausa prioritario, comandi secondari più discreti e avanzamento dorato, senza nuovi pannelli invasivi.
- **Header e filtri armonizzati**: caratteri più leggibili, marca verticale, icona ⋮ discreta, filtri evidenziati solo se attivi.
- **Riproduzione a 1×**: il vecchio selettore della velocità resta rimosso. Shuffle, preferiti, diretta, Media3, download e timer invariati.
- **Aggiornamento sul telefono**: versionCode base 51000, superiore alle build 5.0, APK distribuito tramite GitHub Releases con la firma permanente.

## Novità della v5.0

## Novità della v5.0

- **Mini-player sempre visibile in basso**, anche durante lo scorrimento del catalogo: titolo dell'episodio, tempo trascorso/durata, stato Play/Pausa, Successiva e barra di avanzamento.
- **Controlli completi espandibili** con il tasto ⌃/⌄ nel mini-player. Per impostazione predefinita rimangono chiusi, lasciando spazio alla lista. La scelta viene memorizzata.
- **Elenco più leggibile** con titolo da 16sp, separazione e spaziatura migliorate, progresso in ogni riga: da ascoltare, percentuale con punto di ripresa oppure ascoltata.
- Il progresso degli episodi deriva dai **segnalibri originali** (`episode_resume_ms_`) e dallo stato del player Media3, senza cancellare i dati preesistenti né introdurre duplicazioni.
- Aggiornamento della barra per la puntata corrente ottimizzato: l'elenco non viene ridisegnato ogni mezzo secondo, ma soltanto al cambiamento della percentuale.
- **Menu ⋮ organizzato** in tre gruppi separati (Catalogo: Ricerca e Cronologia; Riproduzione: Coda e Timer; App: Aggiornamenti), con indicazione della coda e del timer attivo. Su Android 9+ le sezioni sono separate visivamente.
- Conservati Shuffle, preferiti, download offline, diretta, ricerca, cronologia, coda, timer, notifiche Media3 e aggiornamenti automatici.
- Eliminato completamente il menu a tendina della velocità: le puntate vengono riprodotte a 1×, senza recuperare vecchie preferenze di velocità.
- Versione 5.0 resta compatibile con il pacchetto e la firma delle versioni precedenti: è un normale aggiornamento.

## Novità della v4.25

- AudioPlaybackService ora usa **MediaSessionService Media3**, con un ExoPlayer stabile durante l'intera vita del servizio e un'unica sessione.
- Notifica e schermata di blocco gestite da Media3; metadati titolo, autore e copertina incorporati nel MediaItem.
- Play, Pausa, Stop, Successiva, Precedente e Seek dei controller esterni inoltrati alla logica esistente di playlist, segnalibri, Shuffle, timer sonno e coda.
- Il collegamento UI con MainActivity resta compatibile; il binder locale rifiuta richieste da UID estranei.
- Il servizio permette il controllo tramite cuffie e dispositivi Media3 e conserva l'uscita esplicita dell'utente senza continuare la riproduzione.
- Diretta automatica separata e non modificata. MinSdk/targetSdk e firma di aggiornamento invariate.
- Test automatici sulla struttura Media3 e regressioni esistenti; la prova sui dispositivi Bluetooth reali rimane necessaria.

## Novità della v4.24

**v4.24 – Motore puntate Media3 / ExoPlayer**

## Novità della v4.24

- Migrazione incrementale del motore delle puntate da Android MediaPlayer a Media3/ExoPlayer 1.11.1. La diretta programmata rimane sul servizio separato per ridurre i rischi.
- Mantiene la sessione media e i comandi della notifica, segnalibri per puntata, download offline, timer sonno, Shuffle e coda.
- Media3 gestisce buffering e ritentativi HTTP; dopo un errore di rete definitivo sono disponibili al massimo tre tentativi aggiuntivi distanziati, con ripresa dalla posizione corrente.
- Scollegare le cuffie o un dispositivo Bluetooth ferma la riproduzione per evitare audio inatteso dagli altoparlanti; la posizione viene salvata.
- Il focus audio viene richiesto prima di avviare o riprendere l'episodio. Una chiamata non deve avviare audio se il focus è negato; la pausa durante il caricamento annulla i callback pendenti.
- Il player mantiene il risveglio necessario al servizio in background usando il wake lock locale di Media3, senza nuovi permessi.
- Test automatici aggiunti per le politiche di riconnessione, i confini di pausa e la migrazione API.
- Nessuna modifica al certificato di firma, all'identità del pacchetto o ai dati precedenti.

## Novità della v4.23

**v4.23 – Diretta informativa integrata nel pulsante**

## Novità della v4.23

- Le informazioni relative alla diretta sono ora raccolte dentro il pulsante «Diretta programma» già presente, senza occupare spazio aggiuntivo sotto la barra.
- Il pulsante mantiene esattamente **48dp di altezza** e presenta tre righe compatte: stato ON/OFF, conto alla rovescia per inizio/fine della fascia e stato della connessione.
- Le informazioni complete restano disponibili ai lettori di schermo grazie alla descrizione accessibile; il testo si adatta allo spazio disponibile.
- Conservati tutti i meccanismi della diretta, il timer da 1 secondo, le funzioni audio e il menu ⋮ a cinque voci.
- Test aggiornati per verificare che nessuna etichetta esterna sia rimasta e che le informazioni siano leggibili nel pulsante.

## Novità della v4.22

**v4.22 – Diretta con countdown e stato della connessione**

## Novità della v4.22

- Il riquadro Diretta mostra il tempo restante fino alla prossima fascia feriale delle 05:35 o alla chiusura delle 06:00, con orario italiano e gestione dei cambi d'ora.
- Lo stato della connessione riflette gli eventi del servizio: collegamento, in ascolto, riconnessione, pausa, errore e inattività. Non si presume che lo streaming sia attivo solo perché l'orario è quello previsto.
- Countdown aggiornato ogni secondo quando la schermata è aperta, senza richieste di rete aggiuntive; heartbeat dal servizio per evitare stati obsoleti.
- Nessuna nuova autorizzazione, nessuna modifica ai dati, alla firma di aggiornamento o ai cinque comandi nel menu ⋮.
- Test automatici su confini 05:35/06:00, fine settimana, cambio dell'ora e stati della connessione.

## Novità della v4.21

**v4.21 – Navigazione più sicura e aggiornamenti non invasivi**

## Novità della v4.21

- Con il pannello di ricerca aperto, il tasto **Indietro** lo richiude, azzera il filtro e mantiene l'audio in riproduzione. Solo il successivo Indietro esegue la chiusura completa prevista.
- Il controllo aggiornamenti continua a ogni accesso, ma non apre finestre quando l'app è già passata in background. Quando si rientra, viene effettuato un nuovo controllo.
- Eliminati aggiornamenti duplicati dello stato del menu e aggiunta una descrizione accessibile quando la ricerca è aperta.
- Sistemata la formattazione del messaggio di download degli aggiornamenti.
- Conservati menu ⋮ con esattamente cinque voci, segnalibri, preferiti, coda, cronologia, download, Shuffle, audio in background e firma permanente.
- Nuovo test di regressione per navigazione e ciclo di vita delle finestre.

## Novità della v4.20

- **Menu a tendina ⋮ in alto a destra**, accanto al titolo: raccoglie Ricerca, Coda, Timer sonno, Cronologia e Controlla aggiornamenti in un solo comando.
- Rimossi i due pulsanti separati di **Ricerca** e **Aggiornamenti** dall'intestazione e i pulsanti separati di **Coda**, **Timer** e **Cronologia** dalla barra di riproduzione. Il titolo ha di nuovo più spazio e i controlli audio sono più compatti.
- **Ricerca**: apre o chiude il campo sotto il titolo e conserva l'integrazione con filtri e Shuffle. Il campo appare solo quando richiesto.
- **Coda**: il menu indica quante puntate sono in attesa; restano disponibili riordino, riproduzione successiva ed eliminazione.
- **Timer sonno**: quando attivo il menu mostra tempo residuo o fine puntata, mantenendo il conto alla rovescia nel servizio audio.
- **Cronologia**: apre gli ultimi ascolti e permette ripresa e gestione dei segnalibri.
- **Controlla aggiornamenti**: esegue il controllo manuale su GitHub; la verifica automatica a ogni accesso rimane attiva.
- Le cinque funzioni mantengono le rispettive finestre e logiche originali. Nessuna modifica ai dati o ai permessi.

## Novità della v4.19

- Un piccolo pulsante **◷** nella barra di controllo apre **Ascoltate di recente**. La cronologia contiene al massimo 50 puntate, in ordine dalla più recente.
- Vengono registrati soltanto gli episodi che **hanno realmente iniziato la riproduzione**, inclusi ascolti in background, Shuffle e puntate offline. Selezionare una puntata senza ascoltarla non modifica la cronologia.
- Ogni riga mostra **titolo, data e ora dell'ultimo ascolto** e, quando disponibile, il punto da cui riprendere.
- Toccando una riga puoi scegliere **Riprendi ascolto**, **Aggiungi alla coda** o **Rimuovi dalla cronologia**.
- Il comando **Svuota** richiede una conferma; cancellare la cronologia non cancella le posizioni di ascolto, i preferiti, la coda o i download.
- La cronologia è salvata nelle preferenze dell'app e resta disponibile dopo la chiusura e la riapertura, senza nuove autorizzazioni o raccolta dati esterna.
- Il sistema di aggiornamento, la ricerca, i filtri, Shuffle, la coda e lo Sleep Timer restano invariati.

## Novità della v4.18

- Aggiunto il piccolo pulsante **☾** ai controlli del player per programmare lo **Sleep Timer** con 15, 30, 45, 60 o 90 minuti.
- È disponibile anche **Alla fine della puntata**: il lettore non avvia la coda successiva né un nuovo episodio Shuffle dopo la fine della puntata in ascolto.
- Il timer viene gestito direttamente dal **servizio di riproduzione**, usando un orologio monotono. Continua a funzionare durante la riproduzione in background e con schermo bloccato, senza richiedere che l'Activity sia visibile.
- Alla scadenza il servizio mette **in pausa**, mantenendo il segnalibro dell'episodio. Se la riproduzione è ancora in preparazione, annulla l'avvio asincrono per evitare che l'audio parta dopo la scadenza.
- L'icona mostra il tempo residuo oppure **☾ Fine**. Dal menu puoi cambiare la durata o disattivare lo Sleep Timer.
- Se passi manualmente a un altro episodio, il timer impostato su **fine puntata** viene annullato; quelli in minuti rimangono attivi.
- La chiusura esplicita dell'app e il comando Stop annullano il timer. Non è necessaria alcuna nuova autorizzazione Android.

## Novità della v4.17

- Nuova **icona di ricerca** nella barra superiore, accanto all'icona per gli aggiornamenti. Il campo di ricerca resta nascosto finché non serve.
- Ricerca in **titoli, descrizioni, date e numeri di puntata**, ignorando maiuscole e accenti. È possibile scrivere più parole in ordine diverso.
- Il filtro di ricerca si combina con **Preferite** e **Non ascoltate**, senza modificare i filtri attivi.
- **Shuffle intelligente** seleziona solo fra le puntate che corrispondono alla ricerca e agli eventuali altri filtri. Se non ci sono risultati, non avvia un'altra puntata casuale.
- La riproduzione attuale non viene interrotta quando si cambia ricerca. L'elenco mostra il numero di risultati e conserva le azioni della coda sul tocco prolungato.
- La ricerca resta disponibile dopo una ricreazione della schermata; toccando di nuovo l'icona si chiude e si cancella il filtro, evitando ricerche invisibili.
- Nessun cambiamento al sistema di aggiornamento, ai download o alla firma APK.

## Novità della v4.16

- **Coda di riproduzione**: tieni premuta una puntata per scegliere **Aggiungi alla coda**, **Riproduci come prossimo** o **Riproduci subito**.
- Il pulsante **☷** accanto a Shuffle apre la coda, con il numero di puntate in attesa.
- Dalla coda puoi **spostare un episodio su o giù, rimuoverlo, avviarlo subito oppure svuotare l'elenco**.
- La coda ha la priorità sullo Shuffle e sulla riproduzione sequenziale, anche a fine puntata in background o premendo Avanti dalla notifica e dai controlli Android.
- Gli episodi scelti manualmente possono essere fuori dai filtri attivi senza modificarli.
- L'ordine viene conservato nelle preferenze dell'app e non avvia automaticamente la riproduzione alla riapertura.
- Download offline, segnalibri individuali, aggiornamenti e diretta restano disponibili.

## Novità della v4.15

- Il pulsante **📂** apre la libreria integrata delle puntate scaricate, senza uscire da Successo Player.
- Elenco dei download completati di questa app, con titoli, dimensioni e totale occupato. Vengono rilevati anche i download delle versioni precedenti ancora presenti nel DownloadManager.
- **Ascolta offline**: usa direttamente il file locale di Android, senza richiedere una connessione streaming.
- **Elimina download**: richiede una conferma e rimuove soltanto il file scaricato, non la puntata dal catalogo.
- Il pannello offre anche **Apri cartella** per accedere al gestore file Android.
- Nessun permesso generale di accesso alla memoria: la libreria usa esclusivamente DownloadManager e i file scaricati da Successo Player.
- Restano invariati la firma APK, il controllo aggiornamenti a ogni accesso, la diretta e lo Shuffle.

## Novità della v4.14

- Risolto il passaggio incompleto dal download dell'APK all'installazione: il pulsante aggiornamenti usa adesso il **DownloadManager di Android**, senza aprire un download nel browser.
- L'avanzamento può arrivare al 100% ma l'APK non viene dichiarato pronto finché Android non segnala **`STATUS_SUCCESSFUL`**.
- Prima di proporre l'installazione, la dimensione e l'impronta **SHA-256** dell'APK vengono verificate contro il file `SHA256.txt` allegato alla GitHub Release.
- Al termine compare **Installa**: il file viene aperto con l'installer di Android dopo consenso esplicito. Se necessario Android chiede l'autorizzazione a installare app da Successo Player.
- Un download iniziato mentre l'app è aperta può continuare in background; quando l'app torna in primo piano, lo stato viene letto dal DownloadManager e l'installazione proposta se l'APK è già pronto.
- Gli errori reali del download sono mostrati chiaramente con possibilità di riprovare; nessun APK incompleto viene passato all'installer.
- Conservati l'icona discreta ↻ in alto a destra, il controllo aggiornamenti a ogni accesso, l'ascolto e i dati utente.

## Novità della v4.13

- Il controllo manuale degli aggiornamenti è tornato a essere una **piccola icona ↻ in alto a destra**, accanto al titolo, con area di tocco da 44 × 44 dp e descrizione accessibile.
- Eliminato il grande pulsante "Controlla aggiornamenti" sotto il titolo, recuperando spazio per la lista.
- Rimosso il riferimento al servizio esterno dal sottotitolo, dai messaggi di caricamento, dalle descrizioni dei download, dalle notifiche e dai metadati audio; il catalogo e la riproduzione restano invariati.
- Il controllo automatico continua **a ogni apertura o ritorno in primo piano**, senza intervallo di 24 ore.
- Restano attivi download APK da GitHub Releases, firma permanente, Shuffle intelligente e segnalibri individuali.

## Novità della v4.12

- L'app verifica la Release più recente **ogni volta che viene aperta o torna dal background**, non più soltanto una volta ogni 24 ore.
- Il pulsante "↻ Controlla aggiornamenti" ora è **ben visibile subito sotto l'intestazione**, senza dover riconoscere una piccola icona.
- Le verifiche avvengono in background e senza utilizzare una cache HTTP, evitando blocchi del player e informazioni obsolete.
- Le richieste simultanee vengono deduplicate. Installazione e download richiedono sempre l'azione dell'utente.
- Conservati firma permanente, download diretto da GitHub Releases, Shuffle intelligente e segnalibri di ciascuna puntata.

## Novità della v4.11

- Memorizzazione indipendente della posizione di ogni puntata. Passando da A a B e tornando ad A, il lettore riprende dal punto precedente.
- I segnalibri sono persistenti e non fanno ripartire l'audio automaticamente quando riapri l'app. Gli episodi terminati ricominciano dall'inizio.
- Ad ogni build firmata la CI pubblica una **GitHub Release**, comprensiva di APK con nome della build, `SuccessoPlayer_Android_latest.apk` e `SHA256.txt`.
- Il link permanente per l'ultima versione è: https://github.com/HGWells2001/Successo-Player-Android/releases/latest/download/SuccessoPlayer_Android_latest.apk
- Nuovo comando discreto **⬆** nell'intestazione per controllare gli aggiornamenti. Il controllo viene effettuato automaticamente al massimo una volta ogni 24 ore all'avvio.
- Quando esiste una versione più recente, l'app propone il download dal collegamento ufficiale GitHub, senza installazione silenziosa o autorizzazioni aggiuntive.
- La Release può essere pubblicata solo dopo compilazione e verifica della firma con certificato permanente.

## Novità della v4.10

- **Shuffle senza ripetizioni**: ogni puntata del gruppo disponibile viene scelta una volta prima dell'inizio di un nuovo ciclo; il primo episodio di un nuovo ciclo non coincide con l'ultimo di quello precedente quando esistono alternative.
- **Filtri rispettati**: con Preferite o Non ascoltate attivi, lo Shuffle sceglie esclusivamente tra le puntate visibili, senza disattivare i filtri.
- Lo stesso ordine casuale senza ripetizioni è condiviso tra il pulsante Shuffle, la fine automatica della puntata, i comandi Bluetooth e la schermata di blocco.
- I progressi dello Shuffle vengono conservati nelle preferenze Android, così un riavvio dell'app non azzera il ciclo quando il gruppo di puntate è rimasto invariato.
- Se cambia l'insieme delle puntate ammesse dai filtri, comincia un nuovo ciclo limitato a quel gruppo. Un filtro vuoto non avvia nuove puntate.
- Rimane lo scroll automatico sulla puntata casuale e la sua evidenziazione. I normali cambi di episodio e il comportamento della pausa non sono stati modificati.

## Novità della v4.9

- La lista scorre automaticamente fino alla puntata scelta da **Shuffle**, anche se la transizione arriva dal servizio audio dopo la fine di una puntata o dai comandi multimediali Android.
- La puntata corrente è evidenziata con sfondo distinto, bordo e titolo in grassetto anche quando la lista ricicla le righe.
- Se i filtri Preferite o Non ascoltate nascondono la puntata casuale, sono disattivati con un avviso per rendere visibile la puntata scelta.
- Lo scorrimento ignora eventi superati da una selezione più recente. Nessuna modifica all'algoritmo Shuffle o alla riproduzione.

## Novità della v4.8

- Corretto l'allineamento verticale del pulsante **Diretta programma** rispetto ai pulsanti **⬇** e **📂**.
- I tre controlli sono ora figli diretti della **stessa riga orizzontale**, con altezza identica di **48 dp**.
- La scritta con l'orario della diretta compare **sotto la riga dei pulsanti** e non influisce più sul loro posizionamento.
- Invariato il comportamento ON/OFF, la programmazione 05:35–06:00 e la grafica coordinata introdotta nella versione 4.7.

## Novità della v4.7

- Il vecchio interruttore Android è stato trasformato in un **pulsante Diretta programma** con la stessa forma arrotondata dei comandi Download e Cartella.
- Lo stato **spento** adotta il fondo neutro del pulsante Cartella, con simbolo ○; lo stato **acceso** usa l'accento terracotta del pulsante Download, con simbolo ●.
- Conservata la medesima logica: durante la fascia feriale 05:35–06:00, premere il pulsante ferma oppure riprende la diretta immediatamente; fuori fascia abilita o disabilita gli avvii automatici.
- L'etichetta resta «Diretta programma», con descrizione accessibile dello stato e riga d'orario sottostante.

## Novità della v4.6

- Pulsanti **⬇ Scarica** e **📂 Cartella download** quadrati (48 × 48 dp), solo icona e accessibilita' tramite descrizione.
- Switch **Diretta programma** sulla stessa riga. Rimossi i pulsanti “Rai Radio 1 ora” e “Ferma”.
- Lunedi-venerdi, ore **05:35–06:00**: switch **OFF** interrompe subito la diretta e **ON** la riavvia subito in streaming, dal momento attuale.
- Fuori fascia lo switch abilita/disabilita la prossima trasmissione automatica, senza avviare Rai Radio 1 a orari diversi.
- Stop dalla notifica disattiva anche lo switch. Restano invariati tutti gli altri controlli del player e la firma APK.

## Novità della v4.5

- Sostituito il pulsante **Apri su RaiPlay** con **📂 Apri download**.
- Il pulsante apre la cartella pubblica `Download/Successo - Storie e voci dal Novecento`, dove l'app salva le puntate.
- Compatibilità con gestori file Android che supportano l'apertura diretta delle cartelle tramite `DocumentsContract`; in alternativa, apre il selettore file nella cartella indicata o la schermata Download.
- Nessun cambio della posizione dei download, delle autorizzazioni dell'app o della firma dell'APK.

## Novità della v4.4

- Rimossi **✕ Esci** e **↻ Aggiorna lista** dall'intestazione.
- Il catalogo continua a caricarsi automaticamente quando si apre l'app, utilizzando la cache breve per non ripetere download inutili.
- La chiusura completa è disponibile tramite **Indietro** o rimozione dalle app recenti; ferma puntate e diretta e pulisce le notifiche.
- Restano invariati la diretta programmata, i filtri e i 2.253 proverbi offline.

## Novità della v4.3

- **Uscita completa** tramite tasto/gesto Indietro: termina le puntate, la radio in diretta e i servizi di riproduzione;
- quando si rimuove Successo Player dalle **applicazioni recenti**, entrambi i servizi si arrestano anche a interfaccia già chiusa;
- la chiusura elimina i controlli multimediali dalle **notifiche** e dalla **schermata di blocco** e cancella le notifiche residue dell'app;
- la posizione della puntata viene salvata in pausa, ma non riparte senza una nuova pressione su **▶**;
- il tasto **Home** e il solo blocco schermo rimangono modalità background e **non interrompono l'ascolto**;
- la programmazione futura della diretta feriale 05:35–06:00 resta memorizzata se abilitata, ma la diretta in corso si interrompe immediatamente all'uscita.

## Novità della v4.2

- Premendo **Pausa**, uscendo e tornando nell'app, la riproduzione resta in pausa.
- La selezione della puntata e la posizione rimangono memorizzate. Per continuare serve premere **▶**.
- L'impostazione della velocita' audio non puo' più riavviare un `MediaPlayer` fermo, incluso quando l'interfaccia si ricollega al servizio.
- Se l'audio sta suonando volontariamente in background, aprire l'app non lo interrompe.
- All'avvio con processo ricreato si ripristina la posizione, ma non si avvia autonomamente la puntata.
- Invariate diretta automatica di Rai Radio 1, 2.253 proverbi offline e firma per aggiornamento senza disinstallazione.

## Novità della v4.1

- Rimossa la barra di ricerca delle puntate per lasciare più spazio al catalogo.
- Conservati i filtri **Preferite** e **Non ascoltate**, combinabili tra loro, con preferiti, dati e download esistenti.
- Restano la diretta automatica di Rai Radio 1, i 2.253 proverbi offline e il sistema di aggiornamenti con firma stabile.

## Novità della v4.0

- **Diretta automatica opzionale**: switch nell'interfaccia; dal lunedì al venerdì, dalle 05:35 alle 06:00 (fuso Europe/Rome), l'app avvia e ferma il flusso MP3 di Rai Radio 1;
- lettore radio dedicato, riproducibile anche a schermo bloccato tramite un servizio multimediale foreground e notifica con comando Stop;
- pulsanti per ascoltare o fermare Rai Radio 1 manualmente in qualsiasi momento;
- rischedulazione dopo riavvio del telefono, aggiornamento app, variazioni di ora/fuso e permessi;
- per gli orari precisi Android 12+ richiede l'autorizzazione **Sveglie e promemoria**. In mancanza, l'avvio è approssimato dal sistema e può avvenire in ritardo;
- necessita connessione Internet: non è garantita la disponibilità continua dello stream Rai. Non riattiva automaticamente il servizio se Android impedisce la partenza per limiti di batteria;

## Novità della v3.9

- nuovo popup **📜 Il proverbio del giorno** dopo quello astronomico;
- **2253 proverbi italiani distinti** in una raccolta offline, uno diverso ogni giorno per 2253 giorni consecutivi (oltre sei anni);
- entrambi i popup si mostrano **solo una volta al primo accesso quotidiano**;
- design coerente con i colori dell'app e con il tema giorno/notte;
- compatibilità con la riproduzione in background e i controlli sulla schermata di blocco.

- archivio testuale esterno al codice Java, espandibile senza connessioni o dipendenze web;
- proverbi selezionati dalla tradizione popolare, con deduplicazione e controllo automatico durante ogni build.

## Controlli multimediali

- vera **MediaSession Android** per i controlli multimediali di sistema;
- player visibile sulla **schermata di blocco** durante la riproduzione;
- controlli **precedente, play/pausa, successiva e stop** dalla notifica multimediale;
- titolo della puntata e artwork pubblicati al sistema;
- posizione di riproduzione sincronizzata con i controlli Android;
- supporto migliorato per cuffie, Bluetooth e dispositivi compatibili;
- servizio audio foreground mantenuto anche quando l'interfaccia è in background.

## Funzioni

- catalogo delle puntate da RaiPlay Sound;
- riproduzione audio in foreground e background;
- ripristino della sessione quando si passa ad altre app;
- download delle puntate con indicatore di avanzamento;
- preferiti ★;
- indicatore **✓ Ascoltata** sulle puntate completate;
- filtro **Non ascoltate** per mostrare soltanto le puntate ancora da ascoltare;
- filtri **Preferite** e **Non ascoltate** combinabili;
- modalità **Shuffle** con riproduzione casuale continua;
- riproduzione automatica della puntata successiva con Shuffle disattivato;
- notifica delle nuove puntate;
- popup giornaliero con alba, tramonto e fase lunare a Saxa Rubra;
- tema automatico giorno/notte basato su alba e tramonto;
- palette grafica ispirata al programma.

I detti provengono dalla tradizione popolare italiana. La selezione è stata integrata e deduplicata a partire anche dalla [raccolta pubblica di proverbi italiani](https://github.com/regolimassimo/Proverbi/blob/master/app/src/main/assets/proverbi-IT.txt); le formule proverbiali sono di origine tradizionale.

## Aggiornamenti senza disinstallazione

Per sostituire un APK gia installato, Android richiede **lo stesso applicationId**, **lo stesso certificato di firma** e un **versionCode crescente**. I primi APK GitHub fino alla v3.7 erano compilati in debug su macchine temporanee, che cambiano la chiave a ogni build. Dalla v3.8 la compilazione corregge la compilazione GitHub: gli APK aggiornabili richiedono una firma permanente.

**Configurazione una tantum, sul PC Windows usato per il builder:**
1. Esegui `windows-builder/COMPILA_SUCCESSO_PLAYER_v24.bat` almeno una volta per creare o riutilizzare la chiave `%LOCALAPPDATA%\SuccessoPlayerBuild\successo_update.keystore`. Conservane un backup privato.
2. Installa [GitHub CLI](https://cli.github.com/), poi autentica con `gh auth login`. Assicurati di avere Java 17 e `keytool.exe`.
3. In PowerShell, nella cartella `windows-builder`, esegui `powershell -ExecutionPolicy Bypass -File .\CONFIGURA_FIRMA_GITHUB.ps1`.
4. Avvia `build-apk.yml` da GitHub Actions. Da questo momento i successivi APK GitHub saranno firmati con **la stessa chiave**. La build rifiuta di pubblicare APK se la chiave o la sua impronta non coincidono con i secrets configurati.

**Attenzione alla prima migrazione:** la v3.6 e la v3.7 distribuite da GitHub hanno certificati differenti. Android non consente di aggiornarle direttamente con un APK firmato da una terza chiave: potrebbe essere necessaria **un'ultima disinstallazione**, con possibile perdita dei dati locali. Dopo aver installato un APK della nuova serie firmato dalla chiave permanente, i successivi aggiornamenti dalla stessa serie non richiederanno disinstallazioni. Non eliminare mai la chiave `successo_update.keystore` e non caricarla pubblicamente su GitHub.

## Build su Windows

Nella cartella [`windows-builder`](windows-builder) sono inclusi gli script di build automatica.

Avvia:

```text
COMPILA_SUCCESSO_PLAYER_v24.bat
```

Il builder usa Java 17, Gradle 8.9 e Android SDK 35 e mantiene il sistema di firma persistente per gli aggiornamenti. Per installazioni aggiornabili, usa lo **stesso certificato** su PC e in GitHub Actions.

## Pacchetto Android

- Application ID: `it.successoplayer.app`
- Versione: `5.0`
- Version code base: `50000`

I pacchetti ZIP pronti sono disponibili nella cartella [`dist`](dist).

## Nota

Questo è un progetto non ufficiale e indipendente. Rai, RaiPlay Sound e i relativi marchi e contenuti appartengono ai rispettivi titolari. Il progetto non include né redistribuisce le puntate del programma: l'app accede ai contenuti disponibili tramite RaiPlay Sound.
