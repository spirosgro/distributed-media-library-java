package model;

public class Movie extends Media {
    private static final long serialVersionUID = 1L;

    private int durationMinutes;
    private String director;

    public Movie() {
        
    }

    public Movie(String title, String description, String genre, int firstReleaseYear,
                 int durationMinutes, String director) {
        super(title, description, genre, firstReleaseYear);
        this.durationMinutes = durationMinutes;
        this.director = director;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        
        this.durationMinutes = durationMinutes;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        
        this.director = director;
    }

    @Override
    public String toString() {
        
        return "Movie{" + super.toString() +
                ", durationMinutes=" + durationMinutes +
                ", director='" + director + '\'' +
                '}';
    }
}

