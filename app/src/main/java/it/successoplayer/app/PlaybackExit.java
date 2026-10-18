package it.successoplayer.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

/** Pulisce i due servizi multimediali e i controlli del sistema. */
public final class PlaybackExit {
    private PlaybackExit() {}

    public static void stopAll(Context context) {
        // stopService() non crea nuovi servizi se erano gia' stati terminati.
        context.stopService(new Intent(context, AudioPlaybackService.class));
        context.stopService(new Intent(context, LiveRadioService.class));

        // Rimuove i controlli di riproduzione dalle notifiche e dal lockscreen.
        // Puliamo anche eventuali notifiche residue dell'app su richiesta di uscita.
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();
    }
}
