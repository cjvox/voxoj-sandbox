package com.vox.voxojsandbox.modal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteResponse {
    private List<String> outputList;

    /**
     * 执行信息信息
     */
    private String message;

    private Long time;

    private Long memory;

    private String LastInput;

}