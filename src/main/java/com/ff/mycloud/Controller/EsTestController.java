package com.ff.mycloud.Controller;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.FileDocument;
//import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileSearchService;
import com.ff.mycloud.service.impl.FileInfoMapperServiceImpl;
import com.ff.mycloud.service.impl.FileSearchServiceImpl;
import com.ff.mycloud.service.impl.OssStorageServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/12 18:11
 * @version 1.0
 */

@RestController
@RequestMapping("/es")
public class EsTestController {
    private final FileInfoMapperServiceImpl fileInfoMapperServiceImpl;
    OssStorageServiceImpl ossStorageServiceImpl;

    public EsTestController(FileInfoMapperServiceImpl fileInfoMapperServiceImpl, OssStorageServiceImpl ossStorageServiceImpl) {
        this.fileInfoMapperServiceImpl = fileInfoMapperServiceImpl;
        this.ossStorageServiceImpl = ossStorageServiceImpl;
    }

//    @Autowired
//    private FileSearchRepository fileSearchRepository;
//    @Autowired
//    FileSearchServiceImpl fileSearchServiceImpl;
//    @GetMapping("/test")
//    public String test(){
//
//        FileDocument doc = new FileDocument();
//        doc.setId(1L);
//        doc.setFileName("Java笔记.pdf");
//        doc.setUserId(1L);
//
//        fileSearchRepository.save(doc);
//
//        return "ok";
//    }
//    @GetMapping("/tb")
//    public String test2(){
//        fileSearchServiceImpl.initEsIndex();
//        return "ok";
//    }

    @GetMapping("/pre")
    public Result<String> pre(@RequestParam Long fileId, HttpServletRequest request) {
        Long userId = UserContext.getCurrentUserId();

        FileInfo fileInfo = fileInfoMapperServiceImpl.findByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            return Result.error("error");
        }
        // 5. 生成签名URL（5分钟有效期 + IP绑定）
        String signedUrl = ossStorageServiceImpl.generatePresignedUrlWithIpRestriction(
                fileInfo.getPath(),
                300
        );
        return Result.success(signedUrl);
    }

}