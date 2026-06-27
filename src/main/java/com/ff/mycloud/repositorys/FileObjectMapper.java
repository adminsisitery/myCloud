package com.ff.mycloud.repositorys;

import co.elastic.clients.elasticsearch.xpack.usage.Sql;
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
     * 创建文件对象记录
     * 功能：向数据库插入一条文件对象记录，并自动回填生成的主键ID
     *
     * @param fileObject 文件对象实体，包含文件哈希、大小、存储路径、类型、后缀、状态、引用计数等信息
     */
    @Insert("INSERT INTO t_file_object " +
            "(sha256_hash, file_size, storage_path, file_type, file_suffix, status, ref_count, created_at, updated_at) " +
            "VALUES " +
            "(#{sha256Hash}, #{fileSize}, #{storagePath}, #{fileType}, #{fileSuffix}, #{status}, #{refCount}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id") // 自动回填主键ID
    public void createFileObject(FileObject fileObject);
}
