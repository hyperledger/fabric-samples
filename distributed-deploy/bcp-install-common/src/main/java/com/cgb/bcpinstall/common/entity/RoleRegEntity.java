package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class RoleRegEntity implements Serializable {
    private static final long serialVersionUID = 6214474905502751178L;

    /**
     * http 服务的 port
     */
    private String serverPort;
}
