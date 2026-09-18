package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.dto.FastUploadRequest;
import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.FastUploadVO;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.model.vo.FileDownloadVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文件服务接口
 * <p>提供文件的上传、下载、列表、重命名、删除、秒传校验等核心业务功能。</p>
 *
 * @author 飞飞
 * @date 2026/2/14
 */
public interface FileService {
   /**
     * 上传文件
     *
     * @param file     上传的文件
     * @param parentId 父目录ID
     * @return 上传后的文件路径
     */
   String uploadFile(MultipartFile file,Long parentId);

    /**
     * 列出指定目录下的所有文件和文件夹
     *
     * @param parentId 父目录ID，为null时列出根目录
     * @return 文件列表视图对象列表
     */
    List<FileListVO> listFiles(Long parentId);

    /**
     * 从本地文件路径上传文件（支持进度跟踪）
     *
     * @param fileType         文件MIME类型
     * @param absolutePath     文件的本地绝对路径
     * @param originalFilename 原始文件名
     * @param size             文件大小（字节）
     * @param parentId         父目录ID
     * @param taskId           上传任务ID（用于进度跟踪）
     * @param currentUserId    当前用户ID
     */
    void uploadFileWithProgressFromPath(String fileType, String absolutePath, String originalFilename, long size, Long parentId, String taskId, Long currentUserId) throws IOException;

    /**
     * 下载文件
     *
     * @param fileId 文件ID
     * @return 文件的字节数组
     */
    byte[] downloadFile(Long fileId);

    /** 校验文件权限并返回下载所需元数据，不读取文件内容。 */
    FileDownloadVO prepareDownload(Long fileId);

    /**
     * 获取文件名
     *
     * @param fileId 文件ID
     * @return 文件名
     */
    String getFilename(long fileId);

    /**
     * 重命名文件
     *
     * @param fileId      文件ID
     * @param newFilename 新文件名（不能为空，不超过50个字符）
     */
    void renameFile(Long fileId, @NotBlank(message = "新文件名不能为空") @Size(max = 50, message = "新文件名不能超过50个字符") String newFilename);

    /**
     * 移动文件或文件夹到新的父目录
     * <p>更新 parentId 和 localFilePath，文件夹会递归更新所有子节点路径。</p>
     *
     * @param fileId         文件ID
     * @param targetParentId 目标父目录ID，null或0为根目录
     */
    void moveFile(Long fileId, Long targetParentId);

    /**
     * 删除文件或文件夹
     * <p>如果是文件夹则递归删除所有子节点，文件则判断秒传引用后再物理删除。</p>
     *
     * @param fileId 文件ID
     */
    void deleteFile(Long fileId);

    /**
     * 获取文件内容
     *
     * @param id 文件ID
     * @return 文件的字节数组
     */
    byte[] getFileContent(Long id);

    /**
     * 根据存储路径获取文件输入流
     *
     * @param path 文件在存储系统中的路径
     * @return 文件的输入流
     */
    InputStream getFile(String path);

    /**
     * 校验文件哈希值（秒传检测）
     * <p>根据SHA-256哈希值判断文件是否已存在，用于实现秒传功能。</p>
     *
     * @param fileCheck 文件校验参数（包含哈希值和文件名等信息）
     * @return 文件校验结果视图
     */
    Result<FileCheckVO> checkFilesHash(FileCheck fileCheck);

    /**
     * 执行秒传（秒传判定的后半段）
     * <p>
     * 在 {@link #checkFilesHash} 返回 exists=true 之后调用：服务端在目标目录建一条逻辑文件记录，
     * 复用已有的物理对象，不再传输文件字节。本方法会重新校验物理对象确实存在，并保证幂等
     * （目标目录已有同内容同名记录时直接返回，不重复插入）。
     * </p>
     *
     * @param request 秒传请求（文件名、大小、SHA-256、目标目录）
     * @return 落库结果（fileId、最终文件名）
     */
    Result<FastUploadVO> fastUpload(FastUploadRequest request);

    void copyFile(Long fileId);

    /**
     * 收藏文件
     *
     * @param fileId 文件ID
     */
    void starFile(Long fileId);

    /**
     * 取消收藏文件
     *
     * @param fileId 文件ID
     */
    void unstarFile(Long fileId);

    /**
     * 获取当前用户的收藏文件列表
     *
     * @return 收藏的文件列表
     */
    List<FileListVO> listStarredFiles();

    /**
     * 获取最近访问的文件列表
     *
     * @return 最近访问的文件列表 VO
     */
    List<FileListVO> getRecentFiles();

    /**
     * 记录文件访问（下载时调用）
     *
     * @param fileId 文件ID
     */
    void recordAccess(Long fileId);

    /**
     * 文件分类方法
     * @param fileInfo
     * @return
     */
    int categoryFiles(FileInfo fileInfo);

}
