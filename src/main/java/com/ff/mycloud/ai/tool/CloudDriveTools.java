package com.ff.mycloud.ai.tool;

import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.UserCenterMapperService;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CloudDriveTools {

    private static final Logger log = LoggerFactory.getLogger(CloudDriveTools.class);
    private static final int RECENT_LIMIT = 10;
    private static final int MAX_CALLS_PER_TOOL = 3;

    private final FileInfoMapperService fileInfoService;
    private final FileSearchHandler fileSearchHandler;
    private final UserCenterMapperService userCenterService;
    private final FileRecentAccess fileRecentAccess;
    private final Long userId;

    /** 每个工具方法的调用计数，防止 DeepSeek 无限循环调用 */
    private final Map<String, AtomicInteger> callCount = new ConcurrentHashMap<>();

    public CloudDriveTools(FileInfoMapperService fileInfoService,
                           FileSearchHandler fileSearchHandler,
                           UserCenterMapperService userCenterService,
                           FileRecentAccess fileRecentAccess,
                           Long userId) {
        this.fileInfoService = fileInfoService;
        this.fileSearchHandler = fileSearchHandler;
        this.userCenterService = userCenterService;
        this.fileRecentAccess = fileRecentAccess;
        this.userId = userId;
    }

    /** 检查调用次数，超过限制返回提示 */
    private boolean checkLimit(String toolName) {
        int count = callCount.computeIfAbsent(toolName, k -> new AtomicInteger()).incrementAndGet();
        if (count > MAX_CALLS_PER_TOOL) {
            log.warn("[Tool] {} 调用次数已达上限({})，拒绝执行", toolName, MAX_CALLS_PER_TOOL);
            return false;
        }
        return true;
    }

    // ==================== 目录浏览 ====================

    @Tool("查看当前用户网盘根目录下所有文件和文件夹")
    public String listAllFiles() {
        if (!checkLimit("listAllFiles")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        return listDirectory(0L);
    }

    @Tool("查看指定文件夹下的文件和子文件夹，parentId 为文件夹ID")
    public String listDirectory(Long parentId) {
        if (!checkLimit("listDirectory")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] listDirectory userId={}, parentId={}", userId, parentId);

        List<FileInfo> files = fileInfoService.findByParentIdAndUserId(userId, parentId);

        String dirName;
        if (parentId == null || parentId == 0L) {
            dirName = "根目录";
        } else {
            FileInfo parent = fileInfoService.getById(parentId);
            dirName = parent != null ? parent.getFilename() : "文件夹";
        }

        if (files == null || files.isEmpty()) {
            return "**" + dirName + "** 为空";
        }

        List<FileInfo> folders = files.stream()
                .filter(FileInfo::getIsFolder)
                .sorted(Comparator.comparing(FileInfo::getFilename, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        List<FileInfo> fileItems = files.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsFolder()))
                .sorted(Comparator.comparing(FileInfo::getFilename, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        long totalSize = fileItems.stream()
                .filter(f -> f.getFileSize() != null)
                .mapToLong(FileInfo::getFileSize)
                .sum();

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(dirName).append("** — ");
        sb.append(folders.size()).append("个文件夹, ").append(fileItems.size()).append("个文件");
        if (totalSize > 0) sb.append(", ").append(formatSize(totalSize));
        sb.append("\n");

        for (FileInfo f : folders) {
            sb.append("- 📁 **").append(f.getFilename()).append("**\n");
        }
        for (FileInfo f : fileItems) {
            String name = f.getFileSuffix() != null
                    ? f.getFilename() + "." + f.getFileSuffix()
                    : f.getFilename();
            sb.append("- 📄 **").append(name).append("**");
            if (f.getFileSize() != null && f.getFileSize() > 0)
                sb.append(" *").append(formatSize(f.getFileSize())).append("*");
            sb.append("\n");
        }

        return sb.toString();
    }

    // ==================== 文件搜索 ====================

    @Tool("按照文件名模糊搜索文件")
    public String findFile(String keyWord) {
        if (!checkLimit("findFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] findFile userId={}, keyWord={}", userId, keyWord);

        List<FileListVO> files = fileSearchHandler.searchFiles(keyWord, userId);

        if (files == null || files.isEmpty()) {
            return "未找到包含 **" + keyWord + "** 的文件";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("搜索 **").append(keyWord).append("** — ").append(files.size()).append("个结果\n");

        for (FileListVO f : files) {
            String name = f.getFileSuffix() != null
                    ? f.getFilename() + "." + f.getFileSuffix()
                    : f.getFilename();
            sb.append("- 📄 **").append(name).append("**");
            if (f.getFileSize() != null && f.getFileSize() > 0)
                sb.append(" *").append(formatSize(f.getFileSize())).append("*");
            sb.append("\n");
        }

        return sb.toString();
    }

    // ==================== 存储空间 ====================

    @Tool("查看当前用户的网盘存储空间使用情况")
    public String getStorageInfo() {
        if (!checkLimit("getStorageInfo")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] getStorageInfo userId={}", userId);

        UserCenter uc = userCenterService.getByUserId(userId);
        if (uc == null) {
            return "存储信息不可用";
        }

        long used = uc.getUsedSpace();
        long total = uc.getTotalSpace();
        long free = total - used;
        int pct = total > 0 ? (int) (used * 100 / total) : 0;

        return "**存储空间**\n"
                + "- 已用: " + formatSize(used) + "\n"
                + "- 剩余: " + formatSize(free) + "\n"
                + "- 总容量: " + formatSize(total) + "\n"
                + "- 使用率: " + pct + "%";
    }

    // ==================== 收藏列表 ====================

    @Tool("查看当前用户收藏的文件列表")
    public String listStarredFiles() {
        if (!checkLimit("listStarredFiles")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] listStarredFiles userId={}", userId);

        List<FileInfo> starred = fileInfoService.findStarredFiles(userId);

        if (starred == null || starred.isEmpty()) {
            return "暂无收藏文件";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**收藏文件** — ").append(starred.size()).append("个\n");

        for (FileInfo f : starred) {
            String name = f.getFileSuffix() != null
                    ? f.getFilename() + "." + f.getFileSuffix()
                    : f.getFilename();
            sb.append("- ⭐ **").append(name).append("**");
            if (f.getFileSize() != null && f.getFileSize() > 0)
                sb.append(" *").append(formatSize(f.getFileSize())).append("*");
            sb.append("\n");
        }

        return sb.toString();
    }

    // ==================== 最近访问 ====================

    @Tool("查看当前用户最近访问的文件列表")
    public String getRecentFiles() {
        if (!checkLimit("getRecentFiles")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] getRecentFiles userId={}", userId);

        List<FileInfo> recent = fileRecentAccess.getRecentFiles(userId, RECENT_LIMIT);

        if (recent == null || recent.isEmpty()) {
            return "暂无最近访问记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**最近访问** — ").append(recent.size()).append("个\n");

        for (FileInfo f : recent) {
            String name = f.getFileSuffix() != null
                    ? f.getFilename() + "." + f.getFileSuffix()
                    : f.getFilename();
            sb.append("- 📄 **").append(name).append("**");
            if (f.getFileSize() != null && f.getFileSize() > 0)
                sb.append(" *").append(formatSize(f.getFileSize())).append("*");
            sb.append("\n");
        }

        return sb.toString();
    }

    // ==================== 回收站 ====================

    @Tool("查看当前用户回收站中的文件列表")
    public String getTrashList() {
        if (!checkLimit("getTrashList")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] getTrashList userId={}", userId);

        List<FileInfo> trash = fileInfoService.getTrashFile(userId);

        if (trash == null || trash.isEmpty()) {
            return "回收站为空";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**回收站** — ").append(trash.size()).append("个\n");

        for (FileInfo f : trash) {
            String name = f.getFileSuffix() != null
                    ? f.getFilename() + "." + f.getFileSuffix()
                    : f.getFilename();
            sb.append("- 🗑️ **").append(name).append("**");
            if (f.getFileSize() != null && f.getFileSize() > 0)
                sb.append(" *").append(formatSize(f.getFileSize())).append("*");
            sb.append("\n");
        }

        return sb.toString();
    }


    // ==================== 获取当前时间 ====================
    @Tool("获取当前时间")
    public String getNowDate(){
        return LocalDateTime.now().toString();
    }


    // ==================== 工具方法 ====================

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
