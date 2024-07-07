package com.eclectics.chamapayments.wrappers.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.Collections;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@ToString
@Builder
public class UniversalResponse {
    private String status;
    private String message;
    private Object data;
    @JsonFormat(shape = JsonFormat.Shape.STRING,pattern = "dd-MM-yyyy HH:mm:ss")
    private Date timestamp;
    private Object metadata;

    public UniversalResponse() {
        this.timestamp = new Date();
    }

    public UniversalResponse(String status, String message,Object data) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.timestamp = new Date();
    }

    public UniversalResponse(String status, String message){
        this.status =status;
        this.message=message;
        this.data = Collections.emptyMap();
        this.timestamp=new Date();
    }
}
