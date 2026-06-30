package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.service.DeleteFileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 回收站 & 批量删除控制器
 * <p>提供文件软删除（移入回收站）、批量删除、彻底删除、恢复等接口。</p>
 *
 * @author 飞飞
 * @date 2026/06/29
 */
@RestController
@RequestMapping("/api/files")
public class deleteFileController {

    private final DeleteFileService deleteFileService;

    public deleteFileController(DeleteFileService deleteFileService) {
        this.deleteFileService = deleteFileService;
    }

    /**
     * 批量删除文件（移入回收站）
     *
     * @param ids 文件ID列表
     * @return 操作结果
     */
    @DeleteMapping("/batch")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<?> batchDelete(@RequestBody List<Long> ids) {
        deleteFileService.batchDeleteFile(ids);
        return Result.success();
    }
}
