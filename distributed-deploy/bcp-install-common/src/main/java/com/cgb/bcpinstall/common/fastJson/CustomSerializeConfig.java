package com.cgb.bcpinstall.common.fastJson;

import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.cgb.bcpinstall.common.response.StatusCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


/**
 * class_name: CustomSerializeConfig
 * package: com.midea.mobile.uom.main.fastjson
 * describe: Json序列化配置
 *
 * @author: zhun.xiao
 * @date: 2017/11/25
 * @time: 20:15
 **/
public class CustomSerializeConfig extends SerializeConfig {
    public CustomSerializeConfig() {
    }

    @Override
    public ObjectSerializer getObjectWriter(Class<?> clazz) {
        ObjectSerializer writer = this.get(clazz);
        if (writer == null) {
            if (StatusCode.class.isAssignableFrom(clazz)) {
                this.put(clazz, StatusCodeSerializer.INSTANCE);
            }
            if (LocalDateTime.class.isAssignableFrom(clazz)) {
                this.put(clazz, JodaTimeDeserializer.instance);
            }
            if (LocalDate.class.isAssignableFrom(clazz)) {
                this.put(clazz, JodaTimeDeserializer.instance);
            }
            if (LocalTime.class.isAssignableFrom(clazz)) {
                this.put(clazz, JodaTimeDeserializer.instance);
            }
        }

        return super.getObjectWriter(clazz);
    }
}
