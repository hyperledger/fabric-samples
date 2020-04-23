package com.cgb.bcpinstall.common.response;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class HttpInstallResponse implements Serializable {
    private static final long serialVersionUID = -1685537598043940452L;

    public HttpInstallResponse() {
        this.code = ResponseCode.SUCCESS.getCode();
    }

    private String code;
    private String msg;
}
