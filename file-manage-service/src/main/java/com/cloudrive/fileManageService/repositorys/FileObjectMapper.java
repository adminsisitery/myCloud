package com.cloudrive.fileManageService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileManageService.model.entity.FileObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 文件对象 Mapper 接口
 * <p>
 * 提供文件对象的基础 CRUD 操作，并支持按哈希创建/增加引用计数。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    /**
     * 创建文件对象记录（哈希冲突时引用计数 +1）
     *
     * @param fileObject 文件对象实体
     */
    @Insert("INSERT INTO t_file_object " +
            "(sha256_hash, file_size, storage_path, file_type, file_suffix, status, ref_count, created_at, updated_at) " +
            "VALUES " +
            "(#{sha256Hash}, #{fileSize}, #{storagePath}, #{fileType}, #{fileSuffix}, #{status}, #{refCount}, #{createdAt}, #{updatedAt}) " +
            "ON DUPLICATE KEY UPDATE ref_count = ref_count + 1, updated_at = #{updatedAt}")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createFileObject(FileObject fileObject);
}
