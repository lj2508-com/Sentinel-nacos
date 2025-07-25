/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import java.util.List;
import java.util.Properties;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.ParamFlowRuleEntity;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eric Zhao
 * @since 1.4.0
 */
@Configuration
public class NacosConfig {


    @Bean
    public Converter<List<FlowRuleEntity>, String> flowRuleEntityEncoder() {
        return JSON::toJSONString;
    }

    @Bean
    public Converter<String, List<FlowRuleEntity>> flowRuleEntityDecoder() {
        return s -> JSON.parseArray(s, FlowRuleEntity.class);
    }

    /**
     * 为 DegradeRuleNacosPublisher 提供服务
     * 将熔断规则列表（List<DegradeRuleEntity>）编码为 JSON 字符串
     */
    @Bean
    public Converter<List<DegradeRuleEntity>, String> degradeRuleEntityEncoder() {
        return JSON::toJSONString;
    }

    /**
     * 为 DegradeRuleNacosProvider 提供服务 (这是解决当前错误的关键)
     * 将 JSON 字符串解码为熔断规则列表（List<DegradeRuleEntity>）
     */
    @Bean
    public Converter<String, List<DegradeRuleEntity>> degradeRuleEntityDecoder() {
        return s -> JSON.parseArray(s, DegradeRuleEntity.class);
    }

    @Bean
    public Converter<List<ParamFlowRuleEntity>, String> paramFlowRuleEntityEncoder() {
        return JSON::toJSONString;
    }

    @Bean
    public Converter<String, List<ParamFlowRuleEntity>> paramFlowRuleEntityDecoder() {
        return s -> JSON.parseArray(s, ParamFlowRuleEntity.class);
    }

    @Bean
    public ConfigService nacosConfigService() throws Exception {
        // 从JVM启动参数中动态获取Nacos的配置
        String serverAddr = System.getProperty("sentinel.nacos.server-addr");
        String namespace = System.getProperty("sentinel.nacos.namespace");
        String username = System.getProperty("sentinel.nacos.username");
        String password = System.getProperty("sentinel.nacos.password");
        String contextPath = System.getProperty("sentinel.nacos.context-path");
        Properties properties = new Properties();
        if (serverAddr != null) {
            properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        }
        if (namespace != null) {
            properties.put(PropertyKeyConst.NAMESPACE, namespace);
        }
        if (username != null) {
            properties.put(PropertyKeyConst.USERNAME, username);
        }
        if (password != null) {
            properties.put(PropertyKeyConst.PASSWORD, password);
        }
        if (contextPath != null) {
            properties.put(PropertyKeyConst.CONTEXT_PATH, contextPath);
        }
        return ConfigFactory.createConfigService(properties);
    }
}
