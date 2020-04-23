package com.cgb.bcpinstall.common.fastJson;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.cgb.bcpinstall.common.response.StatusCode;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * class_name: StatusCodeSerializer
 * package: com.midea.mobile.uom.main.fastjson
 * describe: StatusCode fastjson序列化code值
 *
 * @author: zhun.xiao
 * @date: 2017/11/24
 * @time: 23:21
 **/

public class StatusCodeSerializer implements ObjectSerializer {
    public static final StatusCodeSerializer INSTANCE = new StatusCodeSerializer();

    StatusCodeSerializer() {
    }

    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;
        StatusCode value = (StatusCode) object;
        if (value == null) {
            out.writeNull();
        } else {
            out.writeString((String) value.code());
        }
    }
}
