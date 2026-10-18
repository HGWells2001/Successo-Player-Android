package it.successoplayer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Al boot si preparano soltanto i timer; il servizio media parte esclusivamente
 * all'allarme delle 05:35 o su richiesta esplicita da parte dell'utente.
 */
public final class LiveRadioReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (LiveRadioScheduler.ACTION_START.equals(action)) {
            if (LiveRadioScheduler.enabled(context) && LiveRadioScheduler.inBroadcastWindow()) {
                Intent play = new Intent(context, LiveRadioService.class)
                        .setAction(LiveRadioService.ACTION_START_AUTO);
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(play);
                    } else {
                        context.startService(play);
                    }
                } catch (RuntimeException ignored) {
                    // Sistema in modalita' risparmio/restrizione: niente crash.
                }
            }
        } else if (LiveRadioScheduler.ACTION_STOP.equals(action)) {
            // Fermiamo solo la diretta automatica, senza interrompere le puntate manuali.
            context.stopService(new Intent(context, LiveRadioService.class));
        }
        // Alla scadenza ripianifica il prossimo giorno feriale; al boot e ai cambi
        // di orario/fuso/permesso riallinea tutte e due le sveglie.
        LiveRadioScheduler.schedule(context);
    }
}
