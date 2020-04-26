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
package com.cgb.bcpinstall.common.response;

import java.io.Serializable;

/**
 * @program: StatusCode
 * @description:
 * @author: Zhun.Xiao
 * @create: 2019-03-19 09:04
 **/
public interface StatusCode<T> extends Serializable {
    /**
     * 状态码
     *
     * @return
     */
    public T code();

    /**
     * 状态码描述
     *
     * @return
     */
    public String msg();
}
