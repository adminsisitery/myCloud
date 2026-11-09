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
 * 仅限内网调用：pan-common 的 {@code InternalAuthWebConfig} 已对 {@code /internal/**}
 * 统一挂了服务间鉴权（X-Internal-Token + 时间戳），调用方由 Feign 拦截器自动注入。
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
