package com.vox.voxojsandbox;

import cn.hutool.core.io.resource.ResourceUtil;

import com.vox.voxojsandbox.core.DockerSandBoxACM;
import com.vox.voxojsandbox.core.DockerSandboxTemplate;
import com.vox.voxojsandbox.modal.ExecuteRequest;
import com.vox.voxojsandbox.modal.ExecuteResponse;
import com.vox.voxojsandbox.modal.LanguageCmdEnum;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@SpringBootTest
class DockerSandboxTemplateOldTest {

    @Resource
    private DockerSandboxTemplate dockerSandboxTemplate;
    @Resource
    private DockerSandBoxACM DockerSandBoxACM;

    @Test
    void testJava() throws InterruptedException {
        String code = ResourceUtil.readStr("languageCode/Main.java", StandardCharsets.UTF_8);
        ExecuteRequest executeRequest = new ExecuteRequest();
        ArrayList<String> inputs=new ArrayList<>();
        inputs.add("1 2");
        inputs.add("2 3");
        executeRequest.setInputList(inputs);
        executeRequest.setCode(code);
        executeRequest.setLanguage(LanguageCmdEnum.JAVA.getLanguage());
//        ExecuteResponse execute = dockerSandbox.execute(executeRequest);
        ExecuteResponse execute= dockerSandboxTemplate.execute(executeRequest);
        System.out.println(execute);
        //防止主线程死亡影响测试
//        Thread.sleep(5000);
    }
    @Test
    void testACM() throws InterruptedException {
        String code = ResourceUtil.readStr("languageCode/acm.java", StandardCharsets.UTF_8);
        ExecuteRequest executeRequest = new ExecuteRequest();
        ArrayList<String> inputs=new ArrayList<>();
        inputs.add("1 2");
        inputs.add("2 3");
        executeRequest.setInputList(inputs);
        executeRequest.setCode(code);
        executeRequest.setLanguage(LanguageCmdEnum.JAVA.getLanguage());
//        ExecuteResponse execute = dockerSandbox.execute(executeRequest);
        ExecuteResponse execute= DockerSandBoxACM.execute(executeRequest);
        System.out.println(execute);
        //防止主线程死亡影响测试
//        Thread.sleep(5000);
    }



    @Test
    void test(){
         Integer corePoolSize = Runtime.getRuntime().availableProcessors() * 10;

         Integer maximumPoolSize = Runtime.getRuntime().availableProcessors() * 20;

        System.out.println("corePoolSize:"+corePoolSize);
    }
}
