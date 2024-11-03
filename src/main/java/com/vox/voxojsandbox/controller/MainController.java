package com.vox.voxojsandbox.controller;

import com.vox.voxojsandbox.core.DockerSandBoxACM;
import com.vox.voxojsandbox.core.DockerSandboxTemplate;
import com.vox.voxojsandbox.modal.ExecuteRequest;
import com.vox.voxojsandbox.modal.ExecuteResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    @Resource
    private DockerSandboxTemplate dockerSandboxTemplate;

    @Resource
    private DockerSandBoxACM dockerSandBoxACM;

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @PostMapping("/executeCode")
    ExecuteResponse executeCode(@RequestBody ExecuteRequest executeRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String header = httpServletRequest.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(header)) {
            httpServletResponse.setStatus(403);
            return null;
        }
        if (executeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
//        return dockerSandboxTemplate.execute(executeRequest);
        return dockerSandBoxACM.execute(executeRequest);
    }
}
