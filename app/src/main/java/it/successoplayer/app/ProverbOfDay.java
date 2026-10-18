package it.successoplayer.app;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Archivio di proverbi italiani offline.
 * Le frasi sono distribuite in app/src/main/assets/proverbi_it.txt,
 * non generate da servizi esterni e non ricaricate ad ogni apertura.
 */
public final class ProverbOfDay {
    private static final String ASSET_FILE = "proverbi_it.txt";
    private static volatile String[] sayings;

    private ProverbOfDay() {}

    private static String[] load(Context context) {
        String[] cached = sayings;
        if (cached != null) return cached;

        synchronized (ProverbOfDay.class) {
            if (sayings != null) return sayings;

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getApplicationContext().getAssets().open(ASSET_FILE),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) lines.add(line);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Archivio dei proverbi offline non disponibile", e);
            }
            if (lines.isEmpty()) {
                throw new IllegalStateException("Archivio dei proverbi offline vuoto");
            }
            sayings = lines.toArray(new String[0]);
            return sayings;
        }
    }

    public static String forDate(Context context, String dateKey) {
        String[] available = load(context);
        long epochDay = LocalDate.parse(dateKey).toEpochDay();
        return available[Math.floorMod(epochDay, available.length)];
    }

    public static int count(Context context) {
        return load(context).length;
    }
}
