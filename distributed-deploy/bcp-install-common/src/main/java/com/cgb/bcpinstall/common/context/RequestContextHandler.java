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
package com.cgb.bcpinstall.common.context;

import com.cgb.bcpinstall.common.constant.LoginConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @program: RequestContextHandler
 * @description: 每个请求对应的线程内存数据管理
 * @author: Zhun.Xiao
 * @create: 2018-11-22 16:44
 **/
@Slf4j
public class RequestContextHandler {

    public static ThreadLocal<Map<String, Object>> threadLocal = new ThreadLocal<>();

    public static void set(String key, Object value) {
        Map<String, Object> map = threadLocal.get();
        if (null == map) {
            map = new HashMap<String, Object>();
            threadLocal.set(map);
        }
        map.put(key, value);

    }

    public static Object get(String key) {
        Map<String, Object> map = threadLocal.get();
        if (null == map) {
            map = new HashMap<String, Object>();
            threadLocal.set(map);
        }
        return map.get(key);

    }

    public static void setCurrentUserName(String userName) {
        set(LoginConstant.CURRENT_USER_NAME, userName);
    }

    public static void setCurrentUserId(String id) {
        set(LoginConstant.CURRENT_USER_ID, id);

    }

    public static void setCurrentUserToken(String token) {
        set(LoginConstant.CURRENT_USER_TOKEN, token);

    }

    public static String object2String(Object value) {
        return value == null ? null : value.toString();
    }

    public static void remove() {
        threadLocal.remove();
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {
        private final Logger logger = LoggerFactory.getLogger(UnitTest.class);

        @Test
        public void testRequestHandler() throws InterruptedException {
            RequestContextHandler.set("name", "xiaozhun");
            new Thread(() -> {
                RequestContextHandler.set("name", "tyrant");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    log.error(e.toString());
                }
                assertEquals(RequestContextHandler.get("name"), "tyrant");
                logger.info("thread one done");

            }).start();

            new Thread(() -> {
                RequestContextHandler.set("name", "batman");
                assertEquals(RequestContextHandler.get("name"), "batman");
                logger.info("thread two done");

            }).start();

            Thread.sleep(5000);
            assertEquals(RequestContextHandler.get("name"), "xiaozhun");
            logger.info("main thread done");
        }

        @Test
        public void TestUser() {
            RequestContextHandler.setCurrentUserId(111 + "");
            assertEquals(RequestContextHandler.get(LoginConstant.CURRENT_USER_ID), "xiao-id");
            RequestContextHandler.setCurrentUserName("xiao-name");
            assertEquals(RequestContextHandler.get(LoginConstant.CURRENT_USER_NAME), "xiao-name");
            RequestContextHandler.setCurrentUserToken("xiao-token");
            assertEquals(RequestContextHandler.get(LoginConstant.CURRENT_USER_TOKEN), "xiao-token");

        }
    }
}
