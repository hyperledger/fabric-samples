package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class EndCmd implements Serializable {
    private static final long serialVersionUID = -2812305986585325397L;

    private boolean success;
}
