package com.cgb.bcpinstall.service;

import com.cgb.bcpinstall.biz.helper.YamlRepresenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * @author zheng.li
 * @date 2020/3/12 15:54
 */
@Service
@Slf4j
public class YamlFileService {

    DumperOptions createOption() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        dumperOptions.setPrettyFlow(false);
        return dumperOptions;
    }

    /**
     * 将配置信息写入到yaml文件中
     *
     * @param entity
     * @param yamlFile
     * @return
     */
    public boolean writeObjectToYamlFile(Map<Object, Object> entity, String yamlFile) {
        DumperOptions options = this.createOption();
        String yaml = new Yaml(new YamlRepresenter(options)).dumpAs(entity, null, DumperOptions.FlowStyle.BLOCK);
        try {
            File file = new File(yamlFile);
            if (!file.getParentFile().exists()) {
                boolean isMk = file.getParentFile().mkdirs();
                if (!isMk) {
                    return false;
                }
            }
            FileOutputStream outputStream = new FileOutputStream(file, false);
            outputStream.write(yaml.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public Map<Object, Object> loadYamlFile(String filePath) throws FileNotFoundException {
        return new Yaml().loadAs(new FileInputStream(new File(filePath)), Map.class);
    }

}
