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
import com.cgb.bcpinstall.biz.InitializeBiz;
import com.cgb.bcpinstall.biz.InstallBiz;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.NetUtil;
import com.cgb.bcpinstall.main.MainApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MainApplication.class)
public class InitTest {

    @Autowired
    private InitializeBiz initializeBiz;

    @Autowired
    private InstallBiz installBiz;

    /**
     * 运行前指定参数：--init.config=./resources/generateInstallPackage/masterPackage/initconfig.propertise;--init.dir=./resources/generateInstallPackage/masterPackage/;--init.yes=1
     */
    @Test
    public void testInit() {
        initializeBiz.initialize();
    }

    /**
     * 运行前指定参数：--init.config=./resources/generateInstallPackage/masterPackage/initconfig.propertise;--init.dir=./resources/generateInstallPackage/masterPackage/;--init.yes=0
     */
    @Test
    public void testGetIP() {
        List<String> ipList = NetUtil.getLocalIPList();

        System.out.println(ipList.stream().collect(Collectors.joining(",")));
    }

    @Test
    public void testUpdate() {
        Map<String, String> diffMap = new HashMap<>(16);
        diffMap.put("A-BCMP-peer2.YB02.cgb.cn", "192.168.43.206:8053");
        diffMap.put("A-BCMP-peer3.YB02.cgb.cn", "192.168.43.206:8054");
        String path = "F:\\space30\\bcp-app-install\\bcp-install-main\\resources\\generateInstallPackage\\masterPackage\\backend\\bcp-app-mgr\\resources\\networkconfigs\\network-config.json";
        InitConfigEntity configEntity = new InitConfigEntity();
        configEntity.setPeerDomain("YB02.cgb.cn");
        configEntity.setOrgMSPID("Org1MSP");
        configEntity.setOrgName("Org1");
        /*installBiz.addPeerFromNetworkConfig(path, diffMap, configEntity,null);*/
    }
}
