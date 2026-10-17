package model;

import java.io.Serializable;

public class Episode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private int seasonNumber;
    private int episodeNumberInSeason;
    private int durationMinutes;

    public Episode() {
        
    }

    public Episode(String title, int seasonNumber, int episodeNumberInSeason, int durationMinutes) {
        this.title = title;
        this.seasonNumber = seasonNumber;
        this.episodeNumberInSeason = episodeNumberInSeason;
        this.durationMinutes = durationMinutes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        
        this.seasonNumber = seasonNumber;
    }

    public int getEpisodeNumberInSeason() {
        return episodeNumberInSeason;
    }

    public void setEpisodeNumberInSeason(int episodeNumberInSeason) {
        
        this.episodeNumberInSeason = episodeNumberInSeason;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        
        this.durationMinutes = durationMinutes;
    }

    @Override
    public String toString() {
        
        return "Episode{" +
                "title='" + title + '\'' +
                ", seasonNumber=" + seasonNumber +
                ", episodeNumberInSeason=" + episodeNumberInSeason +
                ", durationMinutes=" + durationMinutes +
                '}';
    }
}

