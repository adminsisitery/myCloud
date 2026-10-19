package com.cloudrive.fileService.service;

import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * 文件存储服务接口
 * <p>
 * 定义统一的文件存储操作规范，支持文件上传、下载、删除、获取流等操作。
 * 具体实现可以是OSS、本地存储、MinIO等，通过实现此接口进行适配。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
public interface StorageService {

    /**
     * 上传MultipartFile文件到指定路径
     *
     * @param file 上传的文件对象
     * @param path 目标存储路径（目录）
     * @return 上传后的完整对象路径（含文件名）
     */
    String UploadFile(MultipartFile file, String path);

    /**
     * 从本地临时文件上传到存储服务，并支持进度追踪
     *
     * @param file             本地临时文件
     * @param uploadPath       目标上传路径
     * @param taskId           上传任务ID，用于进度追踪
     * @param originalFilename 原始文件名
     * @param size             文件大小（字节）
     * @param fileType         文件MIME类型
     * @return 上传后的完整对象路径
     */
    String uploadFileWithProgressFromPath(File file, String uploadPath, String taskId, String originalFilename, long size, String fileType);

    /** 以指定 objectKey 上传流，供后台生成物（如临时 ZIP）使用。 */
    void uploadObject(String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 下载文件内容为字节数组
     *
     * @param filePath 文件存储路径（对象Key）
     * @return 文件内容的字节数组
     */
    byte[] downloadFile(String filePath);

    /**
     * 删除指定路径的文件
     *
     * @param path 文件存储路径（对象Key）
     */
    void deleteFile(String path);

    /**
     * 获取文件的输入流
     *
     * @param path 文件存储路径（对象Key）
     * @return 文件输入流
     */
    InputStream getFile(String path);

    /** 获取对象元数据，用于直传确认和流式下载。 */
    ObjectMetadata getObjectMetadata(String path);

    /** 获取闭区间 [start, end] 的对象流；关闭返回流时必须同时释放存储客户端。 */
    InputStream getFileRange(String path, long start, long end);

    /**
     * 初始化OSS分片上传
     *
     * @param objectKey OSS对象路径（目录+文件名）
     * @return OSS返回的uploadId，用于后续uploadPart/completeMultipartUpload
     */
    String initiateMultipartUpload(String objectKey);

    /**
     * 初始化OSS分片上传（指定Content-Type）
     */
    default String initiateMultipartUpload(String objectKey, String contentType) {
        return initiateMultipartUpload(objectKey);
    }

    /**
     * 上传单个分片到OSS
     *
     * @param objectKey   OSS对象Key
     * @param ossUploadId OSS MultipartUpload ID
     * @param partNumber  分片编号（从1开始）
     * @param inputStream 分片数据流
     * @param partSize    分片大小（字节）
     * @return 分片的ETag值
     */
    String uploadPart(String objectKey, String ossUploadId, int partNumber, InputStream inputStream, long partSize);

    String completeChunk(String objectKey, String ossUploadId, List<PartETag> partETag);

    void cancelUpload(String ossUploadId, String key);
}
