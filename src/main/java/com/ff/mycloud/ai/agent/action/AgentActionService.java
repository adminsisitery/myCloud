package com.ff.mycloud.ai.agent.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.agent.AgentProperties;
import com.ff.mycloud.ai.agent.AgentRun;
import com.ff.mycloud.ai.agent.AgentRunStore;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

/**
 * The model can only propose. Confirmation executes on the authenticated HTTP thread.
 */
@Service
public class AgentActionService {
    private static final Logger log = LoggerFactory.getLogger(AgentActionService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FileInfoMapperService files;
    private final FileService fileService;
    private final FolderService folders;
    private final DeleteFileService trash;
    private final AgentProperties properties;
    private final AgentRunStore runs;
    private final RedissonClient redis;
    private final UserCenterMapperService users;

    public AgentActionService(JdbcTemplate jdbc, ObjectMapper json, FileInfoMapperService files, FileService fileService,
                              FolderService folders, DeleteFileService trash, AgentProperties properties, AgentRunStore runs, RedissonClient redis, UserCenterMapperService users) {
        this.jdbc = jdbc;
        this.json = json;
        this.files = files;
        this.fileService = fileService;
        this.folders = folders;
        this.trash = trash;
        this.properties = properties;
        this.runs = runs;
        this.redis = redis;
        this.users = users;
    }

    public AgentAction propose(AgentRun run, AgentAction.Operation operation, Long fileId, Long parentId, String name) {
        return propose(run, operation, fileId, parentId, name, null, null);
    }

    public AgentAction draft(AgentRun run, Long parentId, String name, String content) {
        if (content == null || content.isBlank() || content.length() > 30000)
            throw new ResponseStatusException(BAD_REQUEST, "成果内容应为1到30000字符");
        if (name == null || !name.toLowerCase(Locale.ROOT).matches(".+\\.(md|txt|csv)"))
            throw new ResponseStatusException(BAD_REQUEST, "成果支持.md、.txt和.csv");
        // Neutralize spreadsheet formula injection in CSV exports.
        if (name.toLowerCase(Locale.ROOT).endsWith(".csv") && java.util.regex.Pattern.compile("(^|[,;\\r\\n])\\s*\"?[=+@-]").matcher(content).find())
            throw new ResponseStatusException(BAD_REQUEST, "CSV包含公式，请使用纯文本单元格");
        return propose(run, AgentAction.Operation.SAVE_DOCUMENT, null, parentId, name, content, null);
    }

    private AgentAction propose(AgentRun run, AgentAction.Operation operation, Long fileId, Long parentId, String name, String content, String reverses) {
        if (runs.shouldStop(run.getId())) throw new ResponseStatusException(CONFLICT, "任务已停止");
        if (run.getClarification() != null)
            throw new ResponseStatusException(CONFLICT, "任务正在等待用户补充信息，不能同时提出修改");
        if (list(run.getId(), run.getUserId()).size() >= 50)
            throw new ResponseStatusException(BAD_REQUEST, "每轮最多50项操作，请分批处理");
        AgentAction action = new AgentAction();
        action.setRunId(run.getId());
        action.setUserId(run.getUserId());
        action.setOperation(operation);
        action.setFileId(fileId);
        action.setParentId(parentId == null ? 0L : parentId);
        action.setName(name);
        action.setContent(content);
        action.setReversesActionId(reverses);
        validate(action, false);
        action.setExpiresAt(LocalDateTime.now().plusMinutes(properties.getActionExpiryMinutes()));
        String fingerprint = hash(operation + ":" + fileId + ":" + action.getParentId() + ":" + action.getName() + ":" + content + ":" + reverses);
        String ancestor = run.getParentRunId();
        for (int depth = 0; ancestor != null && depth < 10; depth++) {
            for (AgentAction old : list(ancestor, run.getUserId())) {
                String oldFingerprint = hash(old.getOperation() + ":" + old.getFileId() + ":" + old.getParentId() + ":" + old.getName() + ":" + old.getContent() + ":" + old.getReversesActionId());
                if (fingerprint.equals(oldFingerprint) && Set.of("SUCCEEDED", "REJECTED", "UNKNOWN", "EXECUTING", "PENDING").contains(old.getStatus()))
                    throw new ResponseStatusException(CONFLICT, "原任务中该操作已完成、被拒绝或仍待核实，不得重复提出");
            }
            ancestor = runs.get(ancestor, run.getUserId()).getParentRunId();
        }
        jdbc.update("INSERT IGNORE INTO t_ai_agent_action(id,run_id,user_id,fingerprint,status,snapshot,expires_at) VALUES(?,?,?,?,'PENDING',?,?)",
                action.getId(), run.getId(), run.getUserId(), fingerprint, encode(action), action.getExpiresAt());
        run.setPauseReason("WAITING_CONFIRMATION");
        return jdbc.queryForObject("SELECT snapshot,status FROM t_ai_agent_action WHERE run_id=? AND fingerprint=?", (rs, n) -> decode(rs.getString(1), rs.getString(2)), run.getId(), fingerprint);
    }

    public List<AgentAction> list(String runId, Long userId) {
        runs.get(runId, userId);
        return jdbc.query("SELECT snapshot,status FROM t_ai_agent_action WHERE run_id=? AND user_id=? ORDER BY created_at,id", (rs, n) -> decode(rs.getString(1), rs.getString(2)), runId, userId);
    }

    public AgentAction get(String id, Long userId) {
        var result = jdbc.query("SELECT snapshot,status FROM t_ai_agent_action WHERE id=? AND user_id=?", (rs, n) -> decode(rs.getString(1), rs.getString(2)), id, userId);
        if (result.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "操作不存在");
        return result.getFirst();
    }

    public AgentAction reject(String id, Long userId) {
        get(id, userId);
        jdbc.update("UPDATE t_ai_agent_action SET status='REJECTED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='PENDING'", id, userId);
        return get(id, userId);
    }

    public AgentAction confirm(String id, Long userId) {
        if (!Objects.equals(userId, UserContext.getCurrentUserId())) throw new ResponseStatusException(FORBIDDEN);
        var lock = redis.getLock("ai:action:user:" + userId);
        if (!lock.tryLock()) throw new ResponseStatusException(CONFLICT, "另一个文件操作正在执行");
        try {
            AgentAction action = get(id, userId);
            if (!"PENDING".equals(action.getStatus())) return action; // An HTTP retry cannot repeat a side effect.
            if (!action.getExpiresAt().isAfter(LocalDateTime.now())) {
                setStatus(id, "EXPIRED");
                return get(id, userId);
            }
            AgentRun run = runs.get(action.getRunId(), userId);
            if (!Set.of("SUCCEEDED", "WAITING_CONFIRMATION").contains(run.getStatus()))
                throw new ResponseStatusException(CONFLICT, "任务未完成或已停止，不能确认操作");
            try {
                validate(action, true);
            } catch (RuntimeException e) {
                setStatus(id, "STALE");
                throw e;
            }
            if (jdbc.update("UPDATE t_ai_agent_action SET status='EXECUTING',updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP", id, userId) != 1)
                return get(id, userId);
            long reservedBytes = 0;
            try {
                if (action.getOperation() == AgentAction.Operation.SAVE_DOCUMENT) {
                    long contentBytes = action.getContent().getBytes(StandardCharsets.UTF_8).length;
                    if (!users.reserveSpace(userId, contentBytes)) {
                        setStatus(id, "FAILED");
                        return get(id, userId);
                    }
                    reservedBytes = contentBytes;
                }
                switch (action.getOperation()) {
                    case CREATE_FOLDER -> {
                        var result = folders.createFolder(action.getParentId(), action.getName());
                        if (!Integer.valueOf(200).equals(result.getCode()))
                            throw new IllegalStateException("Folder service rejected operation");
                    }
                    case RENAME_FILE -> fileService.renameFile(action.getFileId(), action.getName());
                    case MOVE_FILE -> fileService.moveFile(action.getFileId(), action.getParentId());
                    case TRASH_FILE -> trash.deleteSingleFile(action.getFileId());
                    case RESTORE_FILE -> trash.restoreFile(action.getFileId());
                    case STAR_FILE -> fileService.starFile(action.getFileId());
                    case UNSTAR_FILE -> fileService.unstarFile(action.getFileId());
                    case SAVE_DOCUMENT -> {
                        String path = fileService.uploadFile(new AgentDocumentFile(action.getName(), action.getContent()), action.getParentId() == 0 ? null : action.getParentId());
                        if (path == null || path.isBlank()) throw new IllegalStateException("Document was not saved");
                    }
                }
                FileInfo result = verifyResult(action);
                action.setResultFileId(result.getId());
                action.setResultVersion(version(result));
                action.setVerified(true);
                jdbc.update("UPDATE t_ai_agent_action SET snapshot=? WHERE id=? AND user_id=?", encode(action), id, userId);
                setStatus(id, "SUCCEEDED");
                log.info("Agent action completed: actionId={}, runId={}, userId={}, operation={}, fileId={}", id, action.getRunId(), userId, action.getOperation(), action.getFileId());
            } catch (Exception e) {
                // DB/MQ/storage may have partially committed; never automatically retry UNKNOWN.
                // 预占的空间只在「成果确实没有落库」时才归还：部分提交（文件已入库但后续步骤失败）时
                // 空间确实被占用，归还等于把额度白送给用户，属资损，交由 reconcile 核实后再定。
                if (reservedBytes > 0 && documentNotPersisted(action)) {
                    users.releaseSpace(userId, reservedBytes);
                    log.warn("成果保存失败且未落库，已归还预占空间 - actionId={}, userId={}, bytes={}",
                            id, userId, reservedBytes);
                }
                setStatus(id, "UNKNOWN");
                log.error("Agent action outcome needs verification: actionId={}, userId={}", id, userId, e);
            }
            return get(id, userId);
        } finally {
            lock.unlock();
        }
    }

    private void validate(AgentAction action, boolean recheck) {
        if (action.getOperation() == null) throw new ResponseStatusException(BAD_REQUEST, "操作类型不能为空");
        if (action.getOperation() == AgentAction.Operation.CREATE_FOLDER || action.getOperation() == AgentAction.Operation.RENAME_FILE || action.getOperation() == AgentAction.Operation.SAVE_DOCUMENT) {
            action.setName(validName(action.getName(), action.getOperation() == AgentAction.Operation.CREATE_FOLDER ? 30 : 50));
        }
        FileInfo source = null;
        if (action.getOperation() != AgentAction.Operation.CREATE_FOLDER && action.getOperation() != AgentAction.Operation.SAVE_DOCUMENT) {
            source = requireFile(action.getFileId(), action.getUserId(), action.getOperation() == AgentAction.Operation.RESTORE_FILE);
            String version = version(source);
            if (recheck && !version.equals(action.getSourceVersion()))
                throw new ResponseStatusException(CONFLICT, "文件已变化，请重新生成操作");
            action.setSourceVersion(version);
            if (!recheck) {
                action.setOriginalName(fullName(source));
                action.setOriginalParentId(source.getParentId());
                action.setOriginalStarred(Boolean.TRUE.equals(source.getIsStarred()));
            }
        }
        if (action.getOperation() == AgentAction.Operation.CREATE_FOLDER || action.getOperation() == AgentAction.Operation.MOVE_FILE || action.getOperation() == AgentAction.Operation.SAVE_DOCUMENT) {
            FileInfo parent = requireFolder(action.getParentId(), action.getUserId());
            String version = parent == null ? "ROOT" : version(parent);
            if (recheck && !version.equals(action.getTargetVersion()))
                throw new ResponseStatusException(CONFLICT, "目标目录已变化，请重新生成操作");
            action.setTargetVersion(version);
            if (source != null && Boolean.TRUE.equals(source.getIsFolder()) && parent != null &&
                    (source.getId().equals(parent.getId()) || (source.getLocalFilePath() != null && parent.getLocalFilePath() != null && parent.getLocalFilePath().startsWith(source.getLocalFilePath()))))
                throw new ResponseStatusException(BAD_REQUEST, "不能将文件夹移入自身或子目录");
        }
        if (action.getOperation() == AgentAction.Operation.SAVE_DOCUMENT) {
            if (action.getContent() == null || action.getContent().isBlank() || action.getContent().length() > 30000)
                throw new ResponseStatusException(BAD_REQUEST, "成果内容无效");
            if (!matchingChildren(action).isEmpty())
                throw new ResponseStatusException(CONFLICT, "目标目录存在同名成果，请更换名称");
        }
        String display = source == null ? "新建“" + action.getName() + "”" : fullName(source) + " (ID=" + source.getId() + ")";
        action.setDescription(action.getOperation() + "：" + display +
                (action.getOperation() == AgentAction.Operation.RENAME_FILE ? " → " + action.getName() : "") +
                (action.getOperation() == AgentAction.Operation.CREATE_FOLDER || action.getOperation() == AgentAction.Operation.MOVE_FILE || action.getOperation() == AgentAction.Operation.SAVE_DOCUMENT ? "，目标目录 ID=" + action.getParentId() : ""));
    }

    /**
     * Read-only recovery: UNKNOWN never repeats a possibly committed write.
     */
    public AgentAction reconcile(String id, Long userId) {
        var lock = redis.getLock("ai:action:user:" + userId);
        if (!lock.tryLock()) throw new ResponseStatusException(CONFLICT, "文件操作正在执行");
        try {
            AgentAction a = get(id, userId);
            if (!"UNKNOWN".equals(a.getStatus())) return a;
            // New-object creation cannot be attributed safely after a crash using just a name.
            if (a.getOperation() == AgentAction.Operation.CREATE_FOLDER || a.getOperation() == AgentAction.Operation.SAVE_DOCUMENT)
                return a;
            try {
                FileInfo result = verifyResult(a);
                a.setResultFileId(result.getId());
                a.setResultVersion(version(result));
                a.setVerified(true);
                jdbc.update("UPDATE t_ai_agent_action SET snapshot=?,status='SUCCEEDED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='UNKNOWN'", encode(a), id, userId);
            } catch (RuntimeException ignored) { /* Leave unresolved until its actual effect can be verified. */ }
            return get(id, userId);
        } finally {
            lock.unlock();
        }
    }

    public AgentAction proposeUndo(AgentRun run, String id) {
        AgentAction prior = get(id, run.getUserId());
        if (!"SUCCEEDED".equals(prior.getStatus()) || !prior.isVerified() || prior.getResultFileId() == null)
            throw new ResponseStatusException(CONFLICT, "只能撤销已核实的操作");
        if (prior.getOperation() == AgentAction.Operation.CREATE_FOLDER || prior.getOperation() == AgentAction.Operation.SAVE_DOCUMENT || prior.getOperation() == AgentAction.Operation.RESTORE_FILE)
            throw new ResponseStatusException(BAD_REQUEST, "该操作不支持自动撤销，请明确提出新的文件操作");
        var current = requireFile(prior.getResultFileId(), run.getUserId(), prior.getOperation() == AgentAction.Operation.TRASH_FILE);
        if (!Objects.equals(prior.getResultVersion(), version(current)))
            throw new ResponseStatusException(CONFLICT, "文件在操作后已变化，不能直接撤销");
        AgentAction.Operation reverse = switch (prior.getOperation()) {
            case RENAME_FILE -> AgentAction.Operation.RENAME_FILE;
            case MOVE_FILE -> AgentAction.Operation.MOVE_FILE;
            case TRASH_FILE -> AgentAction.Operation.RESTORE_FILE;
            case STAR_FILE, UNSTAR_FILE ->
                    Boolean.TRUE.equals(prior.getOriginalStarred()) ? AgentAction.Operation.STAR_FILE : AgentAction.Operation.UNSTAR_FILE;
            default -> throw new ResponseStatusException(BAD_REQUEST);
        };
        return propose(run, reverse, current.getId(), prior.getOriginalParentId(), prior.getOriginalName(), null, id);
    }

    public List<AgentAction> audit(Long userId, Long conversationId, int offset) {
        if (offset < 0 || offset > 10000) throw new ResponseStatusException(BAD_REQUEST);
        return jdbc.query("SELECT a.snapshot,a.status FROM t_ai_agent_action a JOIN t_ai_agent_run r ON r.id=a.run_id WHERE a.user_id=? AND r.conversation_id=? ORDER BY a.created_at DESC,a.id DESC LIMIT 50 OFFSET ?",
                (rs, n) -> decode(rs.getString(1), rs.getString(2)), userId, conversationId, offset);
    }

    private List<FileInfo> matchingChildren(AgentAction a) {
        var query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileInfo>().eq("user_id", a.getUserId()).eq("isDeleted", false);
        if (a.getParentId() == null || a.getParentId() == 0)
            query.and(q -> q.eq("parent_id", 0).or().isNull("parent_id"));
        else query.eq("parent_id", a.getParentId());
        String name = a.getName();
        int dot = name.lastIndexOf('.');
        boolean folder = a.getOperation() == AgentAction.Operation.CREATE_FOLDER;
        query.eq("filename", !folder && dot > 0 ? name.substring(0, dot) : name).eq("isFolder", folder);
        if (!folder && dot > 0) query.eq("file_suffix", name.substring(dot + 1));
        return files.list(query.last("LIMIT 2"));
    }

    /**
     * 判断成果是否确实没有落库（用于决定能否归还预占空间）
     * <p>只有查明确实不存在同名成果时才返回 true；查询本身出错一律按「可能已落库」
     * 处理并返回 false —— 宁可少还，也不能把已经占用的额度还掉。</p>
     */
    private boolean documentNotPersisted(AgentAction action) {
        if (action.getOperation() != AgentAction.Operation.SAVE_DOCUMENT) return false;
        try {
            return matchingChildren(action).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private FileInfo verifyResult(AgentAction a) {
        FileInfo result;
        if (a.getOperation() == AgentAction.Operation.CREATE_FOLDER || a.getOperation() == AgentAction.Operation.SAVE_DOCUMENT) {
            var found = matchingChildren(a);
            if (found.size() != 1) throw new IllegalStateException("Created object could not be identified uniquely");
            result = requireFile(found.getFirst().getId(), a.getUserId(), false);
            if (a.getOperation() == AgentAction.Operation.SAVE_DOCUMENT && !hash(a.getContent()).equals(result.getSha256Hash()))
                throw new IllegalStateException("Saved content hash mismatch");
        } else result = requireFile(a.getFileId(), a.getUserId(), a.getOperation() == AgentAction.Operation.TRASH_FILE);
        boolean valid = switch (a.getOperation()) {
            case RENAME_FILE -> fullName(result).equals(a.getName()) || result.getFilename().equals(a.getName());
            case MOVE_FILE -> Objects.equals(result.getParentId() == null ? 0L : result.getParentId(), a.getParentId());
            case STAR_FILE -> Boolean.TRUE.equals(result.getIsStarred());
            case UNSTAR_FILE -> !Boolean.TRUE.equals(result.getIsStarred());
            default -> true;
        };
        if (!valid) throw new IllegalStateException("File state does not match requested operation");
        return result;
    }

    private static String fullName(FileInfo f) {
        return f.getFilename() + (Boolean.TRUE.equals(f.getIsFolder()) || f.getFileSuffix() == null || f.getFileSuffix().isBlank() ? "" : "." + f.getFileSuffix());
    }

    public FileInfo requireFile(Long id, Long userId, boolean deleted) {
        if (id == null || id <= 0) throw new ResponseStatusException(BAD_REQUEST, "文件 ID 无效");
        FileInfo file = files.getById(id);
        if (file == null || !Objects.equals(file.getUserId(), userId) || Boolean.TRUE.equals(file.getIsDeleted()) != deleted)
            throw new ResponseStatusException(NOT_FOUND, "文件不存在或无权访问");
        return file;
    }

    public FileInfo requireFolder(Long id, Long userId) {
        if (id == null || id == 0L) return null;
        FileInfo file = requireFile(id, userId, false);
        if (!Boolean.TRUE.equals(file.getIsFolder())) throw new ResponseStatusException(BAD_REQUEST, "目标不是文件夹");
        return file;
    }

    static String validName(String name, int max) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "名称不能为空");
        name = name.trim();
        if (name.length() > max || name.equals(".") || name.equals("..") || name.chars().anyMatch(c -> c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0))
            throw new ResponseStatusException(BAD_REQUEST, "名称含非法字符或过长");
        return name;
    }

    private String version(FileInfo file) {
        return hash(file.getId() + ":" + file.getFilename() + ":" + file.getParentId() + ":" + file.getLocalFilePath() + ":" + file.getUpdatedAt() + ":" + file.getIsDeleted() + ":" + file.getIsStarred());
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void setStatus(String id, String status) {
        jdbc.update("UPDATE t_ai_agent_action SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
    }

    private String encode(AgentAction a) {
        try {
            return json.writeValueAsString(a);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private AgentAction decode(String s, String status) {
        try {
            var a = json.readValue(s, AgentAction.class);
            a.setStatus(status);
            return a;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
