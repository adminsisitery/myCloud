package com.cloudrive.fileSearchService.Controller;

import com.cloudrive.fileSearchService.handler.FileSearchHandler;
import com.cloudrive.fileSearchService.model.vo.FileListVO;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件搜索控制器
 * <p>
 * 接口契约与原单体 {@code GET /api/files/search} 保持一致：
 * 返回结构与文件列表相同，限流 5 次/秒（用户维度）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@RestController
@RequestMapping("/api/files")
public class SearchController {

    private final FileSearchHandler fileSearchHandler;

    public SearchController(FileSearchHandler fileSearchHandler) {
        this.fileSearchHandler = fileSearchHandler;
    }

    /**
     * 按关键词搜索当前用户的文件
     * <p>
     * 策略由 file.search 配置决定：Mysql（默认，数据库 LIKE）或 ES（Elasticsearch 索引）。
     * </p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件列表
     */
    @GetMapping("/search")
    @RateLimit(dimensions = {RateLimit.Dimension.USER},
            permitsPerSecond = 5.0, timeout = 1000)
    public Result<List<FileListVO>> searchFiles(@RequestParam("keyword") String keyword) {
        return Result.success(fileSearchHandler.searchFiles(keyword));
    }
}
