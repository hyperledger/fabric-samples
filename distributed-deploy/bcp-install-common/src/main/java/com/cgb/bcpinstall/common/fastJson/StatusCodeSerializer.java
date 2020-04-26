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
