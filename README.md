# Successo Player Android

Player Android non ufficiale dedicato a **“Successo. Storie e voci dal Novecento”**.

## Versione corrente

**v4.18 – Sleep Timer integrato nel motore audio**

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
- Versione: `4.18`
- Version code base: `41800`

I pacchetti ZIP pronti sono disponibili nella cartella [`dist`](dist).

## Nota

Questo è un progetto non ufficiale e indipendente. Rai, RaiPlay Sound e i relativi marchi e contenuti appartengono ai rispettivi titolari. Il progetto non include né redistribuisce le puntate del programma: l'app accede ai contenuti disponibili tramite RaiPlay Sound.
