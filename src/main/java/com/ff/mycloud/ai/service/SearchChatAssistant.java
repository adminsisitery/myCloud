package com.ff.mycloud.ai.service;

import com.ff.mycloud.ai.model.entity.QueryData;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface SearchChatAssistant {
    @SystemMessage("""
               你是查询条件提取助手。
                         任务：从用户的自然语言中提取文件检索条件，严格输出JSON字符串。
                         约束：
                         1. 只返回纯粹JSON，不要```json标记、不要解释、不要额外文字；
                         2. 用户没有提到的条件，字段值设置为null；
                         3. 时间统一输出格式：yyyy-MM-dd；'上周'=7天前到今天，'上个月'=30天前到今天；
                         4. 大小单位换算：1MB=1048576，1GB=1073741824，'大于X'填minSize，'小于X'填maxSize；
                         5. fileType填写文件后缀小写，常见映射：图片=jpg，视频=mp4，文档=pdf，表格=xlsx；
                         6. fileName为用户描述的文件名称关键词，不是完整路径，无关键词则为null。
                        \s
                         JSON结构：
                         {
                             "fileName": "文件名关键词|null",
                             "dateFrom": "开始日期yyyy-MM-dd|null",
                             "dateTo": "结束日期yyyy-MM-dd|null",
                             "minSize": 最小字节数|null,
                             "maxSize": 最大字节数|null,
                             "fileType": "文件类型后缀小写|null"
                         }
                         注意：必须是{...}格式，前后不允许有多余格式，里面必须满足JSON语法。
            """)
    @UserMessage("{{msg}}")
    QueryData chat (String msg);
}
