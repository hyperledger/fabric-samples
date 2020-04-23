package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class DiffHostConfigs implements Serializable {
    private static final long serialVersionUID = 7652411049227664431L;
    private Map<String, String> ordererHostConfig;
    private Map<String, String> peerHostConfig;
}
