package com.cgb.bcpinstall.db.util.object;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
public class BaseDO implements Serializable {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 数据版本
     */
    private Long revision;

    /**
     * 数据创建时间
     */
    private Date createTime;

    /**
     * 数据修改时间
     */
    private Date modifyTime;

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
