package com.ff.mycloud.service.impl;

//import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FileSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/12 19:42
 * @version 1.0
 */

@Service
public class FileSearchServiceImpl implements FileSearchService {
//
////    FileSearchRepository fileSearchRepository;
//    FileInfoMapperService fileInfoMapperService;
//    private FileInfoMapper fileInfoMapper;
//    public FileSearchServiceImpl(FileSearchRepository fileSearchRepository,
//                                 FileInfoMapperService fileInfoMapperService,
//                                 FileInfoMapper fileInfoMapper) {
//        this.fileSearchRepository = fileSearchRepository;
//        this.fileInfoMapperService = fileInfoMapperService;
//        this.fileInfoMapper = fileInfoMapper;
//    }
//
//    @Override
//    public List<FileInfo> searchFile(String keyword) {
//        //查找ES
//        List<FileDocument> containing = fileSearchRepository.findByFileNameContainingAndUserId(keyword,UserContext.getCurrentUserId());
//        //提取id
//        List<Long> ids = containing.stream().map(FileDocument::getId).toList();
//
//        if (ids.isEmpty()){
//            return List.of();
//        }
//
//        List<FileInfo> inIdAndUserId = fileInfoMapperService.findInIdAndUserId(ids, UserContext.getCurrentUserId());
//        for (FileInfo fileInfo : inIdAndUserId) {
//            if (!fileInfo.getIsFolder()){
//                fileInfo.setFilename(fileInfo.getFilename()+"."+fileInfo.getFileSuffix());
//            }
//        }
//        return inIdAndUserId;
//    }
//
//    @Override
//    public void del(Long fileId) {
//        fileSearchRepository.deleteById(fileId);
//    }
//
//    @Override
//    public void renameById(Long fileId, String name) {
//
//        fileSearchRepository.findById(fileId).ifPresent(doc -> {
//            doc.setFileName(name);
//            fileSearchRepository.save(doc);
//        });
//
//    }
//
//    @Override
//    public void save(Long id, String filename, Long currentUserId) {
//        FileDocument fileDocument = new FileDocument();
//        fileDocument.setId(id);
//        fileDocument.setFileName(filename);
//        fileDocument.setUserId(currentUserId);
//        fileSearchRepository.save(fileDocument);
//    }
//
//
//    public void initEsIndex() {
//
//        // 查询数据库所有文件
//        List<FileInfo> list = fileInfoMapper.selectList(null);
//
//        List<FileDocument> docs = new ArrayList<>();
//
//        for (FileInfo file : list) {
//
//            // 不同步删除文件
//            if(Boolean.TRUE.equals(file.getIsDeleted())){
//                continue;
//            }
//
//            FileDocument doc = new FileDocument();
//            doc.setId(file.getId());
//            doc.setFileName(file.getFilename());
//            doc.setUserId(file.getUserId());
//
//            docs.add(doc);
//        }
//
//        // 批量写入 ES
//        fileSearchRepository.saveAll(docs);
//    }
}
