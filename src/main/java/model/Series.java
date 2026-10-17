package model;

import java.util.ArrayList;
import java.util.List;

public class Series extends Media {
    private static final long serialVersionUID = 1L;

    private int numberOfSeasons;
    private ArrayList<Episode> episodes;

    public Series() {
        
        this.episodes = new ArrayList<>();
    }

    public Series(String title, String description, String genre, int firstReleaseYear,
                  int numberOfSeasons, List<Episode> episodes) {
        super(title, description, genre, firstReleaseYear);
        this.numberOfSeasons = numberOfSeasons;
        this.episodes = new ArrayList<>();
        if (episodes != null) {
            this.episodes.addAll(episodes);
        }
    }

    public int getNumberOfSeasons() {
        return numberOfSeasons;
    }

    public void setNumberOfSeasons(int numberOfSeasons) {
        
        this.numberOfSeasons = numberOfSeasons;
    }

    public ArrayList<Episode> getEpisodes() {

        return new ArrayList<>(episodes);
    }

    public void setEpisodes(List<Episode> episodes) {
        
        this.episodes = new ArrayList<>();
        if (episodes != null) {
            this.episodes.addAll(episodes);
        }
    }

    @Override
    public String toString() {
        
        return "Series{" + super.toString() +
                ", numberOfSeasons=" + numberOfSeasons +
                ", episodes=" + episodes +
                '}';
    }
}

