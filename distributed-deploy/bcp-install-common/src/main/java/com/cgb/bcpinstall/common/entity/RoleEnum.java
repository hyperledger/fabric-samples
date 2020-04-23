package com.cgb.bcpinstall.common.entity;

public enum RoleEnum {
    ORDER(1),
    PEER(2);

    private int val;
    private RoleEnum(int val) {
        this.val = val;
    }

    public int getVal() {
        return this.val;
    }
}
