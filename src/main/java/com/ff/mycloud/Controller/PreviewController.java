package com.ff.mycloud.Controller;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.FileTypeUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/6 22:32
 * @version 1.0
 */

@RestController
@RequestMapping("/api/files/preview")
public class PreviewController {

    FileInfoMapperService fileInfoMapperService;

    FileService fileService;

    public PreviewController(FileInfoMapperService fileInfoMapperService,
                             FileService fileService) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileService = fileService;
    }

    @GetMapping("{fileId}")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long fileId) {
        //根据fileId查数据
        FileInfo fileInfo = fileInfoMapperService.findByIdAndUserId(fileId, UserContext.getCurrentUserId());
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        //从oss得到文件流
        InputStream file = fileService.getFile(fileInfo.getPath());
        //切换成spring能给浏览器的
        InputStreamResource resource = new InputStreamResource(file);
        String fileSuffix = fileInfo.getFileSuffix();
        String contentTypeFromFileSuffix = FileTypeUtil.getContentTypeFromFileSuffix(fileSuffix);
        if (!FileTypeUtil.isPreviewSupported(fileSuffix)) {
            throw new BusinessException(ErrorCode.FILE_NOT_SUPPORT_PREVIEW);
        }
        //设置header
        HttpHeaders httpHeaders = new HttpHeaders();
        ContentDisposition contentDisposition = ContentDisposition
                .inline()
                .filename(fileInfo.getFilename() + "." + fileSuffix)
                .build();
        httpHeaders.setContentDisposition(contentDisposition);
        httpHeaders.setContentLength(fileInfo.getFileSize());
        httpHeaders.setContentType(MediaType.valueOf(contentTypeFromFileSuffix));
        //写文件到Response
        return ResponseEntity.ok()
                .headers(httpHeaders)
                .body(resource);
    }
}
