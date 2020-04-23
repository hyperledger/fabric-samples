package com.cgb.bcpinstall.common.entity;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class InstallCmd implements Serializable {
    private static final long serialVersionUID = -5985735701441710827L;

    private String ipAddress;
    private List<String> rolePorts;
    private RoleEnum role;
    private Map<String, String> hosts;
    private String roleFolderName;
}
