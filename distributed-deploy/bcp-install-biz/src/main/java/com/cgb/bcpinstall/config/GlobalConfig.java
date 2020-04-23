package com.cgb.bcpinstall.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "global")
public class GlobalConfig {
    /**
     * 是否主节点
     */
    private int master;
}
