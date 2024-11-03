package com.vox.voxojsandbox.dao;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.vox.voxojsandbox.modal.ContainerInfo;
import com.vox.voxojsandbox.modal.ExecuteMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StopWatch;

import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * docker 相关操作
 * 用于创建、启动、执行 docker 容器。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "codesandbox.config")
public class DockerDao {

    /**
     * 代码沙箱的镜像，Dockerfile 构建的镜像名，默认为 codesandbox:latest
     */
    private String image = "codesandbox:latest";

    private static final Runtime RUNTIME = Runtime.getRuntime();
    /**
     * 内存限制，单位为字节，默认为 1024 * 1024 * 60 MB
     */
    private long memoryLimit = 1024 * 1024 * 60;

    private long memorySwap = 0;

    /**
     * 最大可消耗的 cpu 数
     */
    private long cpuCount = 1;

    private long timeoutLimit = 1;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    private static final DockerClient DOCKER_CLIENT;

    static {
        DockerClientConfig standard = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(standard.getDockerHost())
                .sslConfig(standard.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        DOCKER_CLIENT = DockerClientImpl.getInstance(standard, httpClient);
    }

    /**
     * 执行命令
     *
     * @param containerId 容器 ID
     * @param cmd         CMD
     * @return {@link ExecuteMessage}
     */
    public ExecuteMessage execCmd(String containerId, String[] cmd) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        // 正常返回信息
        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        // 错误信息
        ByteArrayOutputStream errorResultStream = new ByteArrayOutputStream();

        // 结果
        final boolean[] result = {true};
        final boolean[] timeout = {true};
        try (ResultCallback.Adapter<Frame> frameAdapter = new ResultCallback.Adapter<Frame>() {

            @Override
            public void onComplete() {
                // 是否超时
                timeout[0] = false;
                super.onComplete();
            }

            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                byte[] payload = frame.getPayload();
                if (StreamType.STDERR.equals(streamType)) {
                    try {
                        result[0] = false;
                        errorResultStream.write(payload);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        resultStream.write(payload);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                super.onNext(frame);
            }
        }) {
            ExecCreateCmdResponse execCompileCmdResponse = DOCKER_CLIENT.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            String execId = execCompileCmdResponse.getId();
            DOCKER_CLIENT.execStartCmd(execId).exec(frameAdapter).awaitCompletion(timeoutLimit, timeUnit);
            stopWatch.stop();
            // 超时
            if (timeout[0]) {
                return ExecuteMessage
                        .builder()
                        .success(false)
                        .errorMessage("执行超时")
                        .build();
            }

            return ExecuteMessage
                    .builder()
                    .success(result[0])
                    .message(StringUtils.chop(resultStream.toString()))
                    .errorMessage(errorResultStream.toString())
                    .time(stopWatch.getTotalTimeMillis())
                    .build();

        } catch (IOException | InterruptedException e) {
            return ExecuteMessage
                    .builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public ExecuteMessage execCmdInteractive(String containerId, String[] cmd, InputStream ArgsInputStream) throws IOException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 正常返回信息
        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        // 错误信息
        ByteArrayOutputStream errorResultStream = new ByteArrayOutputStream();

        // 标记结果是否成功
        final boolean[] result = {true};
        final boolean[] timeout = {true};
        long[] memory= {0};
        try (ResultCallback.Adapter<Frame> frameAdapter = new ResultCallback.Adapter<Frame>() {

            @Override
            public void onComplete() {
                // 任务是否超时
                timeout[0] = false;
                super.onComplete();
            }

            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                byte[] payload = frame.getPayload();

                if (StreamType.STDERR.equals(streamType)) {
                    try {
                        result[0] = false;
                        errorResultStream.write(payload);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        resultStream.write(payload);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                super.onNext(frame);
            }
        }) {
            // 创建执行命令
            ExecCreateCmdResponse execCompileCmdResponse = DOCKER_CLIENT.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStderr(true)
                    .withAttachStdin(true)  // 打开输入流
                    .withAttachStdout(true)
                    .exec();
            String execId = execCompileCmdResponse.getId();

            // 启动命令执行，并等待执行完成或超时
            DOCKER_CLIENT.execStartCmd(execId)
                    .withStdIn(ArgsInputStream)// 传递输入流
                    .exec(frameAdapter)
                    .awaitCompletion(timeoutLimit, timeUnit);

//            DOCKER_CLIENT.statsCmd(containerId)
//                    .exec(new ResultCallback.Adapter<Statistics>() {
//                        @Override
//                        public void onNext(Statistics stats) {
//                            MemoryStatsConfig memoryStats = stats.getMemoryStats();
//                            memory[0] = memoryStats.getUsage();
//                            super.onNext(stats);
//                        }
//                    })
//                    .awaitCompletion(timeoutLimit, timeUnit); // 等待获取内存使用情况
            stopWatch.stop();

            // 超时处理
            if (timeout[0]) {
                return ExecuteMessage
                        .builder()
                        .success(false)
                        .errorMessage("执行超时")
                        .build();
            }

            return ExecuteMessage
                    .builder()
                    .success(result[0])
                    .message(StringUtils.chop(resultStream.toString()))
                    .errorMessage(errorResultStream.toString())
                    .time(stopWatch.getTotalTimeMillis())
                    .memory(memory[0])
                    .build();

        } catch (IOException | InterruptedException e) {
            return ExecuteMessage
                    .builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }


    public ContainerInfo startContainer(String codePath) {
        CreateContainerCmd containerCmd = DOCKER_CLIENT.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(memoryLimit);
        hostConfig.withMemorySwap(memorySwap);
        hostConfig.withCpuCount(cpuCount);
//        hostConfig.withReadonlyRootfs(true);
//        hostConfig.setBinds(new Bind(codePath, new Volume("/box")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();
        log.info("containerId: {}", containerId);
        // 启动容器
        DOCKER_CLIENT.startContainerCmd(containerId).exec();
        return ContainerInfo
                .builder()
                .containerId(containerId)
                .codePathName(codePath)
                .lastActivityTime(System.currentTimeMillis())
                .build();
    }

    /**
     * 复制文件到容器
     *
     * @param codeFile    代码文件
     * @param containerId 容器 ID
     */
    public void copyFileToContainer(String containerId, String codeFile) {
        DOCKER_CLIENT.copyArchiveToContainerCmd(containerId)
                .withHostResource(codeFile)
                .withRemotePath("/box")
                .exec();
    }

    public void cleanContainer(String containerId) {
        DOCKER_CLIENT.stopContainerCmd(containerId).exec();
        DOCKER_CLIENT.removeContainerCmd(containerId).exec();
    }

    /**
     *
     * @param containerIds 要删除的容器id
     */
    public void cleanContainerBatch(List<String> containerIds) {
        try {
            // 获取Runtime对象
            Runtime runtime = Runtime.getRuntime();

            // 批处理，删除所有容器
            Process process = runtime.exec(String.format("docker rm -f %s", String.join(" ", containerIds)));

            // 等待命令执行完成
            int exitCode = process.waitFor();
            if(exitCode!=0){
                log.error("删除容器发生意外，请手动删除，exitCode："+exitCode);
            }else{
                log.info("Exit Code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取占用内存的情况
     * @param containerId
     * @return
     */
    public long getContainerMemoryUsage(String containerId) {
        StatsCmd statsCmd = DOCKER_CLIENT.statsCmd(containerId);
        AtomicReference<Long> usedMemory = new AtomicReference<>(0L);

        try (ResultCallback.Adapter<Statistics> resultCallback = new ResultCallback.Adapter<Statistics>() {
            @Override
            public void onNext(Statistics stats) {
                MemoryStatsConfig memoryStats = stats.getMemoryStats();
                usedMemory.set(memoryStats.getUsage());
                super.onNext(stats);
            }
        }) {
            statsCmd.exec(resultCallback).awaitCompletion();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("获取内存使用情况失败: " + e.getMessage(), e);
        }

        return usedMemory.get();
    }

}
