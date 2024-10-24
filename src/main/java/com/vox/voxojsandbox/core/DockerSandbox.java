package com.vox.voxojsandbox.core;

import cn.hutool.core.io.FileUtil;
import com.vox.voxojsandbox.dao.DockerDao;
import com.vox.voxojsandbox.modal.*;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Data
@Configuration
public class DockerSandbox {


    @Resource
    private ContainerPoolExecutor containerPoolExecutor;

    @Resource
    private DockerDao dockerDao;

    /**
     * 执行代码
     *
     * @return
     */
    public ExecuteResponse execute(ExecuteRequest executeRequest) {
        return containerPoolExecutor.run(containerInfo -> executeInner(containerInfo, executeRequest));
    }

    private ExecuteResponse executeInner(ContainerInfo containerInfo,ExecuteRequest executeRequest) {
        //获取当前容器ID
        String containerId = containerInfo.getContainerId();
        List<String> inputList = executeRequest.getInputList();
        String language = executeRequest.getLanguage();
        String code = executeRequest.getCode();
        ExecuteResponse executeResponse=new ExecuteResponse();
        executeResponse.setOutputList(new ArrayList<>(inputList.size()));
        LanguageCmdEnum languageCmdEnum=LanguageCmdEnum.getEnumByValue(language);
        if(languageCmdEnum==null){
            executeResponse.setMessage("语言错误");
            return executeResponse;
        }
        // 1.写入文件
        File codeFile = saveCodeToFile(code,languageCmdEnum);
        String userCodePath = codeFile.getAbsolutePath();
        String userCodeParentPath = codeFile.getParentFile().getAbsolutePath();

        // 2.将文件复制到容器中
        dockerDao.copyFileToContainer(containerId, userCodePath);

        // 3.编译代码
        String[] compileCmd = languageCmdEnum.getCompileCmd();
        // 不为空则代表需要编译
        if (compileCmd != null) {
            boolean success=compileCode(containerId, compileCmd);
            if(!success){
                executeResponse.setMessage("编译出错");
                return executeResponse;
            }
        }

        // 4.运行代码
        List<ExecuteMessage> executeMessageList = runCode(containerId, languageCmdEnum,inputList, executeResponse);
        log.info("运行中...");
        // 运行出错，则直接返回
        if(StringUtils.isNotBlank(executeResponse.getMessage())){
            return executeResponse;
        }

        // 5.整理输出结果
        getOutputResponse(executeMessageList,executeResponse);

        // 清理文件和容器，这一步由容器池完成
//            cleanFileAndContainer(userCodeParentPath, containerId);

        return executeResponse;
    }

    /**
     * @param executeMessageList 每个案例的执行信息
     * @param executeResponse    全局执行结果
     * @return 最终执行结果
     */
    private void getOutputResponse(List<ExecuteMessage> executeMessageList, ExecuteResponse executeResponse) {
        long maxTime = 0;
        long maxMemory = 1;
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (executeMessage.getTime() > maxTime) {
                maxTime = executeMessage.getTime();
            }
//            if (executeMessage.getMemory() > maxMemory) {
//                maxMemory = executeMessage.getMemory();
//            }
            //收集结果
            System.out.println("每个输出信息："+ executeMessage.getMessage());
            executeResponse.getOutputList().add(executeMessage.getMessage());
        }
        executeResponse.setTime(maxTime);
        executeResponse.setMemory(maxMemory);
    }

    /**
     * 运行代码
     *
     * @param containerId     容器 ID
     * @param languageCmdEnum 编程语言枚举
     * @param inputList
     * @param executeResponse
     * @return {@link List<ExecuteMessage>}
     */
    private List<ExecuteMessage> runCode(String containerId, LanguageCmdEnum languageCmdEnum, List<String> inputList, ExecuteResponse executeResponse) {

        String[] runCmdNative = languageCmdEnum.getRunCmd();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();

        //遍历所有输入
        for(String input: inputList) {
            String[] inputArray=input.split(" ");
            String[] runCmd= ArrayUtils.addAll(runCmdNative,inputArray);
            ExecuteMessage executeMessage = dockerDao.execCmd(containerId, runCmd);
            //如果执行出错，不用输入下一个案例，直接返回
            executeMessageList.add(executeMessage);
            if(!executeMessage.isSuccess()){
                executeResponse.setLastInput(input);
                log.error("执行错误："+executeMessage.getErrorMessage());
                executeResponse.setMessage("执行出错");

                return executeMessageList;
            }
        }
        return executeMessageList;
    }

    /**
     * 编译代码
     *
     * @param containerId        容器 ID
     * @param compileCmd         编译命令
     * @return
     */
    private boolean compileCode(String containerId, String[] compileCmd) {
        ExecuteMessage executeMessage = dockerDao.execCmd(containerId, compileCmd);
        log.info("编译完成...");
        if(!executeMessage.isSuccess()){
            log.error("编译错误："+executeMessage.getErrorMessage());
        }
        return executeMessage.isSuccess();
    }

    /**
     * 保存代码到文件
     *
     * @param code           代码
     * @param languageCmdEnum 编程语言枚举
     * @return {@link File}
     */
    private File saveCodeToFile(String code, LanguageCmdEnum languageCmdEnum) {
        String userDir = System.getProperty("user.dir");
        String language = languageCmdEnum.getLanguage();
        String globalCodePathName = userDir + File.separator + "tempCode" + File.separator + language;
        // 判断全局代码目录是否存在，没有则新建
        File globalCodePath = new File(globalCodePathName);
        if (!globalCodePath.exists()) {
            boolean mkdir = globalCodePath.mkdirs();
            if (!mkdir) {
                log.info("创建全局代码目录失败");
            }
        }

        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + languageCmdEnum.getSaveFileName();
        File file = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return file;
    }

}
