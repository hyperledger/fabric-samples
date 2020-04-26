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
import com.cgb.bcpinstall.common.constant.DateConstant;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * class_name: JodaTimeDeserializer
 * package: com.midea.mobile.talkbar.common.utils
 * describe: fastjson 对 localdatetime格式化支持
 *
 * @author: zhun.xiao
 * @date: 2017/12/1
 * @time: 18:01
 **/
public class JodaTimeDeserializer implements ObjectSerializer {

    public static final JodaTimeDeserializer instance = new JodaTimeDeserializer();

    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.getWriter();
        String resultStr = object.toString();
        if (object == null) {
            out.writeNull();
            return;
        }
        if (fieldType == LocalDateTime.class) {
            LocalDateTime localDateTime = (LocalDateTime) object;
            resultStr = localDateTime.format(DateTimeFormatter.ofPattern(DateConstant.DATE_TIME_FORMATTER));
        } else if (fieldType == LocalDate.class) {
            LocalDate localDate = (LocalDate) object;
            resultStr = localDate.format(DateTimeFormatter.ofPattern(DateConstant.DATE_FORMATTER));
        } else if (fieldType == LocalTime.class) {
            LocalTime localTime = (LocalTime) object;
            resultStr = localTime.format(DateTimeFormatter.ofPattern(DateConstant.TIME_FORMATTER));
        }
        out.writeString(resultStr);
    }
}
