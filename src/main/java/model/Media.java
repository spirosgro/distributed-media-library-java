package model;

import java.io.Serializable;

public abstract class Media implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String description;
    private String genre;
    private int firstReleaseYear;

    public Media() {
        
    }

    public Media(String title, String description, String genre, int firstReleaseYear) {
        this.title = title;
        this.description = description;
        this.genre = genre;
        this.firstReleaseYear = firstReleaseYear;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public int getFirstReleaseYear() {
        return firstReleaseYear;
    }

    public void setFirstReleaseYear(int firstReleaseYear) {
        this.firstReleaseYear = firstReleaseYear;
    }

    @Override
    public String toString() {
        
        return "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", genre='" + genre + '\'' +
                ", firstReleaseYear=" + firstReleaseYear;
    }
}

