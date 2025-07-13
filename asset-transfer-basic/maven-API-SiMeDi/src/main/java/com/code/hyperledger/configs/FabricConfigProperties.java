package com.code.hyperledger.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fabric")
public class FabricConfigProperties {
    private String mspId;
    private String channelName;
    private String chaincodeName;
    private String peerEndpoint;
    private String overrideAuth;

    private String cryptoPath;
    private String certPath;
    private String keyPath;  
    private String tlsCertPath;
}
