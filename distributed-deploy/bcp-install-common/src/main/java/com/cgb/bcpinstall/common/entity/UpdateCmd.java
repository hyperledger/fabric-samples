package com.cgb.bcpinstall.common.entity;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class UpdateCmd implements Serializable {
    private static final long serialVersionUID = -5201447567245919240L;

    private RoleEnum role;

    /**
     *
     */
    private Map<String, String> hosts;
    /**
     * initconfig.properties 文件中的 host->ip:port 配置（可以是orderer或peer），在扩缩容order、peer时需要用到
     * 可以根据功能写入
     */
    private Map<String, String> peerHostConfig;

    /**
     * 更新原因
     */
    private UpdateReasonEnum reason;

    private InitConfigEntity configEntity;

    /**
     * 新增节点需要加入的业务链
     */
    private Set<String> peerJoinChannelList;

    /**
     * 当前ip对应角色的host
     */
    private String currentHost;
}
