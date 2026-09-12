package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.dto.CreateFolderDTO;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.FolderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 文件夹管理控制器
 * <p>
 * 提供文件夹的创建和下载（打包为 ZIP）功能。
 * 新建文件夹时会对文件名进行合法性校验，下载文件夹时自动压缩打包。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/10 22:03
 * @version 1.0
 */

@RequestMapping("/api/file")
@RestController
public class FolderController {

    FolderService folderService;

    /**
     * 构造器注入文件夹服务
     *
     * @param folderService 文件夹服务
     */
    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * 新建文件夹
     * <p>
     * 在指定父目录下创建一个新文件夹。限流：每个用户+IP 每秒最多 1 次请求。
     * </p>
     *
     * @param dto 包含 parentId 和 folderName 的创建 DTO
     * @return 操作结果
     */
    @PostMapping("/folder")
    @RateLimit(dimensions = { RateLimit.Dimension.USER, RateLimit.Dimension.IP }, permitsPerSecond = 1.0, timeout = 1000)
    public Result<String> makeFolder(@RequestBody CreateFolderDTO dto) {

        return  folderService.createFolder(dto.getParentId(),dto.getFolderName());
    }

    /**
     * 下载文件夹（打包为 ZIP）
     * <p>
     * 将指定文件夹及其子文件压缩为 ZIP 包，通过 HttpServletResponse 流式输出给客户端。
     * </p>
     *
     * @param fileId   文件夹ID
     * @param response HTTP 响应对象，用于直接写入 ZIP 流
     * @return null（响应已通过 response 直接写出）
     */
    @GetMapping("/{fileId}/content")
    @RateLimit(dimensions = { RateLimit.Dimension.USER, RateLimit.Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public void downloadFile(@PathVariable long fileId, HttpServletResponse response) {
        folderService.downLoadFolder(fileId,response);
    }
}
