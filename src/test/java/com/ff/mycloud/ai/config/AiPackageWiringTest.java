package com.ff.mycloud.ai.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ff.mycloud.MyCloudApplication;
import com.ff.mycloud.ai.persistence.mapper.AiConversationMapper;
import com.ff.mycloud.ai.persistence.mapper.AiMessageMapper;
import com.ff.mycloud.ai.persistence.mapper.AiUsageLogMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.*;

class AiPackageWiringTest {
    @Test void requestLoggingStillCoversAllAiControllersAndExcludesInternalServices() throws Exception {
        var advice=com.ff.mycloud.common.aspect.RequestLogAspect.class.getMethod("logRequest",org.aspectj.lang.ProceedingJoinPoint.class);
        var pointcut=new org.springframework.aop.aspectj.AspectJExpressionPointcut();
        pointcut.setExpression(advice.getAnnotation(org.aspectj.lang.annotation.Around.class).value());
        for(Class<?> controller:List.of(com.ff.mycloud.ai.chat.ChatController.class,
                com.ff.mycloud.ai.conversation.ConversationController.class,com.ff.mycloud.ai.usage.AiCostController.class,
                com.ff.mycloud.ai.agent.AgentTaskController.class)) {
            for(var method:controller.getDeclaredMethods())
                if(java.lang.reflect.Modifier.isPublic(method.getModifiers())) assertTrue(pointcut.matches(method,controller),method.toString());
        }
        var service=com.ff.mycloud.ai.chat.AiChatServiceImpl.class;
        assertFalse(pointcut.matches(service.getMethod("sendMessage",com.ff.mycloud.ai.chat.dto.ChatRequest.class),service));
    }

    @Test void applicationMapperScanAndXmlNamespacesResolveAndQueryMovedEntities() throws Exception {
        String aiScan=Arrays.stream(MyCloudApplication.class.getAnnotationsByType(MapperScan.class))
                .flatMap(a->Arrays.stream(a.value())).filter(p->p.contains(".ai.")).findFirst().orElseThrow();
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        String schema=Files.readString(Path.of("deploy/001-agent.sql")).split("-- Idempotent upgrade")[0];
        new ResourceDatabasePopulator(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8))).execute(source);
        var jdbc=new JdbcTemplate(source);
        jdbc.update("INSERT INTO t_ai_conversation(id,user_id,title,mode) VALUES(10,1,'package wiring','CHAT')");
        jdbc.update("INSERT INTO t_ai_message(id,conversation_id,role,content) VALUES(20,10,'USER','original message')");
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(new MybatisConfiguration());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/AI/*.xml"));
        SqlSessionFactory sessions=Objects.requireNonNull(factory.getObject());
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean("sqlSessionFactory",SqlSessionFactory.class,()->sessions);
            var scanner=new ClassPathMapperScanner(context);scanner.setSqlSessionFactoryBeanName("sqlSessionFactory");scanner.registerFilters();scanner.scan(aiScan);context.refresh();
            var conversations=context.getBean(AiConversationMapper.class);
            assertEquals("package wiring",conversations.getConversationListByUserId(1L).getFirst().getTitle());
            assertEquals("original message",conversations.selectByCId(10L).getFirst().getContent());
            assertEquals("original message",context.getBean(AiMessageMapper.class).findByMsgId(10L).getFirst().getContent());
            assertNotNull(context.getBean(AiUsageLogMapper.class));
            assertTrue(sessions.getConfiguration().hasStatement(AiUsageLogMapper.class.getName()+".costSummary"));
        }
    }

    @Test void springFindsRelocatedComponentsWithoutDuplicateBeanNames() {
        var scanner=new ClassPathScanningCandidateComponentProvider(true);
        var definitions=scanner.findCandidateComponents("com.ff.mycloud.ai");
        var registry=new SimpleBeanDefinitionRegistry();Set<String> classes=new HashSet<>();Set<String> names=new HashSet<>();
        for(var bean:definitions) {
            assertTrue(names.add(AnnotationBeanNameGenerator.INSTANCE.generateBeanName(bean,registry)),"Duplicate Spring bean: "+bean.getBeanClassName());
            classes.add(bean.getBeanClassName());
        }
        for(String expected:List.of("chat.ChatController","chat.AiChatServiceImpl","conversation.ConversationController",
                "conversation.AiConversationServiceImpl","usage.AiCostController","usage.AiCostServiceImpl","agent.AgentTaskController",
                "agent.AgentOrchestrator","agent.action.AgentActionService","agent.plan.AgentPlanParser","agent.tool.AgentToolFactory",
                "memory.ConversationMemoryService","model.ModelRegistry","observability.AgentHealthIndicator","config.AIConfig"))
            assertTrue(classes.contains("com.ff.mycloud.ai."+expected),expected);
        assertTrue(names.contains("agentSchema"),"Existing readiness health component name must remain compatible");
    }
}
