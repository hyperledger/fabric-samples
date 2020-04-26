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
