package com.vox.voxojsandbox.modal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExecuteMessage {
    private boolean success;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;

}

