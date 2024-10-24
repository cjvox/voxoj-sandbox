//package com.vox.voxojsandbox.old;
//
//import cn.hutool.core.io.FileUtil;
//import com.github.dockerjava.api.DockerClient;
//import com.github.dockerjava.api.async.ResultCallback;
//import com.github.dockerjava.api.command.CreateContainerCmd;
//import com.github.dockerjava.api.command.CreateContainerResponse;
//import com.github.dockerjava.api.command.ExecCreateCmdResponse;
//import com.github.dockerjava.api.model.*;
//import com.github.dockerjava.core.DefaultDockerClientConfig;
//import com.github.dockerjava.core.DockerClientConfig;
//import com.github.dockerjava.core.DockerClientImpl;
//import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
//import com.github.dockerjava.transport.DockerHttpClient;
//import com.vox.voxojsandbox.modal.ExecuteMessage;
//import com.vox.voxojsandbox.modal.ExecuteRequest;
//import com.vox.voxojsandbox.modal.ExecuteResponse;
//import com.vox.voxojsandbox.modal.LanguageCmdEnum;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.ArrayUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.util.StopWatch;
//
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.time.Duration;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.ThreadPoolExecutor;
//
//@Slf4j
//@Data
////@ConfigurationProperties(prefix = "codesandbox.config")
////@Configuration
//@Deprecated
//public class DockerSandboxOld {
//    private static final DockerClient DOCKER_CLIENT;
//
//    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
//            2, // 核心线程数
//            3, // 最大线程数
//            60, // 空闲线程存活时间
//            TimeUnit.SECONDS, // 时间单位
//            new ArrayBlockingQueue<>(5) // 队列容量
//    );
//    static {
//        DockerClientConfig standard = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
//        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
//                .dockerHost(standard.getDockerHost())
//                .sslConfig(standard.getSSLConfig())
//                .maxConnections(100)
//                .connectionTimeout(Duration.ofSeconds(30))
//                .responseTimeout(Duration.ofSeconds(45))
//                .build();
//        DOCKER_CLIENT = DockerClientImpl.getInstance(standard, httpClient);
//    }
//
//    /**
//     * 代码沙箱的镜像，Dockerfile 构建的镜像名，默认为 codesandbox:latest
//     */
//    private String image = "codesandbox:latest";
//
//    /**
//     * 内存限制，单位为字节，默认为 1024 * 1024 * 60 MB
//     */
//    private long memoryLimit = 1024 * 1024 * 60;
//
//    private long memorySwap = 0;
//
//    /**
//     * 最大可消耗的 cpu 数
//     */
//    private long cpuCount = 1;
//
//    private long timeoutLimit = 1;
//
//    private TimeUnit timeUnit = TimeUnit.SECONDS;
//
//    /**
//     * 执行代码
//     *
//     * @return
//     */
//    public ExecuteResponse execute(ExecuteRequest executeRequest) {
//        List<String> inputList = executeRequest.getInputList();
//        String language = executeRequest.getLanguage();
//        String code = executeRequest.getCode();
//        ExecuteResponse executeResponse=new ExecuteResponse();
//        executeResponse.setOutputList(new ArrayList<>(inputList.size()));
//        LanguageCmdEnum languageCmdEnum=LanguageCmdEnum.getEnumByValue(language);
//        if(languageCmdEnum==null){
//            executeResponse.setMessage("语言错误");
//            return executeResponse;
//        }
//
//        // 1.写入文件
//        File codeFile = saveCodeToFile(code,languageCmdEnum);
//
//        String userCodePath = codeFile.getAbsolutePath();
//        String userCodeParentPath = codeFile.getParentFile().getAbsolutePath();
//
//        // 2.创建容器
//        String containerId = createContainer(userCodePath);
//
//        // 3.编译代码
//        String[] compileCmd = languageCmdEnum.getCompileCmd();
//
//        // 不为空则代表需要编译
//        if (compileCmd != null) {
//            boolean success=compileCode(containerId, compileCmd);
//            if(!success){
//                executeResponse.setMessage("编译出错");
//                cleanFileAndContainer(userCodeParentPath, containerId);
//                return executeResponse;
//            }
//        }
//
//        // 4.运行代码
//        List<ExecuteMessage> executeMessageList = runCode(containerId, languageCmdEnum,inputList, executeResponse);
//        log.info("运行中...");
//
//        if(StringUtils.isNotBlank(executeResponse.getMessage())){
//            cleanFileAndContainer(userCodeParentPath, containerId);
//            return executeResponse;
//        }
//        // 5.整理输出结果
//        getOutputResponse(executeMessageList,executeResponse);
//
//        // 清理文件和容器，异步清理：
//
//        cleanFileAndContainer(userCodeParentPath, containerId);
//
//        return executeResponse;
//    }
//
//    /**
//     * @param executeMessageList 每个案例的执行信息
//     * @param executeResponse    全局执行结果
//     * @return 最终执行结果
//     */
//    private void getOutputResponse(List<ExecuteMessage> executeMessageList, ExecuteResponse executeResponse) {
//        long maxTime = 0;
//        long maxMemory = 1;
//        for (ExecuteMessage executeMessage : executeMessageList) {
//            if (executeMessage.getTime() > maxTime) {
//                maxTime = executeMessage.getTime();
//            }
////            if (executeMessage.getMemory() > maxMemory) {
////                maxMemory = executeMessage.getMemory();
////            }
//            //收集结果
//            System.out.println("每个输出信息："+ executeMessage.getMessage());
//            executeResponse.getOutputList().add(executeMessage.getMessage());
//        }
//        executeResponse.setTime(maxTime);
//        executeResponse.setMemory(maxMemory);
//    }
//
//    /**
//     * 运行代码
//     *
//     * @param containerId     容器 ID
//     * @param languageCmdEnum 编程语言枚举
//     * @param inputList
//     * @param executeResponse
//     * @return {@link List<ExecuteMessage>}
//     */
//    private List<ExecuteMessage> runCode(String containerId, LanguageCmdEnum languageCmdEnum, List<String> inputList, ExecuteResponse executeResponse) {
//
//        String[] runCmdNative = languageCmdEnum.getRunCmd();
//        List<ExecuteMessage> executeMessageList = new ArrayList<>();
//
//        //遍历所有输入
//        for(String input: inputList) {
//            String[] inputArray=input.split(" ");
//            String[] runCmd= ArrayUtils.addAll(runCmdNative,inputArray);
//            ExecuteMessage executeMessage = execCmd(containerId, runCmd);
//            //如果执行出错，不用输入下一个案例，直接返回
//            executeMessageList.add(executeMessage);
//            if(!executeMessage.isSuccess()){
//                executeResponse.setLastInput(input);
//                log.error("执行错误："+executeMessage.getErrorMessage());
//                executeResponse.setMessage("执行出错");
//
//                return executeMessageList;
//            }
//        }
//        return executeMessageList;
//    }
//
//    /**
//     * 编译代码
//     *
//     * @param containerId        容器 ID
//     * @param compileCmd         编译命令
//     * @return
//     */
//    private boolean compileCode(String containerId, String[] compileCmd) {
//        ExecuteMessage executeMessage = execCmd(containerId, compileCmd);
//        log.info("编译完成...");
//        if(!executeMessage.isSuccess()){
//            log.error("编译错误："+executeMessage.getErrorMessage());
//        }
//        return executeMessage.isSuccess();
//    }
//
//    /**
//     * 保存代码到文件
//     *
//     * @param code           代码
//     * @param languageCmdEnum 编程语言枚举
//     * @return {@link File}
//     */
//    private File saveCodeToFile(String code, LanguageCmdEnum languageCmdEnum) {
//        String userDir = System.getProperty("user.dir");
//        String language = languageCmdEnum.getLanguage();
//        String globalCodePathName = userDir + File.separator + "tempCode" + File.separator + language;
//        // 判断全局代码目录是否存在，没有则新建
//        File globalCodePath = new File(globalCodePathName);
//        if (!globalCodePath.exists()) {
//            boolean mkdir = globalCodePath.mkdirs();
//            if (!mkdir) {
//                log.info("创建全局代码目录失败");
//            }
//        }
//
//        // 把用户的代码隔离存放
//        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
//        String userCodePath = userCodeParentPath + File.separator + languageCmdEnum.getSaveFileName();
//        File file = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
//        return file;
//    }
//
//
//    /**
//     * 清理文件和容器
//     * 采用线程池异步处理
//     * @param userCodePath 用户代码路径
//     * @param containerId  容器 ID
//     */
//    private static void cleanFileAndContainer(String userCodePath, String containerId) {
//        threadPoolExecutor.execute(() -> {
//            // 清理临时目录
//            FileUtil.del(userCodePath);
//
//            // 关闭并删除容器
//            DOCKER_CLIENT.stopContainerCmd(containerId).exec();
//            DOCKER_CLIENT.removeContainerCmd(containerId).exec();
//        });
//    }
//
//
//    /**
//     * 执行命令
//     *
//     * @param containerId 容器 ID
//     * @param cmd         CMD
//     * @return {@link ExecuteMessage}
//     */
//    public ExecuteMessage execCmd(String containerId, String[] cmd) {
//        StopWatch stopWatch = new StopWatch();
//        stopWatch.start();
//        // 正常返回信息
//        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
//        // 错误信息
//        ByteArrayOutputStream errorResultStream = new ByteArrayOutputStream();
//
//        // 结果
//        final boolean[] result = {true};
//        final boolean[] timeout = {true};
//        try (ResultCallback.Adapter<Frame> frameAdapter = new ResultCallback.Adapter<Frame>() {
//
//            @Override
//            public void onComplete() {
//                // 是否超时
//                timeout[0] = false;
//                super.onComplete();
//            }
//
//            @Override
//            public void onNext(Frame frame) {
//                StreamType streamType = frame.getStreamType();
//                byte[] payload = frame.getPayload();
//                if (StreamType.STDERR.equals(streamType)) {
//                    try {
//                        result[0] = false;
//                        errorResultStream.write(payload);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                } else {
//                    try {
//                        resultStream.write(payload);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//                super.onNext(frame);
//            }
//        }) {
//            ExecCreateCmdResponse execCompileCmdResponse = DOCKER_CLIENT.execCreateCmd(containerId)
//                    .withCmd(cmd)
//                    .withAttachStderr(true)
//                    .withAttachStdin(true)
//                    .withAttachStdout(true)
//                    .exec();
//            String execId = execCompileCmdResponse.getId();
//            DOCKER_CLIENT.execStartCmd(execId).exec(frameAdapter).awaitCompletion(timeoutLimit, timeUnit);
//            stopWatch.stop();
//            // 超时
//            if (timeout[0]) {
//                return ExecuteMessage
//                        .builder()
//                        .success(false)
//                        .errorMessage("执行超时")
//                        .build();
//            }
//
//            return ExecuteMessage
//                    .builder()
//                    .success(result[0])
//                    .message(StringUtils.chop(resultStream.toString()))
//                    .errorMessage(errorResultStream.toString())
//                    .time(stopWatch.getTotalTimeMillis())
//                    .build();
//
//        } catch (IOException | InterruptedException e) {
//            return ExecuteMessage
//                    .builder()
//                    .success(false)
//                    .errorMessage(e.getMessage())
//                    .build();
//        }
//    }
//
//
//    /**
//     * 创建容器
//     *
//     * @return {@link String}
//     */
//    private String createContainer(String codeFile) {
//        CreateContainerCmd containerCmd = DOCKER_CLIENT.createContainerCmd(image);
//        HostConfig hostConfig = new HostConfig();
//        hostConfig.withMemory(memoryLimit);
//        hostConfig.withMemorySwap(memorySwap);
//        hostConfig.withCpuCount(cpuCount);
//
//        CreateContainerResponse createContainerResponse = containerCmd
//                .withHostConfig(hostConfig)
//                .withNetworkDisabled(true)
//                .withAttachStdin(true)
//                .withAttachStderr(true)
//                .withAttachStdout(true)
//                .withTty(true)
//                .exec();
//        // 启动容器
//        String containerId = createContainerResponse.getId();
//        DOCKER_CLIENT.startContainerCmd(containerId).exec();
//
//        // 将代码复制到容器中
//        DOCKER_CLIENT.copyArchiveToContainerCmd(containerId)
//                .withHostResource(codeFile)
//                .withRemotePath("/box")
//                .exec();
//        return containerId;
//    }
//
//
//}
