package com.vox.voxojsandbox.core;

import cn.hutool.core.io.FileUtil;
import com.vox.voxojsandbox.dao.DockerDao;
import com.vox.voxojsandbox.modal.ContainerInfo;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;


import java.util.List;



/**
 * 监听容器关闭事件，清理容器以及残余文件
 */
@Component
@Slf4j
public class CleanContainerListener implements ApplicationListener<ContextClosedEvent> {

    @Resource
    private DockerDao dockerDao;

    @Resource
    private ContainerPoolExecutor containerPoolExecutor;

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // 批量清理容器
        List<String> containerIds = containerPoolExecutor.getContainerPool().stream().map(ContainerInfo::getContainerId).toList();
        dockerDao.cleanContainerBatch(containerIds);
    }

}

