package common;

import java.io.Serializable;

public enum RequestType implements Serializable {
    
    SEARCH_BY_TITLE,
    
    SEARCH_BY_GENRE,
    
    ADD_MOVIE,
    
    ADD_SERIES,
    
    UPDATE_MOVIE,
    
    UPDATE_SERIES,
    
    DELETE_MEDIA,
    
    SAVE_LIBRARY,
    
    LOAD_LIBRARY,
    
    GET_ALL_MEDIA,
    
    DISCONNECT
}

