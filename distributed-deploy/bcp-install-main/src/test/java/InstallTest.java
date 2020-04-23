import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.biz.InstallBiz;
import com.cgb.bcpinstall.common.entity.InstallStatusEnum;
import com.cgb.bcpinstall.common.entity.OSEnum;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.UpdateCmd;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.HttpClientUtil;
import com.cgb.bcpinstall.common.util.OSinfo;
import com.cgb.bcpinstall.db.CheckPointDb;
import com.cgb.bcpinstall.db.table.NodeDO;
import com.cgb.bcpinstall.main.MainApplication;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MainApplication.class)
@WebAppConfiguration
@AutoConfigureMockMvc
public class InstallTest {
    @Autowired
    private InstallBiz installBiz;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HttpClientUtil httpClientUtil;

    @Autowired
    private CheckPointDb checkPointDb;

    /**
     * 运行前指定参数：--init.config=./resources/generateInstallPackage/masterPackage/initconfig.propertise;--init.dir=./resources/generateInstallPackage/masterPackage/;--init.yes=0
     */
    @Test
    public void testPackRoleFile() {
        OSEnum osType;
        if (OSinfo.isWindows()) {
            osType = OSEnum.WINDOWS;
        } else if (OSinfo.isMacOS() || OSinfo.isMacOSX()) {
            osType = OSEnum.MAC;
        } else {
            osType = OSEnum.LINUX;
        }

        List<RoleEnum> roleList = new ArrayList<>();
        roleList.add(RoleEnum.PEER);
        /*String packFilePath = installBiz.packInstallFiles("172.100.10.4", roleList, osType);
        System.out.println("安装包路径: " + packFilePath);*/
    }

    @Test
    public void testDownloadFile() throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/v1/install/getPackage/MAC")).andReturn();
        System.out.println(mvcResult.getResponse().getContentAsString());
    }

    @Test
    public void testSendUpdateCmd() throws Exception {
        String filePath = "/resources/generateInstallPackage/masterPackage/initconfig.propertise";
        File file = new File(filePath);
        System.out.println(file.getParent());
        System.out.println(new File(file.getParent()).getName());

        UpdateCmd cmd = new UpdateCmd();
        /*cmd.setRole(RoleEnum.WEB);*/

        MockMultipartFile firstFile = new MockMultipartFile("file", "filename.txt", null, "some xml".getBytes());
        MockMultipartFile jsonFile = new MockMultipartFile("content", "", "application/json", JSONObject.toJSONString(cmd).getBytes());

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/install/update").file(firstFile).file(jsonFile)).andReturn();
        System.out.println(mvcResult.getResponse().getContentAsString());
    }

    @Test
    public void testDb() {
        NodeDO nodeDO = new NodeDO();
        nodeDO.setOrgMspId("Org1MSP");
        nodeDO.setHostName("order.example.com");
        nodeDO.setRole(RoleEnum.ORDER);
        nodeDO.setIp("127.0.0.1");
        nodeDO.setPort(7070);
        nodeDO.setStatus(InstallStatusEnum.SUCCESS);
        try {
            checkPointDb.addNodeRecord(nodeDO);

            List<NodeDO> found = checkPointDb.find(nodeDO);
            System.out.println(found.size());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
