package com.ff.mycloud.handler;

import com.ff.mycloud.model.vo.FileListVO;

import java.util.List;

public interface FileSearchHandler {
    public List<FileListVO> searchFiles(String keyword);
}
