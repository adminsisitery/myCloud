package com.ff.mycloud.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.ai.model.entity.QueryData;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FileSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件搜索服务实现类
 * <p>
 * 基于Elasticsearch实现文件名的模糊搜索，搜索结果回查MySQL校验权限并组装完整信息。
 * 同时管理ES索引中文件文档的增删改操作，支持全量重建索引（initEsIndex）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/12 19:42
 * @version 1.0
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
@Slf4j
public class FileSearchServiceImpl implements FileSearchService {

    FileSearchRepository fileSearchRepository;
    FileInfoMapperService fileInfoMapperService;
    private FileInfoMapper fileInfoMapper;

    /**
     * 构造函数
     *
     * @param fileSearchRepository  ES文件搜索仓库
     * @param fileInfoMapperService 文件信息数据库服务
     * @param fileInfoMapper        文件信息Mapper
     */
    public FileSearchServiceImpl(FileSearchRepository fileSearchRepository,
                                 FileInfoMapperService fileInfoMapperService,
                                 FileInfoMapper fileInfoMapper) {
        this.fileSearchRepository = fileSearchRepository;
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileInfoMapper = fileInfoMapper;
    }

    /**
     * 根据关键词搜索当前用户的文件
     * <p>
     * 先在ES中按文件名模糊搜索并限制用户ID，再回查MySQL获取完整文件信息。
     * 对非文件夹文件拼接文件名和后缀。
     * </p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件信息列表，无结果返回空列表
     */
    @Override
    public List<FileInfo> searchFile(String keyword) {
        // 在ES中按文件名关键词和用户ID模糊搜索
        List<FileDocument> byFileNameContainingAndUserId = fileSearchRepository.findByFileNameContainingAndUserId(keyword, UserContext.getCurrentUserId());
        List<FileDocument> byContentContainingAndUserId = fileSearchRepository.findByContentContainingAndUserId(keyword, UserContext.getCurrentUserId());
        Set<Long> ids = new LinkedHashSet<>();
        byFileNameContainingAndUserId.forEach(d -> {ids.add(d.getId());});
        byContentContainingAndUserId.forEach(b -> {ids.add(b.getId());});
        //提取id
        if (ids.isEmpty()){
            return List.of();
        }

        List<FileInfo> inIdAndUserId = fileInfoMapperService.findInIdAndUserId(new ArrayList<>(ids), UserContext.getCurrentUserId());
        for (FileInfo fileInfo : inIdAndUserId) {
            if (!fileInfo.getIsFolder()){
                fileInfo.setFilename(fileInfo.getFilename()+"."+fileInfo.getFileSuffix());
            }
        }
        return inIdAndUserId;
    }

    /**
     * 从ES索引中删除指定文件文档
     *
     * @param fileId 文件ID
     */
    @Override
    public void del(Long fileId) {
        fileSearchRepository.deleteById(fileId);
    }

    /**
     * 更新ES索引中文件的名称
     * <p>先查找再更新保存，若文档不存在则不做任何操作。</p>
     *
     * @param fileId 文件ID
     * @param name   新的文件名
     */
    @Override
    public void renameById(Long fileId, String name) {
        fileSearchRepository.findById(fileId).ifPresent(doc -> {
            doc.setFileName(name);
            fileSearchRepository.save(doc);
        });
    }

    /**
     * 保存文件文档到ES索引（新增或全量更新）
     *
     * @param fileDocument ES文件文档对象
     */
    @Override
    public void save(FileDocument fileDocument) {
        fileSearchRepository.save(fileDocument);
    }

    @Override
    public List<FileInfo> searchFileByQD(QueryData queryData) {
        return searchFileByQD(queryData, UserContext.getCurrentUserId());
    }

    /**
     * 基于 QueryData 的结构化搜索（Tool 专用，显式传入 userId）。
     * <p>
     * 有关键词时：ES 命中 → MySQL 一次查询完成校验+过滤 → 加权排序。
     * 无关键词时：MySQL LambdaWrapper，所有条件下推到 DB。
     * </p>
     */
    @Override
    public List<FileInfo> searchFileByQD(QueryData queryData, Long userId) {
        String keyword = queryData.getFileName();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasKeyword) {
            // ===== ES 关键词 + 权重记录 =====
            // weight=2 文件名命中, weight=1 内容命中
            Map<Long, Integer> weightMap = new LinkedHashMap<>();
            List<FileDocument> byFileName = fileSearchRepository.findByFileNameContainingAndUserId(keyword, userId);
            List<FileDocument> byContent  = fileSearchRepository.findByContentContainingAndUserId(keyword, userId);
            if (byFileName != null) byFileName.forEach(d -> weightMap.merge(d.getId(), 2, Integer::sum));
            if (byContent  != null) byContent.forEach(d  -> weightMap.merge(d.getId(), 1, Integer::sum));

            if (weightMap.isEmpty()) return List.of();

            // MySQL 一次查询：校验 ID + 下推 date/type/size 过滤
            List<FileInfo> files = fileInfoMapperService.findInIdAndUserId(
                    new ArrayList<>(weightMap.keySet()), userId, queryData);

            // 加权排序：文件名+内容命中排前面
            files.sort((a, b) -> Integer.compare(
                    weightMap.getOrDefault(b.getId(), 0),
                    weightMap.getOrDefault(a.getId(), 0)));

            return files;
        }

        // ===== 无关键词 → MySQL LambdaWrapper =====
        LocalDateTime dateFrom = tryParseDate(queryData.getDateFrom(), true);
        LocalDateTime dateTo   = tryParseDate(queryData.getDateTo(), false);

        List<FileInfo> files = fileInfoMapperService.lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getIsFolder, false)
                .apply(dateFrom != null, "created_at >= {0}", dateFrom)
                .apply(dateTo   != null, "created_at <= {0}", dateTo)
                .eq(queryData.getFileType() != null && !queryData.getFileType().isBlank(),
                        FileInfo::getFileSuffix,
                        queryData.getFileType() != null ? queryData.getFileType().toLowerCase() : null)
                .ge(queryData.getMinSize() != null && queryData.getMinSize() > 0,
                        FileInfo::getFileSize, queryData.getMinSize())
                .le(queryData.getMaxSize() != null && queryData.getMaxSize() > 0,
                        FileInfo::getFileSize, queryData.getMaxSize())
                .list();

        return files != null ? files : List.of();
    }

    private LocalDateTime tryParseDate(String date, boolean startOfDay) {
        if (date == null || date.isBlank()) return null;
        try {
            return startOfDay
                    ? LocalDate.parse(date).atStartOfDay()
                    : LocalDate.parse(date).atTime(LocalTime.MAX);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 初始化/重建ES全文索引
     * <p>
     * 从MySQL全量读取所有未删除的文件记录，转换为ES文档后批量写入。
     * 通常用于首次部署ES后的数据同步或索引重建。
     * </p>
     */
    public void initEsIndex() {

        // 查询数据库所有文件
        List<FileInfo> list = fileInfoMapper.selectList(null);

        List<FileDocument> docs = new ArrayList<>();

        for (FileInfo file : list) {

            // 不同步删除文件
            if(Boolean.TRUE.equals(file.getIsDeleted())){
                continue;
            }

            FileDocument doc = new FileDocument();
            doc.setId(file.getId());
            doc.setFileName(file.getFilename());
            doc.setUserId(file.getUserId());

            docs.add(doc);
        }

        // 批量写入 ES
        fileSearchRepository.saveAll(docs);
        log.info("ES索引重建完成: documentCount={}", docs.size());
    }
}
