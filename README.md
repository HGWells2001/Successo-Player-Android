# Successo Player Android

Player Android non ufficiale dedicato a **“Successo. Storie e voci dal Novecento”** di RaiPlay Sound.

## Versione corrente

**v3.7 – 730 proverbi del giorno**

## Novità della v3.7

- nuovo popup **📜 Il proverbio del giorno** dopo quello astronomico;
- **730 proverbi italiani distinti** in una raccolta offline, uno diverso ogni giorno per 730 giorni consecutivi (due anni);
- entrambi i popup si mostrano **solo una volta al primo accesso quotidiano**;
- design coerente con i colori dell'app e con il tema giorno/notte;
- compatibilità con la riproduzione in background e i controlli sulla schermata di blocco.

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

## Build su Windows

Nella cartella [`windows-builder`](windows-builder) sono inclusi gli script di build automatica.

Avvia:

```text
COMPILA_SUCCESSO_PLAYER_v24.bat
```

Il builder usa Java 17, Gradle 8.9 e Android SDK 35 e mantiene il sistema di firma persistente per gli aggiornamenti.

## Pacchetto Android

- Application ID: `it.successoplayer.app`
- Versione: `3.7`
- Version code base: `3700`

I pacchetti ZIP pronti sono disponibili nella cartella [`dist`](dist).

## Nota

Questo è un progetto non ufficiale e indipendente. Rai, RaiPlay Sound e i relativi marchi e contenuti appartengono ai rispettivi titolari. Il progetto non include né redistribuisce le puntate del programma: l'app accede ai contenuti disponibili tramite RaiPlay Sound.
