package com.cloudrive.fileSearchService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.fileSearchService.model.entity.FileInfo;

import java.util.List;

/**
 * 文件信息查询服务接口（只读）
 * <p>
 * 仅保留搜索链路所需的两个查询：MySQL LIKE 关键词搜索与按 ID 列表回查校验。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
public interface FileInfoMapperService extends IService<FileInfo> {

    /**
     * 按文件名关键词模糊搜索当前用户的未删除文件（MySQL LIKE 策略）
     *
     * @param keyword       关键词
     * @param currentUserId 当前用户 ID
     * @return 匹配的文件列表
     */
    List<FileInfo> findBykeyWord(String keyword, Long currentUserId);

    /**
     * 按 ID 列表 + 用户 ID 查询未删除文件（ES 命中后的归属校验）
     *
     * @param ids           文件 ID 列表
     * @param currentUserId 当前用户 ID
     * @return 匹配的文件列表
     */
    List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId);
}
