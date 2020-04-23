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
