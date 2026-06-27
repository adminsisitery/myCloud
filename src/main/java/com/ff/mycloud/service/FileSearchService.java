package com.ff.mycloud.service;

import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;

import java.util.List;

public interface FileSearchService {
    List<FileInfo> searchFile(String keyword);

    void del(Long fileId);

    void renameById(Long fileId, String s);

    void save(FileDocument fileDocument);
}
