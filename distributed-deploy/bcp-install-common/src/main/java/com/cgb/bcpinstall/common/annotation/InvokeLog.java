package com.cgb.bcpinstall.common.annotation;


import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InvokeLog {

    /**
     * 是否写入日志 默认不写入
     *
     * @return
     */
    boolean persistence() default false;

    /**
     * 调用方法名称
     *
     * @return
     */
    String name() default "";

    /**
     * 描述
     *
     * @return
     */
    String description() default "";

    /**
     * 是否打印返回值
     *
     * @return
     */
    boolean printReturn() default true;

}
