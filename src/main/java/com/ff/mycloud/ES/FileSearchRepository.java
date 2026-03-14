//package com.ff.mycloud.ES;
//
//import com.ff.mycloud.model.entity.FileDocument;
//import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
//
//import java.util.List;
//
//public interface FileSearchRepository extends ElasticsearchRepository<FileDocument,Long> {
//    List<FileDocument> findByFileNameContainingAndUserId(
//            String keyword,
//            Long userId
//    );
//
//    void deleteById(Long fileId);
//}
