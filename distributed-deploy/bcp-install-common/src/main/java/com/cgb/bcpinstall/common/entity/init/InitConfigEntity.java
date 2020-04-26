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
package com.cgb.bcpinstall.common.entity.init;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class InitConfigEntity implements Serializable {
    private static final long serialVersionUID = -7686888493480177456L;

    private String network;
    private String channelName;
    private String orgMSPID;
    private String orgName;
    private String ordererDomain;
    private String peerDomain;
    private Map<String, String> ordererHostConfig;
    private Map<String, String> peerHostConfig;
    private Map<String, String> metricPortConfig;
    private Map<String, String> couchdbPortConfig;
}
