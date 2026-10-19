package com.cloudrive.filePreviewService.Controller;

import com.cloudrive.filePreviewService.model.vo.PreviewVO;
import com.cloudrive.filePreviewService.service.PreviewService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件预览控制器
 * <p>
 * 根据文件ID生成 OSS 预签名 URL，供前端实现在线预览。
 * 仅允许文件所有者访问，非所有者统一返回无权限错误。
 * 接口契约与原单体 {@code PreviewController} 保持一致：
 * {@code GET /api/files/preview/{fileId}}，限流 用户+IP 2 次/秒。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/files/preview")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    /**
     * 获取文件预览信息
     * <p>
     * 校验当前用户是否为文件所有者，生成 5 分钟 OSS 预签名 URL，
     * 同时返回预览分类（image/video/audio/pdf/text），前端根据分类选择对应渲染组件。
     * </p>
     *
     * @param fileId 文件ID
     * @return 预览信息（URL + 分类 + MIME + 文件名 + 大小）
     */
    @GetMapping("/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<PreviewVO> preview(@PathVariable Long fileId) {
        return Result.success(previewService.preview(fileId));
    }
}
