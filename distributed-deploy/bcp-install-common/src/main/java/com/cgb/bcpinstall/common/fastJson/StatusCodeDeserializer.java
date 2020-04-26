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

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.cgb.bcpinstall.common.response.ResponseCode;

import java.lang.reflect.Type;

public class StatusCodeDeserializer implements ObjectDeserializer {
    @Override
    public <T> T deserialze(DefaultJSONParser defaultJSONParser, Type type, Object o) {
        JSONLexer lexer = defaultJSONParser.getLexer();
        String value = lexer.stringVal();
        for (ResponseCode responseCode : ResponseCode.values()) {
            if (value.equals(responseCode.code())) {
                return (T) responseCode;
            }
        }
        throw new RuntimeException("no status code: " + value);
    }

    @Override
    public int getFastMatchToken() {
        return 0;
    }
}
