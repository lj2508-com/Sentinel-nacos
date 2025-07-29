package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.GatewayFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关流控规则的 Nacos [规则发布者]
 */
@Component("gatewayFlowRuleNacosPublisher")
public class GatewayFlowRuleNacosPublisher implements DynamicRulePublisher<List<GatewayFlowRuleEntity>> {

    @Autowired
    private ConfigService configService;

    // 注意：这里注入的 Converter 是我们在 GatewayRuleNacosConfig 中定义的 Encoder Bean
    @Autowired
    private Converter<List<GatewayFlowRuleEntity>, String> converter;

    @Override
    public void publish(String app, List<GatewayFlowRuleEntity> rules) throws Exception {
        if (rules == null) {
            return;
        }
        // 构造 Data ID
        String dataId = app + NacosConfigUtil.GATEWAY_FLOW_DATA_ID_POSTFIX;
        // 使用注入的 converter 将规则列表转换为 JSON 字符串，并发布到 Nacos
        configService.publishConfig(dataId, NacosConfigUtil.GROUP_ID, converter.convert(rules));
    }
}