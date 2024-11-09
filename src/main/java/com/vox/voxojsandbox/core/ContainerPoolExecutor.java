package com.vox.voxojsandbox.core;

import cn.hutool.core.io.FileUtil;
import com.vox.voxojsandbox.dao.DockerDao;
import com.vox.voxojsandbox.modal.ContainerInfo;

import com.vox.voxojsandbox.modal.ExecuteResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author voxcode
 * @date 2024/11/9 23:23
 */
@Slf4j
@Configuration
@Data
@ConfigurationProperties(prefix = "codesandbox.pool")
public class ContainerPoolExecutor {

    private Integer corePoolSize = Runtime.getRuntime().availableProcessors() * 10;

    private Integer maximumPoolSize = Runtime.getRuntime().availableProcessors() * 20;

//    private Integer waitQueueSize = 200;

    private Integer keepAliveTime = 5;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    private long getContainTimeout = 5;


    /**
     * 容器池
     */
    private BlockingQueue<ContainerInfo> containerPool;

//    /**
//     * 容器使用排队计数
//     */
//    private AtomicInteger blockingThreadCount;

    /**
     * 可扩展的数量
     */
    private AtomicInteger expandCount;

    @Resource
    private DockerDao dockerDao;


    @PostConstruct
    public void initPool() {

        // 初始化容器池
        // 按照lastTime降序，返回time最小的
//        this.containerPool = new PriorityBlockingQueue<>(maximumPoolSize, Comparator.comparingLong(ContainerInfo::getLastActivityTime));
        this.containerPool = new LinkedBlockingQueue<>(maximumPoolSize);
//        this.blockingThreadCount = new AtomicInteger(0);
        this.expandCount = new AtomicInteger(maximumPoolSize - corePoolSize);

        // 初始化池中的数据
        for (int i = 0; i < corePoolSize; i++) {
            createNewPool();
        }

        // 定时清理过期的容器
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduleExpirationCleanup(scheduledExecutorService);
    }

    //调用dao创建容器
    private void createNewPool() {
        // 写入文件
        String userDir = System.getProperty("user.dir");
        String codePathName = userDir + File.separator + "tempCode";

        // 把用户的代码隔离存放
        UUID uuid = UUID.randomUUID();
        codePathName += File.separator + uuid;

        // 判断代码目录是否存在，没有则新建
        File codePath = new File(codePathName);
        if (!codePath.exists()) {
            boolean mkdir = codePath.mkdirs();
            if (!mkdir) {
                log.info("创建代码目录失败");
            }
        }
        ContainerInfo containerInfo = dockerDao.startContainer(codePathName);
        boolean result = containerPool.offer(containerInfo);
        if (!result) {
            log.error("current capacity: {}, the capacity limit is exceeded...", containerPool.size());
        }
    }

    private boolean expandPool() {
        log.info("超过指定数量，触发扩容");
        if (expandCount.decrementAndGet() < 0) {
            log.error("不能再扩容了");
            return false;
        }
        log.info("扩容了");
        createNewPool();
        return true;
    }


    private ContainerInfo getContainer() throws InterruptedException {
        if (containerPool.isEmpty()) {
            // 尝试扩容
            if (!expandPool()) {
                log.error("扩容失败");
                return null;
            }
            // 阻塞等待可用的数据
            return containerPool.poll(getContainTimeout,timeUnit);
        }
        return containerPool.poll(getContainTimeout,timeUnit);
    }

    /**
     * 清理过期容器
     */
    private void cleanExpiredContainers() {
        long currentTime = System.currentTimeMillis();
        int needCleanCount = containerPool.size() - corePoolSize;
        if (needCleanCount <= 0) {
            return;
        }
        // 处理过期的容器
        containerPool.stream().limit(needCleanCount).filter(containerInfo -> {
            long lastActivityTime = containerInfo.getLastActivityTime();
            lastActivityTime += timeUnit.toMillis(keepAliveTime);
            return lastActivityTime < currentTime;
        }).forEach(containerInfo -> {
            boolean remove = containerPool.remove(containerInfo);
            if (remove) {
                String containerId = containerInfo.getContainerId();
                expandCount.incrementAndGet();
                if (StringUtils.isNotBlank(containerId)) {
                    dockerDao.cleanContainer(containerId);
                }
            }
        });
        log.info("当前容器大小: " + containerPool.size());
    }

    private void scheduleExpirationCleanup(ScheduledExecutorService scheduledExecutorService) {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            log.info("定时清理过期容器...");
            cleanExpiredContainers();
            // 每隔 20 秒执行一次清理操作
        }, 0, 20, TimeUnit.SECONDS);
    }


    private void recordError(ContainerInfo containerInfo) {
        if (containerInfo != null) {
            containerInfo.setErrorCount(containerInfo.getErrorCount() + 1);
        }
    }


    public ExecuteResponse run(Function<ContainerInfo, ExecuteResponse> function) {
        ContainerInfo containerInfo = null;
        try {
            containerInfo = getContainer();
            if (containerInfo == null) {
                return ExecuteResponse.builder().message("提交次数过多，成为plus用户解锁更多").build();
            }
            log.info("有数据，拿到了: {}", containerInfo);
            ExecuteResponse executeResponse = function.apply(containerInfo);
            if (StringUtils.isNotBlank(executeResponse.getMessage())) {
                recordError(containerInfo);
            }
            return executeResponse;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (containerInfo != null) {
                ContainerInfo finalContainerInfo = containerInfo;
                //删除缓存的代码文件
                String containerId = containerInfo.getContainerId();
                CompletableFuture.runAsync(() -> {
                    dockerDao.execCmd(containerId, new String[]{"rm", "-rf", "/box"});
                    try {
                        // 更新时间
                        log.info("操作完了，还回去");
                        String codePathName = finalContainerInfo.getCodePathName();
                        FileUtil.del(codePathName);
                        // 错误超过 3 次就不放回，重新运行一个
                        if (finalContainerInfo.getErrorCount() > 3) {
                            CompletableFuture.runAsync(() -> {
                                dockerDao.cleanContainer(finalContainerInfo.getContainerId());
                                this.createNewPool();
                            });
                            return;
                        }
                        finalContainerInfo.setLastActivityTime(System.currentTimeMillis());
                        containerPool.put(finalContainerInfo);
                        log.info("容器池还剩: {}", containerPool.size());
                    } catch (InterruptedException e) {
                        log.error("无法放入");
                    }
                });
            }
        }
    }

    /**
     * 清楚容器池中的容器以及代码文件
     */
    @PreDestroy
    public void onApplicationEvent() {
        // 批量清理容器以及残余文件
        List<String> containerIds = this.getContainerPool().stream()
                .peek(containerInfo -> FileUtil.del(containerInfo.getCodePathName())) // 执行删除操作
                .map(ContainerInfo::getContainerId) // 获取容器 ID
                .collect(Collectors.toList()); //
        dockerDao.cleanContainerBatch(containerIds);
    }
}

