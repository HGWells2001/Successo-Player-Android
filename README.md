# Successo Player Android

Player Android non ufficiale dedicato a **“Successo. Storie e voci dal Novecento”** di RaiPlay Sound.

## Versione corrente

**v3.9 – 2253 proverbi italiani offline**

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
- filtro **Preferite**, filtro **Non ascoltate** e ricerca combinabili;
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
- Versione: `3.9`
- Version code base: `3900`

I pacchetti ZIP pronti sono disponibili nella cartella [`dist`](dist).

## Nota

Questo è un progetto non ufficiale e indipendente. Rai, RaiPlay Sound e i relativi marchi e contenuti appartengono ai rispettivi titolari. Il progetto non include né redistribuisce le puntate del programma: l'app accede ai contenuti disponibili tramite RaiPlay Sound.
