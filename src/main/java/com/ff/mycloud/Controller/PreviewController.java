package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.FileTypeUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.PreviewVO;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.impl.OssStorageServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * 文件预览控制器
 * <p>
 * 根据文件ID生成 OSS 预签名 URL，供前端实现文件的在线预览。
 * 仅允许文件所有者访问，非所有者返回无权限错误。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/6 22:32
 * @version 1.0
 */

@RestController
@RequestMapping("/api/files/preview")
public class PreviewController {

    FileInfoMapperService fileInfoMapperService;
    OssStorageServiceImpl ossStorageService;

    FileService fileService;

    /**
     * 构造器注入文件信息、文件核心和OSS存储服务
     *
     * @param fileInfoMapperService 文件信息 Mapper 服务
     * @param fileService           文件核心服务
     * @param ossStorageService     OSS 存储服务
     */
    public PreviewController(FileInfoMapperService fileInfoMapperService,
                             FileService fileService,
                             OssStorageServiceImpl ossStorageService) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileService = fileService;
        this.ossStorageService = ossStorageService;
    }


    /**
     * 获取文件预览信息
     * <p>
     * 校验当前用户是否为文件所有者，生成 5 分钟 OSS 预签名 URL，
     * 同时返回预览分类（image/video/audio/pdf/text），前端根据分类选择对应渲染组件。
     * </p>
     *
     * @param fileId 文件ID
     * @return 预览信息（URL + 分类 + MIME + 文件名 + 大小）
     * @throws BusinessException 当用户无权限、文件不存在或不支持预览时抛出
     */
    @GetMapping("/{fileId}")
    @RateLimit(dimensions = { RateLimit.Dimension.USER, RateLimit.Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public Result<PreviewVO> preview(@PathVariable Long fileId) {

        FileInfo fileInfo = fileInfoMapperService.findByIdAndUserId(fileId, UserContext.getCurrentUserId());
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        String suffix = fileInfo.getFileSuffix();
        if (!FileTypeUtil.isPreviewSupported(suffix)) {
            throw new BusinessException(ErrorCode.FILE_NOT_SUPPORT_PREVIEW);
        }

        // 生成 OSS 预签名 URL（5分钟有效期）
        String url = ossStorageService.generatePresignedUrl(fileInfo.getPath(), 300);

        String fullName = fileInfo.getFilename();
        if (suffix != null && !suffix.isEmpty()) {
            fullName = fullName + "." + suffix;
        }

        PreviewVO vo = new PreviewVO(
                url,
                FileTypeUtil.getPreviewCategory(suffix),
                FileTypeUtil.getContentTypeFromFileSuffix(suffix),
                fullName,
                fileInfo.getFileSize()
        );
        return Result.success(vo);
    }

    //    @GetMapping("{fileId}")
//    public ResponseEntity<InputStreamResource> preview(@PathVariable Long fileId) {
//        //根据fileId查数据
//        FileInfo fileInfo = fileInfoMapperService.findByIdAndUserId(fileId, UserContext.getCurrentUserId());
//        if (fileInfo == null) {
//            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
//        }
//        //从oss得到文件流
//        InputStream file = fileService.getFile(fileInfo.getPath());
//        //切换成spring能给浏览器的
//        InputStreamResource resource = new InputStreamResource(file);
//        String fileSuffix = fileInfo.getFileSuffix();
//        String contentTypeFromFileSuffix = FileTypeUtil.getContentTypeFromFileSuffix(fileSuffix);
//        if (!FileTypeUtil.isPreviewSupported(fileSuffix)) {
//            throw new BusinessException(ErrorCode.FILE_NOT_SUPPORT_PREVIEW);
//        }
//        //设置header
//        HttpHeaders httpHeaders = new HttpHeaders();
//        ContentDisposition contentDisposition = ContentDisposition
//                .inline()
//                .filename(fileInfo.getFilename() + "." + fileSuffix)
//                .build();
//        httpHeaders.setContentDisposition(contentDisposition);
//        httpHeaders.setContentLength(fileInfo.getFileSize());
//        httpHeaders.setContentType(MediaType.valueOf(contentTypeFromFileSuffix));
//        //写文件到Response
//        return ResponseEntity.ok()
//                .headers(httpHeaders)
//                .body(resource);
//    }
}
