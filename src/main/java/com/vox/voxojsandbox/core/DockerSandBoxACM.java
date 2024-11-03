package com.vox.voxojsandbox.core;


import com.vox.voxojsandbox.dao.DockerDao;
import com.vox.voxojsandbox.modal.ExecuteMessage;
import com.vox.voxojsandbox.modal.ExecuteResponse;
import com.vox.voxojsandbox.modal.LanguageCmdEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DockerSandBoxACM extends DockerSandboxTemplate{
    @Resource
    private DockerDao dockerDao;

    @Override
    public List<ExecuteMessage> runCode(String containerId, LanguageCmdEnum languageCmdEnum, List<String> inputList, ExecuteResponse executeResponse) {
        String[] runCmdNative = languageCmdEnum.getRunCmd();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        try {
            for(String input: inputList) {
                //将参数写入
                InputStream inputStream = new ByteArrayInputStream((input+"\n").getBytes(StandardCharsets.UTF_8));

                ExecuteMessage executeMessage = dockerDao.execCmdInteractive(containerId, runCmdNative, inputStream);

//                log.info("内存使用情况："+executeMessage.getMemory());
                //如果执行出错，不用输入下一个案例，直接返回
                executeMessageList.add(executeMessage);
                if(!executeMessage.isSuccess()){
                    executeResponse.setLastInput(input);
                    log.error("执行错误："+executeMessage.getErrorMessage());
                    executeResponse.setMessage("执行出错"+executeMessage.getErrorMessage());
                    return executeMessageList;
                }
                inputStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return executeMessageList;
    }
}
