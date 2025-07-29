package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.ApiDefinitionEntity;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("gatewayApiNacosPublisher")
public class GatewayApiNacosPublisher implements DynamicRulePublisher<List<ApiDefinitionEntity>> {

    @Autowired
    private ConfigService configService;
    @Autowired
    private Converter<List<ApiDefinitionEntity>, String> converter;

    @Override
    public void publish(String app, List<ApiDefinitionEntity> rules) throws Exception {
        if (rules == null) {
            return;
        }
        String dataId = app + NacosConfigUtil.GATEWAY_API_DATA_ID_POSTFIX;
        configService.publishConfig(dataId, NacosConfigUtil.GROUP_ID, converter.convert(rules));
    }
}