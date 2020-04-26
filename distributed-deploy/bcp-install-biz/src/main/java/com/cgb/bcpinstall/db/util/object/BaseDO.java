/*
 *  Copyright CGB Corp All Rights Reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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
