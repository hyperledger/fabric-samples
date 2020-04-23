package com.cgb.bcpinstall.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ProcessUtil {

    public static Result execCmd(String cmd, String[] envp, String workingDir) throws Exception {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmd, envp, new File(workingDir));
            CmdStreamReader err = new CmdStreamReader(process.getErrorStream());
            CmdStreamReader out = new CmdStreamReader(process.getInputStream());
            err.start();
            out.start();
            int exitCode = process.waitFor();
            String errMessage = err.getMessage();
            String outMessage = out.getMessage();
            if (exitCode == 0) {
                return new Result(exitCode, outMessage);
            } else {
                return new Result(exitCode, StringUtils.isEmpty(errMessage) ? outMessage : errMessage);
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class Result {
        /**
         * 返回码，0：正常，其他：异常
         */
        public int code;
        /**
         * 返回结果
         */
        public String data;
    }

    public static class CmdStreamReader extends Thread {
        private InputStream input;
        private List<String> lines = new ArrayList<String>();
        public CmdStreamReader(InputStream input) {
            this.input = input;
            setDaemon(true);
            setName("process stream reader");
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            try {
                do {
                    String line = reader.readLine();
                    if (line != null) {
                        log.info(line);
                        this.lines.add(line);
                    } else {
                        return;
                    }
                } while (true);
            } catch (IOException e) {
            	log.warn(e.getMessage());
                //e.printStackTrace();
            }
        }

        public String getMessage() {
            return StringUtils.join(this.lines, "\r\n");
        }
    }
}
