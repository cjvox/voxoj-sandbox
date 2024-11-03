package com.vox.voxojsandbox;

import cn.hutool.core.io.resource.ResourceUtil;

import com.vox.voxojsandbox.core.DockerSandboxTemplate;
import com.vox.voxojsandbox.modal.ExecuteRequest;
import com.vox.voxojsandbox.modal.ExecuteResponse;
import com.vox.voxojsandbox.modal.LanguageCmdEnum;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


class DockerSandboxTemplateTest {

    private static final DockerSandboxTemplate DOCKER_SANDBOX_TEMPLATE = new DockerSandboxTemplate();
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
        ExecuteResponse execute = DOCKER_SANDBOX_TEMPLATE.execute(executeRequest);
        System.out.println(execute);
        //防止主线程死亡影响测试
//        Thread.sleep(5000);
    }


}
