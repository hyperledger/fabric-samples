package com.cgb.bcpinstall.common.entity.init;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class InitConfigEntity implements Serializable {
    private static final long serialVersionUID = -7686888493480177456L;

    private String network;
    private String channelName;
    private String orgMSPID;
    private String orgName;
    private String ordererDomain;
    private String peerDomain;
    private Map<String, String> ordererHostConfig;
    private Map<String, String> peerHostConfig;
    private Map<String, String> metricPortConfig;
    private Map<String, String> couchdbPortConfig;
}
