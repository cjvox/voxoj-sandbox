package com.vox.voxojsandbox.controller;

import com.vox.voxojsandbox.core.DockerSandBoxACM;
import com.vox.voxojsandbox.core.DockerSandboxTemplate;
import com.vox.voxojsandbox.modal.ExecuteRequest;
import com.vox.voxojsandbox.modal.ExecuteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author voxcode
 * @date 2024/11/9 23:23
 */
@RestController
@Slf4j
public class MainController {

    @Resource
    private DockerSandboxTemplate dockerSandboxTemplate;

    @Resource
    private DockerSandBoxACM dockerSandBoxACM;

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @PostMapping("/executeCode")
    @Operation(
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExecuteRequest.class)
                    )
            )
    )
    ExecuteResponse executeCode(@RequestBody ExecuteRequest executeRequest, @RequestHeader(value = AUTH_REQUEST_HEADER,defaultValue = "")String header, HttpServletResponse httpServletResponse) {
        if (!AUTH_REQUEST_SECRET.equals(header)) {
            httpServletResponse.setStatus(403);
            return null;
        }
        if (executeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        log.info("请求代码为：{}",executeRequest.getCode());

        return dockerSandBoxACM.execute(executeRequest);
    }
}
