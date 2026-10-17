package common;

import java.io.Serializable;

// Generic payloads are constrained by ObjectCodec and server-side operation checks.
@SuppressWarnings("serial")
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private RequestType type;

    private Object payload;

    private String textParameter;

    private String fileName;

    public Request() {

    }

    public Request(RequestType type) {
        this.type = type;
    }

    public Request(RequestType type, Object payload, String textParameter, String fileName) {
        this.type = type;
        this.payload = payload;
        this.textParameter = textParameter;
        this.fileName = fileName;
    }

    public RequestType getType() {
        return type;
    }

    public void setType(RequestType type) {
        
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {

        this.payload = payload;
    }

    public String getTextParameter() {
        return textParameter;
    }

    public void setTextParameter(String textParameter) {
        
        this.textParameter = textParameter;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        
        this.fileName = fileName;
    }
}

