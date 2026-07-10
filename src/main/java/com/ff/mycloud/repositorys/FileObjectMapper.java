package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.FileObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 文件对象Mapper接口
 * 功能描述：提供文件对象的基础CRUD数据访问操作，支持文件对象的创建
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    /**
     * 创建文件对象记录（哈希冲突时引用计数+1）
     * 功能：向数据库插入一条文件对象记录，并自动回填生成的主键ID。
     * 如果 (sha256_hash, file_size) 已存在则递增 ref_count 而非报错。
     *
     * @param fileObject 文件对象实体
     */
    @Insert("INSERT INTO t_file_object " +
            "(sha256_hash, file_size, storage_path, file_type, file_suffix, status, ref_count, created_at, updated_at) " +
            "VALUES " +
            "(#{sha256Hash}, #{fileSize}, #{storagePath}, #{fileType}, #{fileSuffix}, #{status}, #{refCount}, #{createdAt}, #{updatedAt}) " +
            "ON DUPLICATE KEY UPDATE ref_count = ref_count + 1, updated_at = #{updatedAt}")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    public void createFileObject(FileObject fileObject);
}
