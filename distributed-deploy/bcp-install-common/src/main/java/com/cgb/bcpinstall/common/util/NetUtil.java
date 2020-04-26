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
package com.cgb.bcpinstall.common.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetUtil {

    public static List<String> getLocalIPList() {
        List<String> ipList = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            NetworkInterface networkInterface;
            Enumeration<InetAddress> inetAddresses;
            InetAddress inetAddress;
            String ip;
            while (networkInterfaces.hasMoreElements()) {
                networkInterface = networkInterfaces.nextElement();
                if (networkInterface.getName().toLowerCase().contains("docker")
                        || networkInterface.getDisplayName().toLowerCase().contains("docker")
//                || networkInterface.getName().startsWith("lo")
                || networkInterface.getName().startsWith("vmnet")
                || networkInterface.getName().startsWith("br-")) {
                    continue;
                }
                inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    inetAddress = inetAddresses.nextElement();
                    if (inetAddress != null && inetAddress instanceof Inet4Address) { // IPV4
                        ip = inetAddress.getHostAddress();
                        ipList.add(ip);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        return ipList;
    }

    public static String getMyNormalIP() {
        List<String> ipList = NetUtil.getLocalIPList();
        for (String ip : ipList) {
            if (!"127.0.0.1".equalsIgnoreCase(ip)) {
                return ip;
            }
        }

        return null;
    }

    public static boolean ipIsMine(String ip) {
        List<String> ipList = NetUtil.getLocalIPList();
        return ipList.stream().anyMatch(s -> s.equalsIgnoreCase(ip));
    }
}
