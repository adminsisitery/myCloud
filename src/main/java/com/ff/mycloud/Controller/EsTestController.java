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
 * Elasticsearch 测试控制器
 * <p>
 * 用于测试 ES 索引操作和 OSS 预签名URL 生成功能。
 * 提供基于用户权限的文件预签名 URL 获取接口。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/12 18:11
 * @version 1.0
 */

@RestController
@RequestMapping("/es")
public class EsTestController {
    private final FileInfoMapperServiceImpl fileInfoMapperServiceImpl;
    OssStorageServiceImpl ossStorageServiceImpl;

    /**
     * 构造器注入文件信息和OSS服务
     *
     * @param fileInfoMapperServiceImpl 文件信息Mapper服务
     * @param ossStorageServiceImpl     OSS存储服务
     */
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

    /**
     * 生成文件预签名访问 URL
     * <p>
     * 根据文件ID和当前登录用户，校验权限后生成 OSS 预签名 URL，
     * 有效期为 5 分钟（300秒），包含 IP 限制以增强安全性。
     * </p>
     *
     * @param fileId  文件ID
     * @param request HTTP 请求对象
     * @return 预签名 URL 结果
     */
    @GetMapping("/pre")
    public Result<String> pre(@RequestParam Long fileId, HttpServletRequest request) {
        // 获取当前登录用户ID
        Long userId = UserContext.getCurrentUserId();

        // 查询文件信息，验证文件是否存在且属于当前用户
        FileInfo fileInfo = fileInfoMapperServiceImpl.findByIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            return Result.error("error");
        }
        // 生成带 IP 限制的签名URL（5分钟有效期）
        String signedUrl = ossStorageServiceImpl.generatePresignedUrlWithIpRestriction(
                fileInfo.getPath(),
                300
        );
        return Result.success(signedUrl);
    }

}