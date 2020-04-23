package com.cgb.bcpinstall.db.util.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Inherited
public @interface TableAnnotation {

    /**
     * 表名
     *
     * @return
     */
    String value();
}
