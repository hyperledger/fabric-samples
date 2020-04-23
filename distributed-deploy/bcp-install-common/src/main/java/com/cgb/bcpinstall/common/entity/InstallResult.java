package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class InstallResult implements Serializable {
    private static final long serialVersionUID = 1649219876175042832L;

    private RoleEnum role;
    private boolean success;
}
