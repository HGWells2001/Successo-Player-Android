package it.successoplayer.app;

public final class Episode {
    public final String id;
    public final String title;
    public final String description;
    public final String dateIso;
    public final String dateDisplay;
    public final String webUrl;
    public final String audioUrl;
    public final String downloadUrl;
    public final long durationMs;
    public final int episodeNumber;

    public Episode(String id, String title, String description, String dateIso,
                   String dateDisplay, String webUrl, String audioUrl,
                   String downloadUrl, long durationMs, int episodeNumber) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.dateIso = dateIso;
        this.dateDisplay = dateDisplay;
        this.webUrl = webUrl;
        this.audioUrl = audioUrl;
        this.downloadUrl = downloadUrl;
        this.durationMs = durationMs;
        this.episodeNumber = episodeNumber;
    }
}
