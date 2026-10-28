package it.successoplayer.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.util.DisplayMetrics;
import android.os.Environment;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ToggleButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements AudioPlaybackService.Listener {
    private static final int REQ_NOTIFICATIONS = 901;
    private static final int REQ_STORAGE = 902;
    private static final int MENU_SEARCH = 1001;
    private static final int MENU_QUEUE = 1002;
    private static final int MENU_SLEEP = 1003;
    private static final int MENU_HISTORY = 1004;
    private static final int MENU_UPDATE = 1005;

    // Cartella pubblica utilizzata dal DownloadManager per le puntate.
    private static final String EPISODE_DOWNLOAD_FOLDER = "Successo - Storie e voci dal Novecento";
    private static final long CATALOG_CACHE_MS = 10L * 60L * 1000L;
    private static final Object CATALOG_CACHE_LOCK = new Object();
    private static RaiClient.Catalog cachedCatalog;
    private static long cachedCatalogAt;

    private boolean appliedDaylightTheme;
    private final Handler themeHandler = new Handler(Looper.getMainLooper());
    private final Runnable themeCheckRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || !activityStarted || isFinishing()) return;

            boolean daylightNow = AstronomyInfo.isDaylightNow();

            if (daylightNow != appliedDaylightTheme) {
                persistUiState();
                themeHandler.removeCallbacks(this);
                recreate();
                return;
            }

            themeHandler.postDelayed(this, 60000L);
        }
    };


    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler liveStatusHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveStatusTick = new Runnable() {
        @Override public void run() {
            if (destroyed || !activityStarted) return;
            updateLiveRadioHint();
            liveStatusHandler.postDelayed(this, 1000L);
        }
    };
    private final Handler sleepTimerUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTimerUiTick = new Runnable() {
        @Override public void run() {
            if (destroyed || !activityStarted) return;
            refreshMoreMenuState();
            sleepTimerUiHandler.postDelayed(this, 15_000L);
        }
    };
    private final List<Episode> allEpisodes = new ArrayList<>();
    private final List<Episode> visibleEpisodes = new ArrayList<>();

    private EpisodeAdapter adapter;
    private Episode selectedEpisode;
    private int selectedVisibleIndex = -1;

    private AudioPlaybackService service;
    private boolean bound;
    private boolean bindingActive;
    private boolean activityStarted;
    private boolean destroyed;
    private boolean exitingApp;
    private boolean catalogLoaded;

    private boolean userSeeking;
    private boolean pendingAutoPlay;
    private boolean favoritesOnly;
    private boolean unlistenedOnly;
    private boolean shuffleEnabled;
    private String searchQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable filterSearchTask = () -> {
        if (!destroyed && catalogLoaded && adapter != null) applyFilter();
    };

    private String pendingRestoreEpisodeId = "";
    private int pendingListPosition;
    private final Random random = new Random();
    private final Set<String> favoriteIds = new HashSet<>();
    private final Set<String> listenedIds = new HashSet<>();
    private long knownDurationMs;
    private Episode pendingDownload;
    private SharedPreferences prefs;
    private ApkUpdateDownloader apkUpdateDownloader;

    private TextView programSubtitle, countText, selectedTitle, selectedMeta, currentTime, totalTime, volumeText, statusText, updateDownloadHint;
    private EditText searchInput;
    private LinearLayout searchPanel;
    private Button clearSearchButton, moreMenuButton;
    private ListView episodeList;
    private SeekBar seekBar, volumeBar;
    private Button playButton, previousButton, nextButton, rewindButton, forwardButton, shuffleButton, downloadButton, openDownloadsButton, favoritesFilterButton, unlistenedFilterButton;
    private Spinner speedSpinner;
    private ToggleButton liveAutoSwitch;
    private TextView liveScheduleHint, liveConnectionHint;
    private boolean syncingLiveSwitch;
    private ProgressBar loadingProgress, downloadProgress;
    private TextView downloadProgressText;
    private final Handler downloadHandler = new Handler(Looper.getMainLooper());
    private long activeDownloadId = -1L;
    private DownloadManager activeDownloadManager;
    private final Runnable downloadProgressPoller = this::updateDownloadProgress;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (destroyed || !activityStarted || !bindingActive) {
                return;
            }

            service = ((AudioPlaybackService.LocalBinder) binder).getService();
            bound = true;

            service.setListener(MainActivity.this);
            service.setVolumePercent(volumeBar.getProgress());
            service.setSpeed(selectedSpeed());
            service.setShuffleEnabled(shuffleEnabled);

            synchronizePlaybackService();
            refreshMoreMenuState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;

            if (activityStarted && !destroyed) {
                setStatus("Motore audio temporaneamente disconnesso", false);
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            bound = false;
            service = null;

            if (bindingActive) {
                try {
                    unbindService(connection);
                } catch (Exception ignored) {}
            }

            bindingActive = false;

            if (activityStarted && !destroyed) {
                mainRebindPlaybackService();
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            bound = false;
            service = null;
            bindingActive = false;

            if (activityStarted && !destroyed) {
                setStatus("Motore audio non disponibile", false);
            }
        }
    };


    @Override
    protected void attachBaseContext(Context newBase) {
        boolean daylight = AstronomyInfo.isDaylightNow();

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode =
                (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | (daylight
                        ? Configuration.UI_MODE_NIGHT_NO
                        : Configuration.UI_MODE_NIGHT_YES);

        appliedDaylightTheme = daylight;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("successo_player", MODE_PRIVATE);
        apkUpdateDownloader = new ApkUpdateDownloader(this, prefs);

        favoriteIds.clear();
        favoriteIds.addAll(
                prefs.getStringSet(
                        "favorite_episode_ids",
                        Collections.emptySet()
                )
        );

        listenedIds.clear();
        listenedIds.addAll(
                prefs.getStringSet(
                        "listened_episode_ids",
                        Collections.emptySet()
                )
        );

        favoritesOnly = prefs.getBoolean("favorites_only", false);
        unlistenedOnly = prefs.getBoolean("unlistened_only", false);
        if (prefs.contains("listened_only")) {
            prefs.edit().remove("listened_only").apply();
        }
        shuffleEnabled = prefs.getBoolean("shuffle_enabled", false);
        searchQuery = prefs.getString("ui_search_query", "");
        if (searchQuery == null) searchQuery = "";
        if (searchQuery.length() > 100) searchQuery = searchQuery.substring(0, 100);

        bindViews();
        configureUi();
        restoreUiState(savedInstanceState);

        requestNotificationPermission();

        AstronomyScheduler.cancel(this);
        EpisodeCheckScheduler.schedule(this);
        LiveRadioScheduler.schedule(this);

        loadCatalog(false);
    }

    @Override
    protected void onStart() {
        super.onStart();

        activityStarted = true;
        liveStatusHandler.removeCallbacks(liveStatusTick);
        liveStatusHandler.post(liveStatusTick);
        sleepTimerUiHandler.removeCallbacks(sleepTimerUiTick);
        sleepTimerUiHandler.post(sleepTimerUiTick);
        apkUpdateDownloader.onStart();
        // Verifica aggiornamenti a ogni nuovo accesso, anche dal background.
        UpdateChecker.check(this, prefs, false);

        bindPlaybackService();
        restoreDownloadProgressIfNeeded();
        // Prima il popup astronomico, poi il proverbio quotidiano.
        if (!showAstronomyPopupIfNeeded()) {
            showProverbPopupIfNeeded();
        }
    }

    private void mainRebindPlaybackService() {
        themeHandler.postDelayed(() -> {
            if (activityStarted && !destroyed && !bindingActive) {
                bindPlaybackService();
            }
        }, 250L);
    }

    private void bindPlaybackService() {
        if (destroyed || bindingActive || bound) return;

        Intent intent = new Intent(this, AudioPlaybackService.class);

        try {
            bindingActive = bindService(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE
            );
        } catch (Exception e) {
            bindingActive = false;
            setStatus("Impossibile collegare il motore audio", false);
        }
    }

    private void unbindPlaybackService() {
        if (bound && service != null) {
            try {
                service.clearListener(this);
            } catch (Exception ignored) {}
        }

        if (bindingActive) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException ignored) {}
        }

        bindingActive = false;
        bound = false;
        service = null;
    }

    private void synchronizePlaybackService() {
        if (!bound || service == null || destroyed) return;

        service.setShuffleEnabled(shuffleEnabled);

        if (catalogLoaded) {
            service.setFullCatalog(allEpisodes);
            service.setQueue(
                    shuffleEnabled ? visibleEpisodes : allEpisodes, shuffleEnabled);
        }
        refreshMoreMenuState();

        Episode active = service.getCurrentEpisode();

        if (active != null) {
            Episode canonical = findEpisodeById(active.id);

            selectedEpisode = canonical != null ? canonical : active;
            selectedVisibleIndex = indexOfVisible(selectedEpisode);

            updateSelectedUi(false);

            long position = service.getCurrentPositionMs();
            long duration = service.getCurrentDurationMs();

            if (duration > 0L) {
                knownDurationMs = duration;
            }

            updateTimelineFromPosition(position, knownDurationMs);
            return;
        }

        if (pendingAutoPlay && selectedEpisode != null) {
            pendingAutoPlay = false;

            service.selectEpisode(selectedEpisode);
            service.togglePlayPause();
            return;
        }

        if (!catalogLoaded || allEpisodes.isEmpty()) return;

        String sessionId = prefs.getString("session_episode_id", "");

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = pendingRestoreEpisodeId;
        }

        Episode restoreEpisode = findEpisodeById(sessionId);

        if (restoreEpisode == null && selectedEpisode != null) {
            restoreEpisode = findEpisodeById(selectedEpisode.id);
        }

        if (restoreEpisode == null) return;

        long resumePosition = Math.max(
                0L,
                prefs.getLong("session_position_ms", 0L)
        );

        // Un rientro nell'app non e' un comando PLAY.
        // Il lettore puo' restare attivo in background: il ramo "active != null"
        // qui sopra lo preserva senza modificarne lo stato. Per una sessione
        // recuperata dopo la morte del processo, si ripristina solo la
        // puntata/posizione; la riproduzione si avvia soltanto premendo PLAY.
        selectedEpisode = restoreEpisode;
        selectedVisibleIndex = indexOfVisible(restoreEpisode);
        knownDurationMs = restoreEpisode.durationMs;

        updateSelectedUi(false);
        updateTimelineFromPosition(resumePosition, knownDurationMs);

        service.restoreSession(
                restoreEpisode,
                resumePosition,
                false
        );
    }

    private void restoreUiState(Bundle savedInstanceState) {
        String savedEpisode;
        int savedPosition;

        if (savedInstanceState != null) {
            savedEpisode = savedInstanceState.getString(
                    "selected_episode_id",
                    prefs.getString("ui_selected_episode_id", "")
            );

            savedPosition = savedInstanceState.getInt(
                    "list_position",
                    prefs.getInt("ui_list_position", 0)
            );
        } else {
            savedEpisode = prefs.getString("ui_selected_episode_id", "");
            savedPosition = prefs.getInt("ui_list_position", 0);
        }

        pendingRestoreEpisodeId = savedEpisode == null ? "" : savedEpisode;
        pendingListPosition = Math.max(0, savedPosition);

    }

    private void persistUiState() {
        if (prefs == null) return;

        String selectedId =
                selectedEpisode != null && selectedEpisode.id != null
                        ? selectedEpisode.id
                        : "";

        int listPosition =
                episodeList != null
                        ? Math.max(0, episodeList.getFirstVisiblePosition())
                        : 0;

        prefs.edit()
                .putString("ui_selected_episode_id", selectedId)
                .putString("ui_search_query", searchQuery)
                .putInt("ui_list_position", listPosition)
                .apply();
    }

    private void restoreSelectionFromState() {
        if (allEpisodes.isEmpty()) return;

        if (selectedEpisode != null && selectedEpisode.id != null) {
            Episode canonical = findEpisodeById(selectedEpisode.id);

            if (canonical != null) {
                selectedEpisode = canonical;
                selectedVisibleIndex = indexOfVisible(canonical);
                updateSelectedUi(false);
                return;
            }
        }

        String selectedId = prefs.getString("session_episode_id", "");

        if (selectedId == null || selectedId.trim().isEmpty()) {
            selectedId = pendingRestoreEpisodeId;
        }

        Episode restored = findEpisodeById(selectedId);

        if (restored == null) return;

        selectedEpisode = restored;
        selectedVisibleIndex = indexOfVisible(restored);
        knownDurationMs = restored.durationMs;

        updateSelectedUi(false);

        long savedPosition = Math.max(
                0L,
                prefs.getLong("session_position_ms", 0L)
        );

        updateTimelineFromPosition(savedPosition, knownDurationMs);
    }

    private Episode findEpisodeById(String id) {
        if (id == null || id.trim().isEmpty()) return null;

        for (Episode episode : allEpisodes) {
            if (episode != null
                    && episode.id != null
                    && episode.id.equals(id)) {
                return episode;
            }
        }

        return null;
    }

    private void updateTimelineFromPosition(long positionMs, long durationMs) {
        long duration = Math.max(0L, durationMs);
        long position = Math.max(
                0L,
                duration > 0L
                        ? Math.min(positionMs, duration)
                        : positionMs
        );

        if (duration > 0L) {
            knownDurationMs = duration;
        }

        int progress =
                knownDurationMs > 0L
                        ? (int) Math.round(
                                (position / (double) knownDurationMs)
                                        * seekBar.getMax()
                        )
                        : 0;

        seekBar.setProgress(
                Math.max(
                        0,
                        Math.min(seekBar.getMax(), progress)
                )
        );

        currentTime.setText(formatTime(position));
        totalTime.setText(formatTime(knownDurationMs));
    }

    private void postUi(Runnable action) {
        if (action == null || destroyed) return;

        MainActivity.this.runOnUiThread(() -> {
            if (destroyed || isFinishing()) return;
            action.run();
        });
    }

    private void restoreDownloadProgressIfNeeded() {
        if (prefs == null || destroyed) return;

        if (activeDownloadId < 0L) {
            activeDownloadId = prefs.getLong(
                    "active_download_id",
                    -1L
            );
        }

        if (activeDownloadId < 0L) return;

        activeDownloadManager = (DownloadManager) getSystemService(
                DOWNLOAD_SERVICE
        );

        if (activeDownloadManager == null) return;

        downloadButton.setEnabled(false);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgressText.setVisibility(View.VISIBLE);

        downloadHandler.removeCallbacks(downloadProgressPoller);
        downloadHandler.post(downloadProgressPoller);
    }

    private boolean showAstronomyPopupIfNeeded() {
        if (prefs == null || isFinishing() || destroyed) return false;

        AstronomyInfo.Summary info = AstronomyInfo.today();
        String lastShown = prefs.getString("astronomy_popup_last_date", "");

        if (info.dateKey.equals(lastShown)) return false;

        // Registriamo subito la giornata, così rotazioni o ricreazioni
        // dell'Activity non fanno comparire il popup due volte.
        prefs.edit()
                .putString("astronomy_popup_last_date", info.dateKey)
                .apply();

        String message =
                info.dateLabel + "\n\n"
                        + "☀  Alba: " + info.sunrise + "\n"
                        + "🌇  Tramonto: " + info.sunset + "\n\n"
                        + "🌙  " + info.moonPhase + "\n"
                        + "Illuminazione lunare: " + info.moonIllumination + "%";

        final int horizontalPadding = (int) (22 * getResources().getDisplayMetrics().density);
        final int topPadding = (int) (18 * getResources().getDisplayMetrics().density);
        final int bottomPadding = (int) (8 * getResources().getDisplayMetrics().density);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(getResources().getColor(R.color.panel, getTheme()));
        content.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding);

        TextView titleView = new TextView(this);
        titleView.setText("Saxa Rubra - Studi radiofonici della palazzina G - Centro Radiotelevisivo Biagio Agnes");
        titleView.setTextSize(20);
        int popupTextColor = getResources().getColor(R.color.textPrimary, getTheme());
        titleView.setTextColor(popupTextColor);
        titleView.setGravity(Gravity.START);
        titleView.setMaxLines(4);
        titleView.setSingleLine(false);
        titleView.setPadding(0, 0, 0, (int) (14 * getResources().getDisplayMetrics().density));
        content.addView(
                titleView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextSize(16);
        messageView.setTextColor(popupTextColor);
        messageView.setLineSpacing(0, 1.12f);
        messageView.setGravity(Gravity.START);
        messageView.setSingleLine(false);
        content.addView(
                messageView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        AlertDialog astronomyDialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("OK", null)
                .create();

        astronomyDialog.setOnDismissListener(dialog -> {
            if (activityStarted && !destroyed && !isFinishing()) {
                showProverbPopupIfNeeded();
            }
        });

        astronomyDialog.setOnShowListener(dialog -> {
            Window window = astronomyDialog.getWindow();
            if (window != null) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();

                // Il popup usa quasi tutta la larghezza disponibile,
                // lasciando un piccolo margine ai lati.
                int targetWidth = (int) (metrics.widthPixels * 0.94f);

                window.setLayout(
                        targetWidth,
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
        });

        astronomyDialog.show();
        try {
            astronomyDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getResources().getColor(R.color.accent, getTheme()));
        } catch (Exception ignored) {}
        return true;
    }

    private void showProverbPopupIfNeeded() {
        if (prefs == null || !activityStarted || destroyed || isFinishing()) return;

        AstronomyInfo.Summary today = AstronomyInfo.today();
        String lastShown = prefs.getString("proverb_popup_last_date", "");
        if (today.dateKey.equals(lastShown)) return;

        prefs.edit().putString("proverb_popup_last_date", today.dateKey).apply();

        float density = getResources().getDisplayMetrics().density;
        int horizontalPadding = (int) (22 * density);
        int verticalPadding = (int) (20 * density);
        int primary = getResources().getColor(R.color.textPrimary, getTheme());
        int secondary = getResources().getColor(R.color.textSecondary, getTheme());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        content.setBackgroundColor(getResources().getColor(R.color.panel, getTheme()));

        TextView title = new TextView(this);
        title.setText("📜 Il proverbio del giorno");
        title.setTextSize(21);
        title.setTextColor(primary);
        title.setPadding(0, 0, 0, (int) (16 * density));
        content.addView(title);

        TextView proverb = new TextView(this);
        proverb.setText("“" + ProverbOfDay.forDate(this, today.dateKey) + "”");
        proverb.setTextSize(19);
        proverb.setTextColor(primary);
        proverb.setLineSpacing(0, 1.15f);
        content.addView(proverb);

        TextView note = new TextView(this);
        note.setText("Un detto della tradizione popolare italiana");
        note.setTextSize(12);
        note.setTextColor(secondary);
        note.setPadding(0, (int) (18 * density), 0, 0);
        content.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("OK", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(
                        (int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(getResources().getColor(R.color.accent, getTheme()));
            }
        });
        dialog.show();
    }

    private void bindViews() {
        programSubtitle = findViewById(R.id.programSubtitle);
        countText = findViewById(R.id.countText);
        selectedTitle = findViewById(R.id.selectedTitle);
        selectedMeta = findViewById(R.id.selectedMeta);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        volumeText = findViewById(R.id.volumeText);
        statusText = findViewById(R.id.statusText);
        updateDownloadHint = findViewById(R.id.updateDownloadHint);
        episodeList = findViewById(R.id.episodeList);
        seekBar = findViewById(R.id.seekBar);
        volumeBar = findViewById(R.id.volumeBar);
        playButton = findViewById(R.id.playButton);
        previousButton = findViewById(R.id.previousButton);
        nextButton = findViewById(R.id.nextButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        rewindButton = findViewById(R.id.rewindButton);
        forwardButton = findViewById(R.id.forwardButton);
        downloadButton = findViewById(R.id.downloadButton);
        openDownloadsButton = findViewById(R.id.openDownloadsButton);
        moreMenuButton = findViewById(R.id.moreMenuButton);
        searchPanel = findViewById(R.id.searchPanel);
        searchInput = findViewById(R.id.searchInput);
        clearSearchButton = findViewById(R.id.clearSearchButton);
        favoritesFilterButton = findViewById(R.id.favoritesFilterButton);
        unlistenedFilterButton = findViewById(R.id.unlistenedFilterButton);
        speedSpinner = findViewById(R.id.speedSpinner);
        loadingProgress = findViewById(R.id.loadingProgress);
        downloadProgress = findViewById(R.id.downloadProgress);
        downloadProgressText = findViewById(R.id.downloadProgressText);
        liveAutoSwitch = findViewById(R.id.liveAutoSwitch);
        liveScheduleHint = findViewById(R.id.liveScheduleHint);
        liveConnectionHint = findViewById(R.id.liveConnectionHint);
    }

    private void configureUi() {
        configureSearch();
        moreMenuButton.setOnClickListener(v -> showMoreMenu());
        liveAutoSwitch.setChecked(LiveRadioScheduler.enabled(this));
        updateLiveRadioHint();
        liveAutoSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (syncingLiveSwitch) return;
            // OFF arresta subito; ON durante la trasmissione riparte subito.
            LiveRadioScheduler.setEnabled(this, enabled);
            updateLiveRadioHint();
            if (enabled && !LiveRadioScheduler.canSchedulePrecisely(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Consenti avvio alle 05:35")
                        .setMessage("Android richiede l'autorizzazione 'Sveglie e promemoria' " +
                                "per avviare Rai Radio 1 puntualmente anche a schermo bloccato. " +
                                "Senza il permesso l'orario potrebbe non essere rispettato.")
                        .setPositiveButton("Apri impostazioni", (dialog, which) -> {
                            try {
                                Intent settings = new Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                settings.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(settings);
                            } catch (Exception e) {
                                toast("Apri Impostazioni > App > Sveglie e promemoria.");
                            }
                        })
                        .setNegativeButton("Non ora", null)
                        .show();
            }
        });
        adapter = new EpisodeAdapter(this, new EpisodeAdapter.FavoriteHandler() {
            @Override public boolean isFavorite(Episode episode) {
                return MainActivity.this.isFavorite(episode);
            }

            @Override public boolean isListened(Episode episode) {
                return MainActivity.this.isListened(episode);
            }

            @Override public void toggleFavorite(Episode episode) {
                MainActivity.this.toggleFavorite(episode);
            }
        });
        episodeList.setAdapter(adapter);

        episodeList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!bound || service == null) {
                toast("Motore audio non ancora pronto.");
                return true;
            }
            final Episode chosen = adapter.getEpisode(position);
            new AlertDialog.Builder(this)
                    .setTitle(chosen.title)
                    .setItems(new String[]{
                                    "Aggiungi alla coda",
                                    "Riproduci come prossimo",
                                    "Riproduci subito"},
                            (dialog, which) -> {
                                if (which == 2) {
                                    service.removeFromUpNextById(chosen.id);
                                    playEpisodeFromAutoMode(chosen);
                                } else {
                                    boolean added = service.enqueueUpNext(chosen, which == 1);
                                    toast(added
                                            ? (which == 1 ? "Impostata come prossima puntata"
                                                    : "Aggiunta alla coda")
                                            : "Puntata già in coda o coda piena");
                                }
                                refreshMoreMenuState();
                            })
                    .setNegativeButton("Annulla", null)
                    .show();
            return true;
        });

        episodeList.setOnItemClickListener((parent, view, position, id) -> {
            selectedVisibleIndex = position;
            selectedEpisode = adapter.getEpisode(position);
            pendingRestoreEpisodeId = selectedEpisode.id;
            // Important: a new episode always owns a fresh timeline. The old
            // episode position must never leak into the new selection.
            knownDurationMs = selectedEpisode.durationMs;
            seekBar.setProgress(0);
            currentTime.setText("0:00");
            totalTime.setText(formatTime(knownDurationMs));
            updateSelectedUi(true);
            if (bound) {
                service.selectEpisode(selectedEpisode);
                service.togglePlayPause();
            } else {
                pendingAutoPlay = true;
                setStatus("Motore audio in avvio…", true);
            }
        });

        updateFavoritesFilterButton();
        favoritesFilterButton.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            prefs.edit().putBoolean("favorites_only", favoritesOnly).apply();
            updateFavoritesFilterButton();
            applyFilter();
        });

        updateUnlistenedFilterButton();
        unlistenedFilterButton.setOnClickListener(v -> {
            unlistenedOnly = !unlistenedOnly;
            prefs.edit().putBoolean("unlistened_only", unlistenedOnly).apply();
            updateUnlistenedFilterButton();
            applyFilter();
        });

        playButton.setOnClickListener(v -> {
            if (selectedEpisode == null) {
                toast("Seleziona prima una puntata.");
                return;
            }
            if (!bound) {
                toast("Motore audio non ancora pronto.");
                return;
            }
            Episode active = service.getCurrentEpisode();
            if (active == null || !active.id.equals(selectedEpisode.id)) service.selectEpisode(selectedEpisode);
            service.togglePlayPause();
        });

        rewindButton.setOnClickListener(v -> { if (bound) service.seekBy(-30000); });
        forwardButton.setOnClickListener(v -> { if (bound) service.seekBy(30000); });
        previousButton.setOnClickListener(v -> changeEpisode(-1, true));
        nextButton.setOnClickListener(v -> {
            if (bound && service != null) {
                service.skipToNext();
                refreshMoreMenuState();
            } else {
                changeEpisode(1, true);
            }
        });

        refreshMoreMenuState();

        updateShuffleButton();
        shuffleButton.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;

            prefs.edit()
                    .putBoolean("shuffle_enabled", shuffleEnabled)
                    .apply();

            updateShuffleButton();

            if (bound && service != null) {
                service.setShuffleEnabled(shuffleEnabled);
                service.setQueue(
                        shuffleEnabled ? visibleEpisodes : allEpisodes, shuffleEnabled);
            }

            if (shuffleEnabled) {
                playRandomEpisode(true);
            } else {
                toast("Shuffle disattivato · alla fine partirà la puntata successiva");
            }
        });

        // Guaranteed click-to-seek: touching anywhere on the bar immediately
        // moves the thumb there; dragging still works normally.
        seekBar.setOnTouchListener((v, event) -> {
            if (knownDurationMs <= 0) return false;
            float x = Math.max(0, Math.min(event.getX(), seekBar.getWidth()));
            int progress = seekBar.getWidth() <= 0 ? 0 : Math.round((x / seekBar.getWidth()) * seekBar.getMax());
            long target = Math.round((progress / (double) seekBar.getMax()) * knownDurationMs);
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                userSeeking = true;
                seekBar.setProgress(progress);
                currentTime.setText(formatTime(target));
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                seekBar.setProgress(progress);
                currentTime.setText(formatTime(target));
                if (bound) service.seekTo(target);
                userSeeking = false;
                return true;
            }
            return true;
        });

        int savedVolume = prefs.getInt("volume", 85);
        volumeBar.setProgress(savedVolume);
        volumeText.setText(savedVolume + "%");
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeText.setText(progress + "%");
                if (bound) service.setVolumePercent(progress);
                if (fromUser) prefs.edit().putInt("volume", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        String[] speeds = {"0.75×", "0.90×", "1.00×", "1.25×", "1.50×", "2.00×"};
        ArrayAdapter<String> speedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, speeds);
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speedSpinner.setAdapter(speedAdapter);
        int speedIndex = Math.max(0, Math.min(speeds.length - 1, prefs.getInt("speedIndex", 2)));
        speedSpinner.setSelection(speedIndex);
        speedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt("speedIndex", position).apply();
                if (bound) service.setSpeed(selectedSpeed());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        downloadButton.setOnClickListener(v -> startDownload(selectedEpisode));
        openDownloadsButton.setOnClickListener(v -> openEpisodeDownloads());
    }

    /**
     * Keep search out of sight until requested, with a dedicated clear icon.
     * Closing it always clears the query so no invisible filter persists.
     */
    private void configureSearch() {
        searchPanel.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
        searchInput.setText(searchQuery);
        searchInput.setSelection(searchInput.length());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                prefs.edit().putString("ui_search_query", searchQuery).apply();
                searchHandler.removeCallbacks(filterSearchTask);
                searchHandler.postDelayed(filterSearchTask, 180L);
                clearSearchButton.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        clearSearchButton.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
        clearSearchButton.setOnClickListener(v -> searchInput.setText(""));
    }

    private void toggleSearchPanel() {
        if (searchPanel.getVisibility() == View.VISIBLE) {
            searchInput.setText("");
            searchInput.clearFocus();
            searchPanel.setVisibility(View.GONE);
            InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (input != null) input.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        } else {
            searchPanel.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
            InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (input != null) input.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }
        refreshMoreMenuState();
    }

    private void loadCatalog(boolean forceNetwork) {
        setStatus("Aggiorno il catalogo…", true);

        if (!forceNetwork) {
            RaiClient.Catalog memoryCache = null;

            synchronized (CATALOG_CACHE_LOCK) {
                if (cachedCatalog != null
                        && System.currentTimeMillis() - cachedCatalogAt < CATALOG_CACHE_MS) {
                    memoryCache = cachedCatalog;
                }
            }

            if (memoryCache != null) {
                applyCatalog(memoryCache, "Catalogo ripristinato");
                return;
            }
        }

        io.execute(() -> {
            try {
                RaiClient.Catalog catalog = RaiClient.loadCatalog();

                synchronized (CATALOG_CACHE_LOCK) {
                    cachedCatalog = catalog;
                    cachedCatalogAt = System.currentTimeMillis();
                }

                EpisodeWatchState.markLatestSeen(
                        getApplicationContext(),
                        catalog.episodes
                );

                postUi(() -> applyCatalog(catalog, "Catalogo aggiornato"));

            } catch (Exception e) {
                postUi(() -> {
                    setStatus("Errore catalogo: " + message(e), false);
                });
            }
        });
    }

    private void applyCatalog(RaiClient.Catalog catalog, String status) {
        if (catalog == null || destroyed) return;

        allEpisodes.clear();
        allEpisodes.addAll(catalog.episodes);

        catalogLoaded = true;

        programSubtitle.setText(catalog.title);

        restoreSelectionFromState();
        applyFilter();

        if (pendingListPosition > 0 && !visibleEpisodes.isEmpty()) {
            episodeList.setSelection(
                    Math.min(
                            pendingListPosition,
                            visibleEpisodes.size() - 1
                    )
            );
        }

        synchronizePlaybackService();
        if (shuffleEnabled) scrollToShuffleSelection();

        setStatus(status, false);
    }

    private void applyFilter() {
        visibleEpisodes.clear();

        for (Episode e : allEpisodes) {
            if (favoritesOnly && !isFavorite(e)) continue;
            if (unlistenedOnly && isListened(e)) continue;
            if (!EpisodeSearch.matches(e, searchQuery)) continue;

            visibleEpisodes.add(e);
        }

        adapter.setItems(visibleEpisodes);

        int favoriteCount = 0;
        int listenedCount = 0;
        for (Episode e : allEpisodes) {
            if (isFavorite(e)) favoriteCount++;
            if (isListened(e)) listenedCount++;
        }

        if (!searchQuery.isEmpty()) {
            countText.setText(visibleEpisodes.size()
                    + (visibleEpisodes.size() == 1 ? " risultato" : " risultati"));
        } else if (favoritesOnly || unlistenedOnly) {
            String filterLabel;
            if (favoritesOnly && unlistenedOnly) {
                filterLabel = " preferite e non ascoltate";
            } else if (favoritesOnly) {
                filterLabel = visibleEpisodes.size() == 1 ? " preferita" : " preferite";
            } else {
                filterLabel = visibleEpisodes.size() == 1 ? " non ascoltata" : " non ascoltate";
            }

            countText.setText(
                    visibleEpisodes.size()
                            + filterLabel
            );
        } else {
            countText.setText(
                    visibleEpisodes.size()
                            + " puntate"
                            + "  ·  "
                            + favoriteCount
                            + (favoriteCount == 1 ? " preferita" : " preferite")
                            + "  ·  "
                            + listenedCount
                            + (listenedCount == 1 ? " ascoltata" : " ascoltate")
            );
        }

        selectedVisibleIndex = indexOfVisible(selectedEpisode);
        updateFavoritesFilterButton();
        updateUnlistenedFilterButton();
        // Keep the service's shuffle pool identical to the filtered list.
        // Changing a filter never restarts the player or clears the filter.
        if (bound && service != null && catalogLoaded) {
            service.setQueue(
                    shuffleEnabled ? visibleEpisodes : allEpisodes, shuffleEnabled);
        }
    }

    private boolean isFavorite(Episode episode) {
        return episode != null
                && episode.id != null
                && favoriteIds.contains(episode.id);
    }


    private boolean isListened(Episode episode) {
        return episode != null
                && episode.id != null
                && listenedIds.contains(episode.id);
    }

    private void toggleFavorite(Episode episode) {
        if (episode == null || episode.id == null || episode.id.trim().isEmpty()) return;

        boolean nowFavorite;
        if (favoriteIds.contains(episode.id)) {
            favoriteIds.remove(episode.id);
            nowFavorite = false;
        } else {
            favoriteIds.add(episode.id);
            nowFavorite = true;
        }

        prefs.edit()
                .putStringSet("favorite_episode_ids", new HashSet<>(favoriteIds))
                .apply();

        if (favoritesOnly) {
            applyFilter();
        } else {
            adapter.notifyDataSetChanged();
            applyFilter();
        }

        toast(nowFavorite ? "Aggiunta ai preferiti ★" : "Rimossa dai preferiti");
    }

    private void updateFavoritesFilterButton() {
        if (favoritesFilterButton == null) return;
        favoritesFilterButton.setText(favoritesOnly ? "★ Preferite" : "☆ Tutte");
        favoritesFilterButton.setContentDescription(
                favoritesOnly
                        ? "Mostra tutte le puntate"
                        : "Mostra solo le puntate preferite"
        );
    }

    private void updateUnlistenedFilterButton() {
        if (unlistenedFilterButton == null) return;

        unlistenedFilterButton.setText(unlistenedOnly ? "✓ Non ascoltate" : "○ Non ascoltate");
        unlistenedFilterButton.setContentDescription(
                unlistenedOnly
                        ? "Disattiva il filtro delle puntate non ascoltate"
                        : "Mostra solo le puntate non ancora ascoltate"
        );

        if (unlistenedOnly) {
            unlistenedFilterButton.setBackgroundResource(R.drawable.shuffle_active_bg);
            unlistenedFilterButton.setTextColor(
                    getResources().getColor(R.color.headerText, getTheme())
            );
        } else {
            unlistenedFilterButton.setBackgroundResource(R.drawable.button_bg);
            unlistenedFilterButton.setTextColor(
                    getResources().getColor(R.color.textPrimary, getTheme())
            );
        }
    }

    private void showPlaybackHistory() {
        if (!bound || service == null) {
            toast("Motore audio non ancora pronto.");
            return;
        }
        if (!catalogLoaded) {
            toast("Attendi il caricamento del catalogo.");
            return;
        }
        final List<PlaybackHistory.Entry> records = service.getRecentHistory();
        if (records.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Ascoltate di recente")
                    .setMessage("La cronologia è vuota. Compariranno qui le puntate "
                            + "che hai effettivamente avviato, anche in background.")
                    .setPositiveButton("Chiudi", null)
                    .show();
            return;
        }

        SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY);
        String[] names = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            PlaybackHistory.Entry item = records.get(i);
            Episode ep = findEpisodeById(item.episodeId);
            String title = ep == null ? "Puntata non più disponibile" : ep.title;
            long resumeMs = ep == null ? 0L : service.getSavedEpisodePosition(ep);
            String progress = resumeMs >= 1000L
                    ? " · Riprendi da " + formatTime(resumeMs) : "";
            names[i] = title + "\n" + dateFormat.format(new Date(item.playedAtMs))
                    + progress;
        }

        new AlertDialog.Builder(this)
                .setTitle("Ascoltate di recente · " + records.size())
                .setItems(names, (dialog, index) ->
                        showHistoryEntryActions(records.get(index).episodeId))
                .setNeutralButton("Svuota", (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Cancellare la cronologia?")
                            .setMessage("La coda, i preferiti e i punti d'ascolto "
                                    + "resteranno invariati.")
                            .setPositiveButton("Cancella", (d, w) -> {
                                if (bound && service != null) {
                                    service.clearPlayHistory();
                                    toast("Cronologia cancellata");
                                }
                            })
                            .setNegativeButton("Annulla", null)
                            .show();
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showHistoryEntryActions(String episodeId) {
        if (!bound || service == null) return;
        Episode ep = findEpisodeById(episodeId);
        final String[] options = ep == null
                ? new String[]{"Rimuovi dalla cronologia"}
                : new String[]{"Riprendi ascolto", "Aggiungi alla coda",
                        "Rimuovi dalla cronologia"};

        new AlertDialog.Builder(this)
                .setTitle(ep == null ? "Puntata non disponibile" : ep.title)
                .setItems(options, (dialog, selected) -> {
                    if (ep == null || selected == 2) {
                        service.removeFromPlayHistory(episodeId);
                        showPlaybackHistory();
                    } else if (selected == 0) {
                        playEpisodeFromAutoMode(ep);
                    } else {
                        if (service.enqueueUpNext(ep, false)) {
                            toast("Aggiunta alla coda");
                        } else {
                            toast("Puntata già in coda o coda piena");
                        }
                        refreshMoreMenuState();
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void showSleepTimerOptions() {
        if (!bound || service == null) {
            toast("Motore audio non ancora pronto.");
            return;
        }
        boolean active = service.isSleepTimerActive();
        String[] choices = active
                ? new String[]{"15 minuti", "30 minuti", "45 minuti",
                        "60 minuti", "90 minuti", "Alla fine della puntata",
                        "Disattiva timer"}
                : new String[]{"15 minuti", "30 minuti", "45 minuti",
                        "60 minuti", "90 minuti", "Alla fine della puntata"};
        String title = "Timer sonno";
        if (service.isSleepTimerUntilEpisodeEnd()) {
            title += " · fine puntata";
        } else if (service.isSleepTimerActive()) {
            long remaining = service.getSleepTimerRemainingMs();
            title += " · " + (Math.max(0L, remaining) + 59999L) / 60000L + " min";
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(choices, (dialog, index) -> {
                    if (index < 5) {
                        int[] minutes = {15, 30, 45, 60, 90};
                        service.startSleepTimer(minutes[index]);
                    } else if (index == 5) {
                        if (!service.sleepAfterCurrentEpisode()) {
                            toast("Seleziona prima una puntata.");
                        }
                    } else {
                        service.cancelSleepTimer();
                    }
                    refreshMoreMenuState();
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    /** A single overflow menu for secondary player actions. */
    private void showMoreMenu() {
        if (moreMenuButton == null || destroyed) return;
        PopupMenu menu = new PopupMenu(this, moreMenuButton);
        menu.setGravity(Gravity.END);
        String searchLabel = searchPanel.getVisibility() == View.VISIBLE
                ? "⌕ Chiudi ricerca" : "⌕ Cerca puntate";
        menu.getMenu().add(0, MENU_SEARCH, 0, searchLabel);

        int queued = bound && service != null ? service.getUpNextIds().size() : 0;
        menu.getMenu().add(0, MENU_QUEUE, 1,
                "☷ Coda di riproduzione" + (queued > 0 ? " (" + queued + ")" : ""));

        String sleepLabel = "☾ Timer sonno";
        if (bound && service != null && service.isSleepTimerActive()) {
            if (service.isSleepTimerUntilEpisodeEnd()) {
                sleepLabel += " · fine puntata";
            } else {
                long remaining = service.getSleepTimerRemainingMs();
                sleepLabel += " · " + Math.max(1L,
                        (remaining + 59_999L) / 60_000L) + " min";
            }
        }
        menu.getMenu().add(0, MENU_SLEEP, 2, sleepLabel);
        menu.getMenu().add(0, MENU_HISTORY, 3, "◷ Cronologia d'ascolto");
        menu.getMenu().add(0, MENU_UPDATE, 4, "↻ Controlla aggiornamenti");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_SEARCH:
                    toggleSearchPanel();
                    return true;
                case MENU_QUEUE:
                    showUpNextQueue();
                    return true;
                case MENU_SLEEP:
                    showSleepTimerOptions();
                    return true;
                case MENU_HISTORY:
                    showPlaybackHistory();
                    return true;
                case MENU_UPDATE:
                    UpdateChecker.check(this, prefs, true);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void refreshMoreMenuState() {
        if (moreMenuButton == null) return;
        String description = "Apri menu: ricerca, coda, timer, cronologia e aggiornamenti";
        if (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE) {
            description += ". Ricerca aperta";
        }
        if (bound && service != null) {
            int queued = service.getUpNextIds().size();
            if (queued > 0) description += ". Coda: " + queued + " puntate";
            if (service.isSleepTimerActive()) {
                description += service.isSleepTimerUntilEpisodeEnd()
                        ? ". Timer fino a fine puntata" : ". Timer sonno attivo";
            }
        }
        moreMenuButton.setContentDescription(description);
    }

    private void showUpNextQueue() {
        if (!bound || service == null) {
            toast("Motore audio non ancora pronto.");
            return;
        }
        final List<String> ids = service.getUpNextIds();
        refreshMoreMenuState();
        if (ids.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Coda di riproduzione")
                    .setMessage("La coda è vuota. Tieni premuta una puntata "
                            + "nell'elenco per aggiungerla o impostarla come prossima.")
                    .setPositiveButton("Chiudi", null)
                    .show();
            return;
        }
        String[] entries = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            Episode ep = findEpisodeById(ids.get(i));
            entries[i] = (i + 1) + ". " +
                    (ep == null ? "Puntata non disponibile" : ep.title);
        }
        new AlertDialog.Builder(this)
                .setTitle("Coda di riproduzione · " + ids.size())
                .setItems(entries, (d, index) -> showUpNextItemActions(ids.get(index)))
                .setNeutralButton("Svuota", (d, w) -> new AlertDialog.Builder(this)
                        .setTitle("Svuotare la coda?")
                        .setMessage("La puntata in ascolto non verrà interrotta.")
                        .setPositiveButton("Svuota", (d2, w2) -> {
                            service.clearUpNext();
                            refreshMoreMenuState();
                        })
                        .setNegativeButton("Annulla", null)
                        .show())
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showUpNextItemActions(String id) {
        if (!bound || service == null) return;
        List<String> ids = service.getUpNextIds();
        int index = ids.indexOf(id);
        if (index < 0) {
            showUpNextQueue();
            return;
        }
        Episode ep = findEpisodeById(id);
        new AlertDialog.Builder(this)
                .setTitle(ep == null ? "Puntata non disponibile" : ep.title)
                .setItems(new String[]{"Sposta su", "Sposta giù",
                                "Rimuovi dalla coda", "Riproduci ora"},
                        (dialog, action) -> {
                            if (action == 0) service.moveUpNext(index, -1);
                            else if (action == 1) service.moveUpNext(index, 1);
                            else if (action == 2) service.removeFromUpNext(index);
                            else if (ep != null) {
                                service.removeFromUpNextById(id);
                                playEpisodeFromAutoMode(ep);
                            }
                            refreshMoreMenuState();
                            if (action < 3) showUpNextQueue();
                        })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void updateShuffleButton() {
        if (shuffleButton == null) return;

        shuffleButton.setText(shuffleEnabled ? "🔀 ON" : "🔀");
        shuffleButton.setContentDescription(
                shuffleEnabled
                        ? "Disattiva riproduzione casuale"
                        : "Attiva riproduzione casuale"
        );

        if (shuffleEnabled) {
            shuffleButton.setBackgroundResource(R.drawable.shuffle_active_bg);
            shuffleButton.setTextColor(getResources().getColor(R.color.headerText, getTheme()));
        } else {
            shuffleButton.setBackgroundResource(R.drawable.button_bg);
            shuffleButton.setTextColor(getResources().getColor(R.color.textPrimary, getTheme()));
        }
    }

    private Episode randomEpisodeExcluding(Episode excluded) {
        if (visibleEpisodes.isEmpty()) return null;
        if (visibleEpisodes.size() == 1) return visibleEpisodes.get(0);
        // Only used while waiting for the service to bind. The service owns
        // the cycle once connected and records this first episode on select.
        int excludedIndex = indexOfVisible(excluded);
        int pick = random.nextInt(
                visibleEpisodes.size() - (excludedIndex >= 0 ? 1 : 0));
        if (excludedIndex >= 0 && pick >= excludedIndex) pick++;
        return visibleEpisodes.get(pick);
    }

    private void playRandomEpisode(boolean showToast) {
        if (!catalogLoaded) {
            toast("Catalogo non ancora disponibile.");
            return;
        }
        if (visibleEpisodes.isEmpty()) {
            toast("Nessuna puntata compatibile con i filtri selezionati.");
            return;
        }
        Episode randomEpisode = bound && service != null
                ? service.pickNextShuffleEpisode(selectedEpisode)
                : randomEpisodeExcluding(selectedEpisode);
        if (randomEpisode == null) return;

        playEpisodeFromAutoMode(randomEpisode);

        if (showToast) {
            toast("Shuffle · " + randomEpisode.title);
        }
    }

    private Episode sequentialEpisodeAfter(Episode current) {
        if (current == null || allEpisodes.isEmpty()) return null;

        for (int i = 0; i < allEpisodes.size(); i++) {
            if (allEpisodes.get(i).id.equals(current.id)) {
                int next = i + 1;
                return next < allEpisodes.size() ? allEpisodes.get(next) : null;
            }
        }

        return null;
    }

    private void playEpisodeFromAutoMode(Episode episode) {
        if (episode == null) return;

        selectedEpisode = episode;
        selectedVisibleIndex = indexOfVisible(episode);
        knownDurationMs = episode.durationMs;

        seekBar.setProgress(0);
        currentTime.setText("0:00");
        totalTime.setText(formatTime(knownDurationMs));
        updateSelectedUi(true);

        if (shuffleEnabled) {
            scrollToShuffleSelection();
        } else if (selectedVisibleIndex >= 0) {
            episodeList.setSelection(Math.max(0, selectedVisibleIndex - 2));
        }

        if (bound) {
            service.selectEpisode(episode);
            service.togglePlayPause();
        } else {
            pendingAutoPlay = true;
            setStatus("Motore audio in avvio…", true);
        }
    }

    /**
     * La selezione puo' arrivare dal servizio mentre l'app non e' visibile.
     * Attendere il layout, poi posizionare la traccia nello spazio visibile.
     * Un vecchio callback non deve scorrere su una traccia ormai sostituita.
     */
    private void scrollToShuffleSelection() {
        if (!shuffleEnabled || !catalogLoaded || selectedEpisode == null
                || selectedEpisode.id == null || episodeList == null) return;
        final String expectedId = selectedEpisode.id;
        episodeList.post(() -> {
            if (destroyed || !activityStarted || !shuffleEnabled
                    || selectedEpisode == null
                    || !expectedId.equals(selectedEpisode.id)) return;
            int index = indexOfVisible(selectedEpisode);
            if (index < 0) return;
            selectedVisibleIndex = index;
            episodeList.setSelectionFromTop(index, Math.max(0, episodeList.getHeight() / 3));
        });
    }

    private int indexOfVisible(Episode ep) {
        if (ep == null) return -1;
        for (int i = 0; i < visibleEpisodes.size(); i++) if (visibleEpisodes.get(i).id.equals(ep.id)) return i;
        return -1;
    }

    private void changeEpisode(int delta, boolean autoPlay) {
        if (visibleEpisodes.isEmpty()) return;
        int idx = indexOfVisible(selectedEpisode);
        if (idx < 0) idx = delta > 0 ? -1 : visibleEpisodes.size();
        idx += delta;
        if (idx < 0 || idx >= visibleEpisodes.size()) return;
        selectedVisibleIndex = idx;
        selectedEpisode = visibleEpisodes.get(idx);
        knownDurationMs = selectedEpisode.durationMs;
        seekBar.setProgress(0);
        currentTime.setText("0:00");
        totalTime.setText(formatTime(knownDurationMs));
        updateSelectedUi(true);
        episodeList.setSelection(Math.max(0, idx - 2));
        if (bound) {
            service.selectEpisode(selectedEpisode);
            if (autoPlay) service.togglePlayPause();
        }
    }

    private void updateSelectedUi(boolean freshSelection) {
        if (selectedEpisode == null) return;
        if (adapter != null) adapter.setSelectedEpisodeId(selectedEpisode.id);
        selectedTitle.setText(selectedEpisode.title);
        String meta = selectedEpisode.dateDisplay
                + (selectedEpisode.durationMs > 0 ? "  ·  " + formatTime(selectedEpisode.durationMs) : "");
        if (isListened(selectedEpisode)) {
            meta += "  ·  ✓ Ascoltata";
        }
        selectedMeta.setText(meta);
        if (freshSelection) {
            knownDurationMs = selectedEpisode.durationMs;
            currentTime.setText("0:00");
            totalTime.setText(formatTime(knownDurationMs));
            seekBar.setProgress(0);
        }
    }

    private float selectedSpeed() {
        float[] v = {0.75f, 0.90f, 1.00f, 1.25f, 1.50f, 2.00f};
        int p = speedSpinner.getSelectedItemPosition();
        return v[Math.max(0, Math.min(v.length - 1, p))];
    }


    /**
     * Mostra la sottocartella pubblica in cui il DownloadManager salva
     * le puntate. Usa solo intent standard e nessun permesso globale ai file.
     */
    private void openEpisodeDownloads() {
        if (!catalogLoaded) {
            toast("Attendi il caricamento del catalogo.");
            return;
        }
        setStatus("Cerco le puntate scaricate…", true);
        final List<Episode> snapshot = new ArrayList<>(allEpisodes);
        io.execute(() -> {
            List<OfflineEpisodeLibrary.Item> downloaded =
                    OfflineEpisodeLibrary.scan(this, snapshot);
            postUi(() -> {
                setStatus("Libreria offline pronta", false);
                showOfflineLibrary(downloaded);
            });
        });
    }

    private void showOfflineLibrary(List<OfflineEpisodeLibrary.Item> downloaded) {
        if (destroyed || !activityStarted) return;
        if (downloaded.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Puntate scaricate")
                    .setMessage("Non risultano download completati nella cronologia "
                            + "di Successo Player. Puoi anche aprire la cartella "
                            + "per vedere file copiati manualmente.")
                    .setPositiveButton("Apri cartella",
                            (d, w) -> openEpisodeDownloadsFolder())
                    .setNegativeButton("Chiudi", null)
                    .show();
            return;
        }
        String[] titles = new String[downloaded.size()];
        long bytes = 0L;
        for (int i = 0; i < downloaded.size(); i++) {
            OfflineEpisodeLibrary.Item item = downloaded.get(i);
            titles[i] = item.label();
            bytes += item.bytes;
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.ITALY,
                        "Puntate offline: %d · %.1f MB", downloaded.size(),
                        bytes / 1048576.0))
                .setItems(titles, (d, index) ->
                        showOfflineEpisodeActions(downloaded.get(index)))
                .setNeutralButton("Apri cartella",
                        (d, w) -> openEpisodeDownloadsFolder())
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showOfflineEpisodeActions(OfflineEpisodeLibrary.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(item.episode.title)
                .setItems(new String[]{"▶ Ascolta offline", "Elimina download", "Apri cartella"},
                        (d, index) -> {
                    if (index == 0) {
                        playOfflineEpisode(item);
                    } else if (index == 1) {
                        new AlertDialog.Builder(this)
                                .setTitle("Eliminare il download?")
                                .setMessage(item.episode.title
                                        + "\nLa puntata rimarrà nel catalogo online.")
                                .setPositiveButton("Elimina", (d2, w2) ->
                                        io.execute(() -> {
                                            boolean removed = OfflineEpisodeLibrary.delete(this, item);
                                            postUi(() -> {
                                                toast(removed ? "Download eliminato" :
                                                        "Impossibile eliminare il download");
                                                openEpisodeDownloads();
                                            });
                                        }))
                                .setNegativeButton("Annulla", null)
                                .show();
                    } else {
                        openEpisodeDownloadsFolder();
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void playOfflineEpisode(OfflineEpisodeLibrary.Item item) {
        if (!bound || service == null) {
            toast("Motore audio non ancora pronto. Riprova.");
            return;
        }
        Uri uri = OfflineEpisodeLibrary.uriFor(this, item);
        if (uri == null) {
            toast("Download non disponibile. Aggiorna la libreria.");
            return;
        }
        selectedEpisode = item.episode;
        selectedVisibleIndex = indexOfVisible(item.episode);
        pendingRestoreEpisodeId = item.episode.id;
        knownDurationMs = item.episode.durationMs;
        updateSelectedUi(true);
        if (selectedVisibleIndex >= 0) {
            episodeList.setSelectionFromTop(selectedVisibleIndex, 0);
        }
        service.selectDownloadedEpisode(item.episode, uri);
        service.togglePlayPause();
    }

    private void openEpisodeDownloadsFolder() {
        String folderId = "primary:" + Environment.DIRECTORY_DOWNLOADS
                + "/" + EPISODE_DOWNLOAD_FOLDER;
        Uri folderUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", folderId);

        // Alcuni gestori file, incluso DocumentsUI su vari dispositivi,
        // aprono direttamente un documento di tipo directory.
        Intent directory = new Intent(Intent.ACTION_VIEW);
        directory.setDataAndType(
                folderUri, DocumentsContract.Document.MIME_TYPE_DIR);
        directory.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(directory);
            return;
        } catch (RuntimeException unsupported) {
            // Nessun gestore compatibile, oppure il provider nega l'apertura.
        }

        // Fallback ufficiale: selettore Android che parte dalla cartella
        // desiderata (EXTRA_INITIAL_URI puo' essere ignorato dal produttore).
        Intent browse = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        browse.addCategory(Intent.CATEGORY_OPENABLE);
        browse.setType("*/*");
        browse.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri);
        try {
            startActivity(browse);
            return;
        } catch (RuntimeException unsupported) {
            // Il dispositivo non espone neanche il selettore documenti.
        }

        // Ultima risorsa: l'interfaccia download del dispositivo.
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (RuntimeException missing) {
            toast("Apri File > Download > " + EPISODE_DOWNLOAD_FOLDER);
        }
    }

    private void startDownload(Episode ep) {
        if (ep == null) {
            toast("Seleziona prima una puntata.");
            return;
        }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = ep;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        setStatus("Preparo il download…", true);
        io.execute(() -> {
            try {
                String url = RaiClient.resolveAudioUrl(ep);
                String ext = RaiClient.extensionFor(url, "");
                String date = ep.dateIso == null ? "" : ep.dateIso;
                String filename = RaiClient.safeName((date.isEmpty() ? "" : date + " - ") + ep.title) + ext;
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setTitle(ep.title);
                req.setDescription("Successo · Puntata");
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setAllowedOverMetered(true);
                req.setAllowedOverRoaming(false);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        EPISODE_DOWNLOAD_FOLDER + "/" + filename);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                long downloadId = dm.enqueue(req);
                postUi(() -> beginDownloadProgress(dm, downloadId));
            } catch (Exception e) {
                postUi(() -> setStatus("Download non riuscito: " + message(e), false));
            }
        });
    }

    private void beginDownloadProgress(DownloadManager dm, long downloadId) {
        activeDownloadManager = dm;
        activeDownloadId = downloadId;

        prefs.edit()
                .putLong("active_download_id", downloadId)
                .apply();

        downloadButton.setEnabled(false);
        downloadProgress.setMax(100);
        downloadProgress.setProgress(0);
        downloadProgress.setIndeterminate(true);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgressText.setText("Download in preparazione…");
        downloadProgressText.setVisibility(View.VISIBLE);
        setStatus("Download avviato · cartella Download/" + EPISODE_DOWNLOAD_FOLDER, false);
        downloadHandler.removeCallbacks(downloadProgressPoller);
        downloadHandler.post(downloadProgressPoller);
    }

    private void updateDownloadProgress() {
        if (activeDownloadManager == null || activeDownloadId < 0) return;

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(activeDownloadId);
        try (Cursor cursor = activeDownloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                finishDownloadProgress(false, "Download non disponibile");
                return;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

            if (total > 0) {
                int percent = (int) Math.max(0, Math.min(100, Math.round(downloaded * 100.0 / total)));
                downloadProgress.setIndeterminate(false);
                downloadProgress.setProgress(percent);
                downloadProgressText.setText(String.format(Locale.ITALY,
                        "Download %d%% · %.1f / %.1f MB", percent, downloaded / 1048576.0, total / 1048576.0));
            } else {
                downloadProgress.setIndeterminate(true);
                downloadProgressText.setText(String.format(Locale.ITALY,
                        "Download… %.1f MB", downloaded / 1048576.0));
            }

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                downloadProgress.setIndeterminate(false);
                downloadProgress.setProgress(100);
                downloadProgressText.setText("Download 100% · completato");
                finishDownloadProgress(true, "Download completato · cartella Download/Successo - Storie e voci dal Novecento");
                return;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                finishDownloadProgress(false, "Download non riuscito");
                return;
            }

            downloadHandler.postDelayed(downloadProgressPoller, 500);
        } catch (Exception e) {
            finishDownloadProgress(false, "Errore monitoraggio download: " + message(e));
        }
    }

    private void finishDownloadProgress(boolean success, String status) {
        downloadHandler.removeCallbacks(downloadProgressPoller);
        activeDownloadId = -1L;
        activeDownloadManager = null;

        prefs.edit()
                .remove("active_download_id")
                .apply();

        downloadButton.setEnabled(true);
        setStatus(status, false);

        if (success) {
            downloadHandler.postDelayed(() -> {
                if (activeDownloadId < 0) {
                    downloadProgress.setVisibility(View.GONE);
                    downloadProgressText.setVisibility(View.GONE);
                }
            }, 1800);
        } else {
            downloadProgress.setVisibility(View.GONE);
            downloadProgressText.setVisibility(View.GONE);
        }
    }

    private void updateLiveRadioHint() {
        if (liveScheduleHint == null || liveConnectionHint == null) return;
        LiveProgramStatus.Snapshot schedule =
                LiveProgramStatus.at(java.time.ZonedDateTime.now(LiveProgramStatus.ROME));
        boolean enabled = LiveRadioScheduler.enabled(this);
        String scheduleText = LiveProgramStatus.summary(schedule);
        liveScheduleHint.setText(scheduleText);
        String connectionText = LiveRadioConnection.label(
                enabled, schedule.inWindow, LiveRadioScheduler.canSchedulePrecisely(this),
                prefs.getString(LiveRadioConnection.KEY_STATE, LiveRadioConnection.STOPPED),
                prefs.getLong(LiveRadioConnection.KEY_UPDATED_AT, 0L),
                System.currentTimeMillis());
        liveConnectionHint.setText(connectionText);
        liveScheduleHint.setContentDescription("Programma lunedì–venerdì 05:35–06:00, ora italiana. "
                + scheduleText);
        liveConnectionHint.setContentDescription(connectionText);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingDownload != null) {
            Episode ep = pendingDownload;
            pendingDownload = null;
            startDownload(ep);
        }
    }

    boolean canShowUpdatePrompt() {
        return activityStarted && !destroyed && !isFinishing();
    }

    boolean showPendingApkUpdate() {
        return apkUpdateDownloader.showPendingOnDemand();
    }

    void beginApkUpdateDownload(String url, String sha, long expectedSize, long versionCode) {
        apkUpdateDownloader.start(url, sha, expectedSize, versionCode);
    }

    void showUpdateDownloadStatus(String status) {
        if (updateDownloadHint == null || destroyed) return;
        updateDownloadHint.setText(status);
        updateDownloadHint.setVisibility(android.view.View.VISIBLE);
    }

    private void setStatus(String text, boolean busy) {
        if (statusText != null) {
            statusText.setText(text);
        }

        if (loadingProgress != null) {
            loadingProgress.setVisibility(
                    busy ? View.VISIBLE : View.GONE
            );
        }
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private static String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    public static String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.ITALY, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ITALY, "%d:%02d", m, s);
    }

    @Override public void onStatus(String text, boolean busy) {
        postUi(() -> {
            setStatus(text, busy);
            refreshMoreMenuState();
        });
    }

    @Override public void onPlaybackState(boolean playing, boolean prepared) {
        postUi(() -> playButton.setText(playing ? "❚❚" : "▶"));
    }

    @Override public void onPosition(long positionMs, long durationMs) {
        postUi(() -> {
            if (durationMs > 0) knownDurationMs = durationMs;
            if (!userSeeking) {
                int p = knownDurationMs > 0 ? (int) Math.round((positionMs / (double) knownDurationMs) * seekBar.getMax()) : 0;
                seekBar.setProgress(Math.max(0, Math.min(seekBar.getMax(), p)));
                currentTime.setText(formatTime(positionMs));
            }
            totalTime.setText(formatTime(knownDurationMs));
        });
    }

    @Override public void onEpisode(Episode episode) {
        postUi(() -> {
            if (episode == null) return;
            selectedEpisode = episode;
            pendingRestoreEpisodeId = episode.id == null ? "" : episode.id;
            selectedVisibleIndex = indexOfVisible(episode);
            updateSelectedUi(false);
            refreshMoreMenuState();
            // Include la coda esplicita e le transizioni da cuffie/lockscreen.
            if (shuffleEnabled) scrollToShuffleSelection();
            else if (selectedVisibleIndex >= 0) {
                final String expectedId = episode.id;
                episodeList.post(() -> {
                    if (!destroyed && activityStarted && selectedEpisode != null
                            && expectedId != null && expectedId.equals(selectedEpisode.id)) {
                        int visible = indexOfVisible(selectedEpisode);
                        if (visible >= 0) episodeList.setSelectionFromTop(visible, 0);
                    }
                });
            }
        });
    }


    @Override public void onListenedChanged(Episode episode) {
        postUi(() -> {
            if (episode == null || episode.id == null) return;

            listenedIds.add(episode.id);
            adapter.notifyDataSetChanged();

            if (selectedEpisode != null && selectedEpisode.id.equals(episode.id)) {
                updateSelectedUi(false);
            }

            // Aggiorna anche i contatori senza cambiare il filtro corrente.
            applyFilter();
        });
    }


    @Override
    public void onPlaybackCompleted(Episode episode) {
        postUi(() -> {
            if (episode == null) return;

            listenedIds.add(episode.id);
            adapter.notifyDataSetChanged();

            // La scelta della puntata seguente ora vive nel Service.
            // In questo modo Shuffle/continua funzionano anche mentre
            // MainActivity è in background o viene ricreata da Android.
        });
    }

    @Override public void onError(String message) { postUi(() -> toast(message)); }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (activityStarted) {
            synchronizePlaybackService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LiveRadioScheduler.schedule(this);
        // Rispetta eventuale arresto dalla notifica senza una nuova attivazione.
        if (liveAutoSwitch != null) {
            syncingLiveSwitch = true;
            liveAutoSwitch.setChecked(LiveRadioScheduler.enabled(this));
            syncingLiveSwitch = false;
        }
        updateLiveRadioHint();

        themeHandler.removeCallbacks(themeCheckRunnable);
        themeHandler.post(themeCheckRunnable);
    }

    @Override
    protected void onPause() {
        themeHandler.removeCallbacks(themeCheckRunnable);
        super.onPause();
    }

    @Override
    protected void onStop() {
        activityStarted = false;
        liveStatusHandler.removeCallbacks(liveStatusTick);
        sleepTimerUiHandler.removeCallbacks(sleepTimerUiTick);
        apkUpdateDownloader.onStop();

        persistUiState();

        downloadHandler.removeCallbacks(downloadProgressPoller);
        unbindPlaybackService();

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(
                "selected_episode_id",
                selectedEpisode != null ? selectedEpisode.id : ""
        );

        outState.putInt(
                "list_position",
                episodeList != null
                        ? Math.max(0, episodeList.getFirstVisiblePosition())
                        : 0
        );
    }

    @Override
    public void onTrimMemory(int level) {
        persistUiState();
        super.onTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        persistUiState();
        super.onLowMemory();
    }

    /**
     * Una chiusura esplicita (tasto o gesto Indietro) ferma entrambi i
     * servizi. Il tasto Home e lo spegnimento dello schermo NON sono chiusure.
     */
    private void closeAppCompletely() {
        if (exitingApp) return;
        exitingApp = true;
        if (bound && service != null) {
            service.shutdownForAppExit();
        }
        PlaybackExit.stopAll(this);
        finishAndRemoveTask();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Close the secondary search view first, without stopping audio.
        if (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE) {
            toggleSearchPanel();
            return;
        }
        closeAppCompletely();
    }

    @Override
    protected void onDestroy() {
        // onDestroy viene chiamato anche durante i cambi di configurazione.
        // Ferma il player solo se l'Activity sta davvero terminando.
        if (isFinishing()) {
            if (bound && service != null) {
                service.shutdownForAppExit();
            }
            PlaybackExit.stopAll(this);
        }
        destroyed = true;

        themeHandler.removeCallbacksAndMessages(null);
        liveStatusHandler.removeCallbacksAndMessages(null);
        downloadHandler.removeCallbacksAndMessages(null);
        searchHandler.removeCallbacksAndMessages(null);
        sleepTimerUiHandler.removeCallbacksAndMessages(null);

        unbindPlaybackService();

        io.shutdownNow();

        super.onDestroy();
    }
}
