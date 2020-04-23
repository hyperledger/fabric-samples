package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class RemoveCmd implements Serializable {
    private static final long serialVersionUID = -950140223007704381L;

    private RoleEnum role;
    private List<String> hostNames;
    private String ip;
    private List<String> ports;
    private String peerDomain;
    private String ordererDomain;

}
