package com.ff.mycloud.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/13 18:07
 * @version 1.0
 */

@Builder
@Data
public class FileCheckVO {
    private Boolean exists;
}
