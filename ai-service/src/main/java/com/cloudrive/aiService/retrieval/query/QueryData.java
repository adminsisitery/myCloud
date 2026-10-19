package com.cloudrive.aiService.retrieval.query;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class QueryData {
    @Description("文件名关键词，用于模糊搜索")
    private String fileName;

    @Description("用户描述的开始时间，格式 yyyy-MM-dd，无法识别则为null")
    private String dateFrom;

    @Description("用户描述的结束时间，格式 yyyy-MM-dd，无法识别则为null")
    private String dateTo;

    @Description("最小文件大小（字节），如'大于10MB'则为10485760，未指定则为null")
    private Long minSize;

    @Description("最大文件大小（字节），如'小于1MB'则为1048576，未指定则为null")
    private Long maxSize;

    @Description("文件类型后缀小写，如 pdf、docx、mp4，未指定则为null")
    private String fileType;

}
