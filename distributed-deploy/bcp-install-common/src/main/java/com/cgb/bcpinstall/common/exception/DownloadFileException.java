package com.cgb.bcpinstall.common.exception;

import lombok.Getter;

@Getter
public class DownloadFileException extends Exception {
    private static final long serialVersionUID = 7913122512705959462L;

    private String httpResponseContent;

    public DownloadFileException(String responseContent, String message) {
        super(message);

        this.httpResponseContent = responseContent;
    }
}
