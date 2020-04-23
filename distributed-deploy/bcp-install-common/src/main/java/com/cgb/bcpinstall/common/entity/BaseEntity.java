package com.cgb.bcpinstall.common.entity;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import org.springframework.data.annotation.Transient;

import java.io.Serializable;

/**
 * @program: BaseEntity
 * @description: DO抽象父类
 * @author: Zhun.Xiao
 * @create: 2018-10-29 15:20
 **/

@Data
public abstract class BaseEntity implements Serializable {
    private static final long serialVersionUID = 8240263207556866796L;

    public String toJson() {
       return JSON.toJSONString(this);
   }
}
