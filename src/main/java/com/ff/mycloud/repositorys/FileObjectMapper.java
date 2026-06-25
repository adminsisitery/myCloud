package com.ff.mycloud.repositorys;

import co.elastic.clients.elasticsearch.xpack.usage.Sql;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.FileObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    @Insert("INSERT INTO t_file_object " +
            "(sha256_hash, file_size, storage_path, file_type, file_suffix, status, ref_count, created_at, updated_at) " +
            "VALUES " +
            "(#{sha256Hash}, #{fileSize}, #{storagePath}, #{fileType}, #{fileSuffix}, #{status}, #{refCount}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id") // 自动回填主键ID
    public void createFileObject(FileObject fileObject);
}
