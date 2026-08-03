package com.ff.mycloud.ai.tool;

import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.ai.model.entity.QueryData;
import com.ff.mycloud.ai.service.SearchChatAssistant;
import com.ff.mycloud.common.util.FileTypeUtil;
import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.UserCenterMapperService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 云盘工具集 —— 供 AI Agent 调用的网盘操作工具。
 * <p>
 * <b>关键设计：所有操作直接使用构造函数传入的 {@code userId}，
 * 不依赖 {@code UserContext.getCurrentUserId()}（SaToken ThreadLocal）。
 * 因为 LC4j 在 OkHttp IO 线程上执行工具调用，ThreadLocal 上下文会丢失。</b>
 * </p>
 */
public class CloudDriveTools {

    private static final Logger log = LoggerFactory.getLogger(CloudDriveTools.class);
    private static final int RECENT_LIMIT = 10;
    private static final int MAX_CALLS_PER_TOOL = 3;

    private final FileInfoMapperService fileInfoService;
    private final FileSearchHandler fileSearchHandler;
    private final UserCenterMapperService userCenterService;
    private final FileRecentAccess fileRecentAccess;
    private final FileMapper fileMapper;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final FileSearchRepository fileSearchRepository;
    private final Long userId;
    SearchChatAssistant searchChatAssistant;

    /** 每个工具方法的调用计数，防止 DeepSeek 无限循环调用 */
    private final Map<String, AtomicInteger> callCount = new ConcurrentHashMap<>();

    public CloudDriveTools(FileInfoMapperService fileInfoService,
                           FileSearchHandler fileSearchHandler,
                           UserCenterMapperService userCenterService,
                           FileRecentAccess fileRecentAccess,
                           FileMapper fileMapper,
                           EmbeddingStore<TextSegment> embeddingStore,
                           EmbeddingModel embeddingModel,
                           FileSearchRepository fileSearchRepository,
                           Long userId,
                           SearchChatAssistant searchChatAssistant) {
        this.fileInfoService = fileInfoService;
        this.fileSearchHandler = fileSearchHandler;
        this.userCenterService = userCenterService;
        this.fileRecentAccess = fileRecentAccess;
        this.fileMapper = fileMapper;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.fileSearchRepository = fileSearchRepository;
        this.userId = userId;
        this.searchChatAssistant = searchChatAssistant;
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

    /** 包含时间/大小/类型描述词的 pattern，命中才走 LLM 解析 */
    private static final Pattern COMPLEX_QUERY = Pattern.compile(
            "上周|本周|下周|今天|昨天|明天|前天|后天|上个月|这个月|下个月|今年|去年|" +
            "星期|月份|号|日|天前|天前|天内|小时|分钟|" +
            "MB|GB|KB|M|G|K|兆|字节|大于|小于|超过|不低于|不超过|以上|以下|之间|" +
            "图片|视频|文档|表格|PDF|Word|Excel|PPT|合同|音乐|压缩|" +
            "jpg|png|gif|mp4|mkv|mp3|doc|docx|xls|xlsx|ppt|pptx|pdf|zip|rar", Pattern.CASE_INSENSITIVE);

    @Tool("搜索文件，支持自然语言（如'上周上传的合同'、'大于100MB的视频'）")
    public String findFile(String keyWord) {
        if (!checkLimit("findFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] findFile userId={}, keyWord={}", userId, keyWord);

        // 纯关键词（如 "report"）直接搜，跳过 LLM 节省 ~500ms + token
        QueryData qd;
        if (COMPLEX_QUERY.matcher(keyWord).find()) {
            qd = searchChatAssistant.chat(keyWord);
        } else {
            qd = new QueryData();
            qd.setFileName(keyWord);
        }

        List<FileListVO> files = fileSearchHandler.searchFiles(qd, userId);

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


    // ==================== 文件操作（全部绕过 UserContext，直接用 userId 字段） ====================

    @Tool("必须在网盘创建文件夹时调用此工具。parentId 传 0 表示根目录，folderName 为文件夹名称。必须执行，禁止口头模拟")
    public String createFolder(Long parentId, String folderName) {
        if (!checkLimit("createFolder")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] createFolder userId={}, parentId={}, folderName={}", userId, parentId, folderName);

        // 1. 空值校验
        if (folderName == null || folderName.isBlank()) return "❌ 文件夹名称不能为空";
        String name = folderName.trim();

        // 2. 长度限制
        if (name.length() > 30) return "❌ 文件夹名称过长（最多30字符）";

        // 3. 非法字符检查
        if (name.matches(".*[\\\\/:*?\"<>|].*")) return "❌ 文件夹名称包含非法字符";

        // 4. 重名检查
        Long pid = parentId != null ? parentId : 0L;
        if (fileInfoService.existsFolder(pid, name, userId))
            return "❌ 同级已存在同名文件夹 **" + name + "**";

        // 5. 计算 localFilePath
        String localFilePath = "";
        if (pid != 0L) {
            FileInfo parent = fileInfoService.getById(pid);
            if (parent != null) {
                localFilePath = parent.getLocalFilePath();
            }
        }
        localFilePath = localFilePath + name + "/";

        // 6. 创建并保存
        FileInfo folder = fileMapper.toFileInfoFromFolder(name, true, false, pid, userId, localFilePath);
        folder.setCategoryId(FileTypeUtil.getCategoryId(null));
        fileInfoService.save(folder);

        log.info("[Tool] createFolder 成功: folderId={}, name={}, parentId={}", folder.getId(), name, pid);
        return "✅ 文件夹 **" + name + "** 创建成功";
    }

    @Tool("重命名文件，currentName 为当前文件名（可含扩展名），newName 为新文件名（不含扩展名）")
    public String renameFile(String currentName, String newName) {
        if (!checkLimit("renameFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] renameFile userId={}, currentName={}, newName={}", userId, currentName, newName);

        if (newName == null || newName.isBlank()) return "❌ 新文件名不能为空";
        FileInfo file = findFileByName(currentName);
        if (file == null) return "❌ 未找到文件：**" + currentName + "**，请先 listDirectory 确认文件存在";

        // 去除文件名中的后缀扩展名
        String cleanName = newName.trim();
        int dotIndex = cleanName.lastIndexOf(".");
        if (dotIndex != -1) {
            cleanName = cleanName.substring(0, dotIndex);
        }

        file.setFilename(cleanName);
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoService.updateById(file);

        log.info("[Tool] renameFile 成功: fileId={}, {} -> {}", file.getId(), currentName, cleanName);
        return "✅ 已重命名为 **" + cleanName + "**";
    }

    @Tool("删除文件或文件夹（移到回收站），fileName 为文件名或文件夹名")
    public String deleteFile(String fileName) {
        if (!checkLimit("deleteFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] deleteFile userId={}, fileName={}", userId, fileName);

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**，请先 listDirectory 确认文件存在";

        file.setIsDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoService.updateById(file);

        log.info("[Tool] deleteFile 成功: fileId={}, name={}", file.getId(), fileName);
        return "✅ **" + fileName + "** 已移到回收站";
    }

    @Tool("移动文件到指定文件夹，fileName 为文件名，targetFolderName 为目标文件夹名（根目录传'根目录'）")
    public String moveFile(String fileName, String targetFolderName) {
        if (!checkLimit("moveFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] moveFile userId={}, fileName={}, target={}", userId, fileName, targetFolderName);

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**";

        Long targetId = 0L;
        if (targetFolderName != null && !targetFolderName.isBlank()
                && !"根目录".equals(targetFolderName.trim())) {
            FileInfo target = findFolderByName(targetFolderName.trim());
            if (target == null) return "❌ 未找到目标文件夹：**" + targetFolderName + "**";
            targetId = target.getId();
        }

        file.setParentId(targetId);
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoService.updateById(file);

        String dest = targetId == 0L ? "根目录" : targetFolderName;
        log.info("[Tool] moveFile 成功: fileId={}, {} -> {}", file.getId(), fileName, dest);
        return "✅ **" + fileName + "** 已移动到 **" + dest + "**";
    }

    @Tool("收藏文件，fileName 为文件名")
    public String starFile(String fileName) {
        if (!checkLimit("starFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] starFile userId={}, fileName={}", userId, fileName);

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**";

        file.setIsStarred(true);
        file.setStarredAt(LocalDateTime.now());
        fileInfoService.updateById(file);

        log.info("[Tool] starFile 成功: fileId={}, name={}", file.getId(), fileName);
        return "✅ **" + fileName + "** 已收藏";
    }

    @Tool("取消收藏文件，fileName 为文件名")
    public String unstarFile(String fileName) {
        if (!checkLimit("unstarFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] unstarFile userId={}, fileName={}", userId, fileName);

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**";

        file.setIsStarred(false);
        file.setStarredAt(null);
        fileInfoService.updateById(file);

        log.info("[Tool] unstarFile 成功: fileId={}, name={}", file.getId(), fileName);
        return "✅ **" + fileName + "** 已取消收藏";
    }

    @Tool("从回收站恢复文件，fileName 为文件名")
    public String restoreFile(String fileName) {
        if (!checkLimit("restoreFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] restoreFile userId={}, fileName={}", userId, fileName);

        List<FileInfo> trash = fileInfoService.getTrashFile(userId);
        FileInfo file = null;
        if (trash != null) {
            String lower = fileName.trim().toLowerCase();
            file = trash.stream()
                .filter(f -> f.getFilename() != null && f.getFilename().toLowerCase().contains(lower))
                .findFirst().orElse(null);
        }
        if (file == null) return "❌ 回收站中未找到文件：**" + fileName + "**";

        file.setIsDeleted(false);
        file.setDeletedAt(null);
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoService.updateById(file);

        log.info("[Tool] restoreFile 成功: fileId={}, name={}", file.getId(), fileName);
        return "✅ **" + fileName + "** 已从回收站恢复";
    }

    @Tool("清空回收站，永久删除回收站中所有文件，操作不可逆")
    public String cleanTrash() {
        if (!checkLimit("cleanTrash")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] cleanTrash userId={}", userId);

        List<FileInfo> trash = fileInfoService.getTrashFile(userId);
        if (trash == null || trash.isEmpty()) return "✅ 回收站已经是空的";

        int count = trash.size();
        for (FileInfo f : trash) {
            fileInfoService.removeById(f.getId());
        }

        log.info("[Tool] cleanTrash 完成: userId={}, 共删除 {} 条", userId, count);
        return "✅ 回收站已清空，共删除 " + count + " 个文件/文件夹";
    }

    @Tool("复制文件，fileName 为要复制的文件名")
    public String copyFile(String fileName) {
        if (!checkLimit("copyFile")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] copyFile userId={}, fileName={}", userId, fileName);

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**";

        // 生成不重复的副本名（同级目录下去重）
        String copyName = generateCopyName(file);

        FileInfo copy = new FileInfo();
        copy.setFilename(copyName);
        copy.setOriginalFilename(file.getOriginalFilename());
        copy.setPath(file.getPath());
        copy.setFileSize(file.getFileSize());
        copy.setFileType(file.getFileType());
        copy.setSha256Hash(file.getSha256Hash());
        copy.setFileSuffix(file.getFileSuffix());
        copy.setUserId(userId);
        copy.setParentId(file.getParentId());
        copy.setIsFolder(file.getIsFolder());
        copy.setIsDeleted(false);
        copy.setLocalFilePath(file.getLocalFilePath());
        copy.setCategoryId(file.getCategoryId());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        fileInfoService.save(copy);

        String displayName = file.getFileSuffix() != null && !file.getFileSuffix().isBlank()
                ? copyName + "." + file.getFileSuffix() : copyName;
        log.info("[Tool] copyFile 成功: fileId={} -> newId={}, name={}", file.getId(), copy.getId(), copyName);
        return "✅ **" + fileName + "** 已复制为 **" + displayName + "**";
    }

    /** 生成不冲突的副本名：原名(复制) / 原名(复制2) / ... */
    private String generateCopyName(FileInfo file) {
        String base = file.getFilename();
        // 获取同级目录下所有文件
        List<FileInfo> siblings = fileInfoService.findByParentIdAndUserId(userId,
                file.getParentId() != null ? file.getParentId() : 0L);

        // 先试 "xxx(复制)"
        String candidate = base + "(复制)";
        if (!nameExists(candidate, file, siblings)) {
            return candidate;
        }
        // 递增数字：xxx(复制2), xxx(复制3), ...
        for (int i = 2; i <= 99; i++) {
            candidate = base + "(复制" + i + ")";
            if (!nameExists(candidate, file, siblings)) {
                return candidate;
            }
        }
        // 极端情况：用时间戳兜底
        return base + "(复制" + System.currentTimeMillis() % 100000 + ")";
    }

    /** 检查同名文件/文件夹是否已存在于同级目录 */
    private boolean nameExists(String candidate, FileInfo source, List<FileInfo> siblings) {
        if (siblings == null) return false;
        for (FileInfo f : siblings) {
            if (f.getId().equals(source.getId())) continue; // 跳过自己
            if (candidate.equalsIgnoreCase(f.getFilename())
                    && java.util.Objects.equals(f.getIsFolder(), source.getIsFolder())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 文档问答 ====================

    @Tool("读取指定文件的完整文本内容，用于文件总结或深度分析。fileName 为文件名（含扩展名），如'report.pdf'")
    public String readFileContent(String fileName) {
        if (!checkLimit("readFileContent")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] readFileContent userId={}, fileName={}", userId, fileName);

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**，请先 listDirectory 确认文件存在";

        // 1. 优先从 ES 读取完整 content（Tika 解析后的全文）
        if (fileSearchRepository != null) {
            Optional<FileDocument> doc = fileSearchRepository.findById(file.getId());
            if (doc.isPresent() && doc.get().getContent() != null && !doc.get().getContent().isBlank()) {
                String content = doc.get().getContent();
                if (content.length() > 10000) {
                    content = content.substring(0, 10000) + "\n\n...(内容过长，已截断前10000字符)";
                }
                String displayName = getFullName(file);
                return "**【" + displayName + "】全文内容**\n\n" + content;
            }
        }

        // 2. 回退：从 PGVector 拼接该文件的所有文本片段
        if (embeddingStore != null && embeddingModel != null) {
            try {
                Embedding embedded = embeddingModel.embed(fileName).content();
                EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedded)
                        .maxResults(10)
                        .minScore(0.0)
                        .filter(buildFileFilter(file.getId()))
                        .build();
                EmbeddingSearchResult<TextSegment> result = embeddingStore.search(req);
                if (!result.matches().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    String displayName = getFullName(file);
                    sb.append("**【").append(displayName).append("】文本内容**\n\n");
                    for (int i = 0; i < result.matches().size(); i++) {
                        sb.append(result.matches().get(i).embedded().text()).append("\n");
                    }
                    String content = sb.toString();
                    if (content.length() > 10000) {
                        content = content.substring(0, 10000) + "\n\n...(内容过长，已截断)";
                    }
                    return content;
                }
            } catch (Exception e) {
                log.warn("[Tool] readFileContent PGVector fallback failed: {}", e.getMessage());
            }
        }

        return "⚠️ 文件 **" + fileName + "** 的文本内容尚未索引。仅支持文档类型（PDF/Word/TXT/Markdown 等），图片/视频不可读取。";
    }

    @Tool("针对指定文件的内容提问，如合同条款查询、报告数据分析等。fileName 为文件名（含扩展名），question 为你的问题")
    public String askDocument(String fileName, String question) {
        if (!checkLimit("askDocument")) return "已达到调用上限，请直接使用已获取的数据回答用户";
        log.info("[Tool] askDocument userId={}, fileName={}, question={}", userId, fileName, question);

        if (question == null || question.isBlank()) return "❌ 问题不能为空";

        FileInfo file = findFileByName(fileName);
        if (file == null) return "❌ 未找到文件：**" + fileName + "**，请先 listDirectory 确认文件存在";

        if (embeddingStore == null || embeddingModel == null) return "❌ 向量检索服务不可用，无法进行文档问答";

        try {
            Embedding embedded = embeddingModel.embed(question).content();
            EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedded)
                    .maxResults(3)
                    .minScore(0.4)
                    .filter(buildFileFilter(file.getId()))
                    .build();
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(req);

            if (result.matches().isEmpty()) {
                return "在 **" + fileName + "** 中未找到与「" + question + "」相关的片段。";
            }

            String displayName = getFullName(file);
            StringBuilder sb = new StringBuilder();
            sb.append("**【").append(displayName).append("】相关片段**（问题：").append(question).append("）\n\n");
            for (int i = 0; i < result.matches().size(); i++) {
                double score = result.matches().get(i).score();
                sb.append("片段").append(i + 1).append("（相关度 ").append(String.format("%.2f", score)).append("）：\n");
                sb.append(result.matches().get(i).embedded().text()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[Tool] askDocument failed: {}", e.getMessage());
            return "❌ 文档问答查询失败：" + e.getMessage();
        }
    }


    /** 构建 fileId + userId 双重过滤 */
    private Filter buildFileFilter(Long fileId) {
        return metadataKey("fileId").isEqualTo(String.valueOf(fileId))
                .and(metadataKey("userId").isEqualTo(String.valueOf(userId)));
    }

    // ==================== 获取当前时间 ====================

    @Tool("获取当前时间")
    public String getNowDate() {
        return LocalDateTime.now().toString();
    }


    // ==================== 内部辅助 ====================

    /** 根据文件名（含扩展名）在当前用户文件中查找，先精确匹配再模糊匹配 */
    private FileInfo findFileByName(String name) {
        if (name == null || name.isBlank()) return null;
        // 先从根目录精确匹配
        List<FileInfo> rootFiles = fileInfoService.findByParentIdAndUserId(userId, 0L);
        if (rootFiles != null) {
            for (FileInfo f : rootFiles) {
                String fullName = getFullName(f);
                if (name.trim().equalsIgnoreCase(fullName)) return f;
            }
            // 模糊匹配
            String lower = name.trim().toLowerCase();
            for (FileInfo f : rootFiles) {
                String fullName = getFullName(f);
                if (fullName != null && fullName.toLowerCase().contains(lower)) return f;
            }
        }
        // 全局关键词搜索兜底
        List<FileInfo> all = fileInfoService.findBykeyWord(name.trim(), userId);
        if (all != null && !all.isEmpty()) return all.get(0);
        return null;
    }

    /** 根据文件夹名查找文件夹 */
    private FileInfo findFolderByName(String name) {
        if (name == null || name.isBlank()) return null;
        List<FileInfo> rootFiles = fileInfoService.findByParentIdAndUserId(userId, 0L);
        if (rootFiles != null) {
            for (FileInfo f : rootFiles) {
                if (Boolean.TRUE.equals(f.getIsFolder())
                        && name.trim().equalsIgnoreCase(f.getFilename())) {
                    return f;
                }
            }
        }
        return null;
    }

    private static String getFullName(FileInfo f) {
        if (f.getFileSuffix() != null && !f.getFileSuffix().isBlank())
            return f.getFilename() + "." + f.getFileSuffix();
        return f.getFilename();
    }

    // ==================== 工具方法 ====================

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
