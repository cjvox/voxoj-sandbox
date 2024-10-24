package com.vox.voxojsandbox.modal;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteRequest {
    private List<String> inputList;
    private String language;
    private String code;
}

