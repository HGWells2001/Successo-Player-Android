package it.successoplayer.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

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

    private static final long CATALOG_CACHE_MS = 10L * 60L * 1000L;
    private static final long SESSION_AUTOPLAY_MAX_AGE_MS = 6L * 60L * 60L * 1000L;
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
    private boolean catalogLoaded;

    private boolean userSeeking;
    private boolean pendingAutoPlay;
    private boolean favoritesOnly;
    private boolean unlistenedOnly;
    private boolean shuffleEnabled;

    private String pendingRestoreEpisodeId = "";
    private int pendingListPosition;
    private final Random random = new Random();
    private final Set<String> favoriteIds = new HashSet<>();
    private final Set<String> listenedIds = new HashSet<>();
    private long knownDurationMs;
    private Episode pendingDownload;
    private SharedPreferences prefs;

    private TextView programSubtitle, countText, selectedTitle, selectedMeta, currentTime, totalTime, volumeText, statusText;
    private EditText searchBox;
    private ListView episodeList;
    private SeekBar seekBar, volumeBar;
    private Button playButton, refreshButton, previousButton, nextButton, rewindButton, forwardButton, shuffleButton, downloadButton, openRaiButton, favoritesFilterButton, unlistenedFilterButton;
    private Spinner speedSpinner;
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

        bindViews();
        configureUi();
        restoreUiState(savedInstanceState);

        requestNotificationPermission();

        AstronomyScheduler.cancel(this);
        EpisodeCheckScheduler.schedule(this);

        loadCatalog(false);
    }

    @Override
    protected void onStart() {
        super.onStart();

        activityStarted = true;

        bindPlaybackService();
        restoreDownloadProgressIfNeeded();
        showAstronomyPopupIfNeeded();
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
            service.setQueue(allEpisodes, shuffleEnabled);
        }

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

        long sessionSavedAt = prefs.getLong(
                "session_saved_at",
                0L
        );

        boolean sessionIsFresh =
                sessionSavedAt > 0L
                        && System.currentTimeMillis() - sessionSavedAt
                        <= SESSION_AUTOPLAY_MAX_AGE_MS;

        boolean wasPlaying =
                prefs.getBoolean(
                        "session_was_playing",
                        false
                )
                        && sessionIsFresh;

        selectedEpisode = restoreEpisode;
        selectedVisibleIndex = indexOfVisible(restoreEpisode);
        knownDurationMs = restoreEpisode.durationMs;

        updateSelectedUi(false);
        updateTimelineFromPosition(resumePosition, knownDurationMs);

        service.restoreSession(
                restoreEpisode,
                resumePosition,
                wasPlaying
        );
    }

    private void restoreUiState(Bundle savedInstanceState) {
        String savedSearch;
        String savedEpisode;
        int savedPosition;

        if (savedInstanceState != null) {
            savedSearch = savedInstanceState.getString(
                    "search_query",
                    prefs.getString("ui_search_query", "")
            );

            savedEpisode = savedInstanceState.getString(
                    "selected_episode_id",
                    prefs.getString("ui_selected_episode_id", "")
            );

            savedPosition = savedInstanceState.getInt(
                    "list_position",
                    prefs.getInt("ui_list_position", 0)
            );
        } else {
            savedSearch = prefs.getString("ui_search_query", "");
            savedEpisode = prefs.getString("ui_selected_episode_id", "");
            savedPosition = prefs.getInt("ui_list_position", 0);
        }

        pendingRestoreEpisodeId = savedEpisode == null ? "" : savedEpisode;
        pendingListPosition = Math.max(0, savedPosition);

        if (savedSearch != null && !savedSearch.isEmpty()) {
            searchBox.setText(savedSearch);
            searchBox.setSelection(savedSearch.length());
        }
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

        String search =
                searchBox != null
                        ? searchBox.getText().toString()
                        : "";

        prefs.edit()
                .putString("ui_selected_episode_id", selectedId)
                .putString("ui_search_query", search)
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

    private void showAstronomyPopupIfNeeded() {
        if (prefs == null || isFinishing()) return;

        AstronomyInfo.Summary info = AstronomyInfo.today();
        String lastShown = prefs.getString("astronomy_popup_last_date", "");

        if (info.dateKey.equals(lastShown)) return;

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
        searchBox = findViewById(R.id.searchBox);
        episodeList = findViewById(R.id.episodeList);
        seekBar = findViewById(R.id.seekBar);
        volumeBar = findViewById(R.id.volumeBar);
        playButton = findViewById(R.id.playButton);
        refreshButton = findViewById(R.id.refreshButton);
        previousButton = findViewById(R.id.previousButton);
        nextButton = findViewById(R.id.nextButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        rewindButton = findViewById(R.id.rewindButton);
        forwardButton = findViewById(R.id.forwardButton);
        downloadButton = findViewById(R.id.downloadButton);
        openRaiButton = findViewById(R.id.openRaiButton);
        favoritesFilterButton = findViewById(R.id.favoritesFilterButton);
        unlistenedFilterButton = findViewById(R.id.unlistenedFilterButton);
        speedSpinner = findViewById(R.id.speedSpinner);
        loadingProgress = findViewById(R.id.loadingProgress);
        downloadProgress = findViewById(R.id.downloadProgress);
        downloadProgressText = findViewById(R.id.downloadProgressText);
    }

    private void configureUi() {
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

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
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

        refreshButton.setOnClickListener(v -> loadCatalog(true));
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
        nextButton.setOnClickListener(v -> changeEpisode(1, true));

        updateShuffleButton();
        shuffleButton.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;

            prefs.edit()
                    .putBoolean("shuffle_enabled", shuffleEnabled)
                    .apply();

            updateShuffleButton();

            if (bound && service != null) {
                service.setShuffleEnabled(shuffleEnabled);
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
        openRaiButton.setOnClickListener(v -> {
            String url = selectedEpisode != null && selectedEpisode.webUrl != null && !selectedEpisode.webUrl.isEmpty()
                    ? selectedEpisode.webUrl : RaiClient.PROGRAM_WEB;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });
    }

    private void loadCatalog(boolean forceNetwork) {
        setStatus("Aggiorno il catalogo RaiPlay Sound…", true);
        refreshButton.setEnabled(false);

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
                    refreshButton.setEnabled(true);
                });
            }
        });
    }

    private void applyCatalog(RaiClient.Catalog catalog, String status) {
        if (catalog == null || destroyed) return;

        allEpisodes.clear();
        allEpisodes.addAll(catalog.episodes);

        catalogLoaded = true;

        programSubtitle.setText(catalog.title + " · RaiPlay Sound");

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

        setStatus(status, false);
        refreshButton.setEnabled(true);
    }

    private void applyFilter() {
        String q = searchBox.getText().toString().trim().toLowerCase(Locale.ITALY);
        visibleEpisodes.clear();

        for (Episode e : allEpisodes) {
            if (favoritesOnly && !isFavorite(e)) continue;
            if (unlistenedOnly && isListened(e)) continue;

            String hay = (
                    e.title + " "
                            + e.description + " "
                            + e.dateDisplay + " "
                            + e.dateIso
            ).toLowerCase(Locale.ITALY);

            if (q.isEmpty() || hay.contains(q)) {
                visibleEpisodes.add(e);
            }
        }

        adapter.setItems(visibleEpisodes);

        int favoriteCount = 0;
        int listenedCount = 0;
        for (Episode e : allEpisodes) {
            if (isFavorite(e)) favoriteCount++;
            if (isListened(e)) listenedCount++;
        }

        if (favoritesOnly || unlistenedOnly) {
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
                            + (q.isEmpty() ? "" : " trovate")
            );
        } else {
            countText.setText(
                    visibleEpisodes.size()
                            + " puntate"
                            + (q.isEmpty() ? "" : " trovate")
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
        if (allEpisodes.isEmpty()) return null;
        if (allEpisodes.size() == 1) return allEpisodes.get(0);

        int excludedIndex = -1;
        if (excluded != null) {
            for (int i = 0; i < allEpisodes.size(); i++) {
                if (allEpisodes.get(i).id.equals(excluded.id)) {
                    excludedIndex = i;
                    break;
                }
            }
        }

        int pick = random.nextInt(allEpisodes.size() - (excludedIndex >= 0 ? 1 : 0));

        if (excludedIndex >= 0 && pick >= excludedIndex) {
            pick++;
        }

        return allEpisodes.get(pick);
    }

    private void playRandomEpisode(boolean showToast) {
        if (allEpisodes.isEmpty()) {
            toast("Catalogo non ancora disponibile.");
            return;
        }

        Episode randomEpisode = randomEpisodeExcluding(selectedEpisode);
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

        if (selectedVisibleIndex >= 0) {
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
                req.setDescription("Successo · RaiPlay Sound");
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setAllowedOverMetered(true);
                req.setAllowedOverRoaming(false);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        "Successo - Storie e voci dal Novecento/" + filename);
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
        setStatus("Download avviato · cartella Download/Successo - Storie e voci dal Novecento", false);
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

    @Override public void onStatus(String text, boolean busy) { postUi(() -> setStatus(text, busy)); }

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

        outState.putString(
                "search_query",
                searchBox != null ? searchBox.getText().toString() : ""
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

    @Override
    protected void onDestroy() {
        destroyed = true;

        themeHandler.removeCallbacksAndMessages(null);
        downloadHandler.removeCallbacksAndMessages(null);

        unbindPlaybackService();

        io.shutdownNow();

        super.onDestroy();
    }
}
