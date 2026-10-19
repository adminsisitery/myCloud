package com.cloudrive.fileManageService.Controller;

import com.cloudrive.fileManageService.service.FileManageService;
import com.cloudrive.pan_common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最近访问记录内部接口
 * <p>
 * 供 file-download-service 在开始下载前回调，写入 t_recent_access。
 * 与原单体「下载即记录最近访问」的行为保持一致；仅限内网调用，未做服务间鉴权
 * （与 user-service /internal/space/* 现状一致）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/internal/recent")
public class InternalRecentController {

    private final FileManageService fileManageService;

    public InternalRecentController(FileManageService fileManageService) {
        this.fileManageService = fileManageService;
    }

    /**
     * 记录一次文件访问
     *
     * @param userId 用户 ID
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @PostMapping("/record")
    public Result<String> recordAccess(@RequestParam("userId") Long userId,
                                       @RequestParam("fileId") Long fileId) {
        fileManageService.recordAccess(userId, fileId);
        return Result.success("记录成功");
    }
}
