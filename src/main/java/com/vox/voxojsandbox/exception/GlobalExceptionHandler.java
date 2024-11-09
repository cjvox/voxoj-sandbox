package com.vox.voxojsandbox.exception;


import com.vox.voxojsandbox.modal.ExecuteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author voxcode
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ExecuteResponse runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ExecuteResponse.builder()
                .message("判题失败")
                .build();
    }
}
