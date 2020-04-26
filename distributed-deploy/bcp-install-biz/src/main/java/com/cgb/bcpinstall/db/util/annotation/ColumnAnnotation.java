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
package com.cgb.bcpinstall.db.util.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
@Inherited
public @interface ColumnAnnotation {

    /**
     * 列名
     *
     * @return
     */
    String columnName();

    /**
     * 数据库类型
     *
     * @return
     */
    String dbType();

    /**
     * 显示长度
     *
     * @return
     */
    int length() default 32;

    /**
     * 是否为空
     *
     * @return
     */
    boolean isNull() default true;

    /**
     * 是否唯一
     *
     * @return
     */
    boolean isUnique() default false;

    /**
     * 是否可通过字段查询
     */
    boolean isMaster() default  false;

    /**
     * 是否模糊查询
     * @return
     */
    boolean isLike() default false;
}
