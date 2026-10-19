package com.cloudrive.aiService.agent.action;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class AgentAction {
    public enum Operation { CREATE_FOLDER, RENAME_FILE, MOVE_FILE, TRASH_FILE, RESTORE_FILE, STAR_FILE, UNSTAR_FILE, SAVE_DOCUMENT }
    private String id = UUID.randomUUID().toString();
    private String runId;
    private Long userId;
    private Operation operation;
    private Long fileId;
    private Long parentId;
    private String name;
    private String description;
    private String sourceVersion;
    private String targetVersion;
    private String content;
    private Long resultFileId;
    private String resultVersion;
    private String originalName;
    private Long originalParentId;
    private Boolean originalStarred;
    private String reversesActionId;
    private boolean verified;
    private String status = "PENDING";
    private LocalDateTime expiresAt;
}
