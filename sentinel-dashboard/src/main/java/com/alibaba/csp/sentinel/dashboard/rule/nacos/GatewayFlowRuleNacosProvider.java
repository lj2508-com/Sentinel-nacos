package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.GatewayFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关流控规则的 Nacos [规则提供者]
 */
@Component("gatewayFlowRuleNacosProvider")
public class GatewayFlowRuleNacosProvider implements DynamicRuleProvider<List<GatewayFlowRuleEntity>> {

    @Autowired
    private ConfigService configService;

    // 注意：这里注入的 Converter 是我们在 GatewayRuleNacosConfig 中定义的 Decoder Bean
    @Autowired
    private Converter<String, List<GatewayFlowRuleEntity>> converter;

    @Override
    public List<GatewayFlowRuleEntity> getRules(String appName) throws Exception {
        // 构造 Data ID
        String dataId = appName + NacosConfigUtil.GATEWAY_FLOW_DATA_ID_POSTFIX;
        // 从 Nacos 获取配置
        String rules = configService.getConfig(dataId, NacosConfigUtil.GROUP_ID, 3000);

        if (StringUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }
        // 使用注入的 converter 将 JSON 字符串转换为规则列表
        return converter.convert(rules);
    }
}