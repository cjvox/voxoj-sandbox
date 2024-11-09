package com.vox.voxojsandbox.modal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.vox.voxojsandbox.constant.Mock.*;

/**
 * @author voxcode
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteRequest {

    @Schema(example = INPUT)
    private List<String> inputList;

    @Schema(example = LANGUAGE)
    private String language;

    @Schema(example = CODE)
    private String code;
}

