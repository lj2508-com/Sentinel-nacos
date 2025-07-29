package com.alibaba.csp.sentinel.dashboard.controller.gateway.v2;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.ApiDefinitionEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.ApiPredicateItemEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.gateway.InMemApiDefinitionStore;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Gateway Api Controller V2 for manage gateway api definitions in app level.
 */
@RestController
@RequestMapping(value = "/v2/gateway/api")
public class GatewayApiControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(GatewayApiControllerV2.class);

    @Autowired
    private InMemApiDefinitionStore repository;

    // V2 改造：注入 Nacos 的规则提供者和发布者
    @Autowired
    @Qualifier("gatewayApiNacosProvider")
    private DynamicRuleProvider<List<ApiDefinitionEntity>> ruleProvider;

    @Autowired
    @Qualifier("gatewayApiNacosPublisher")
    private DynamicRulePublisher<List<ApiDefinitionEntity>> rulePublisher;

    @GetMapping("/rules")
    @AuthAction(AuthService.PrivilegeType.READ_RULE)
    public Result<List<ApiDefinitionEntity>> queryApisForApp(@RequestParam String app) {
        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
            List<ApiDefinitionEntity> apis = ruleProvider.getRules(app);
            repository.saveAll(apis);
            return Result.ofSuccess(apis);
        } catch (Throwable throwable) {
            logger.error("queryApis error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }

    private <R> Result<R> checkEntityInternal(ApiDefinitionEntity entity) {
        if (entity == null) {
            return Result.ofFail(-1, "invalid body");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getApiName())) {
            return Result.ofFail(-1, "apiName can't be null or empty");
        }
        Set<ApiPredicateItemEntity> predicateItems = entity.getPredicateItems();
        if (CollectionUtils.isEmpty(predicateItems)) {
            return Result.ofFail(-1, "predicateItems can't be empty");
        }
        for (ApiPredicateItemEntity item : predicateItems) {
            if (StringUtil.isBlank(item.getPattern())) {
                return Result.ofFail(-1, "pattern can't be null or empty");
            }
        }
        return null;
    }

    @PostMapping("/rule")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<ApiDefinitionEntity> addApi(@RequestBody ApiDefinitionEntity entity) {
        Result<ApiDefinitionEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

        String apiName = entity.getApiName().trim();
        // 检查API名称不能重复
        List<ApiDefinitionEntity> allApis = repository.findAllByApp(entity.getApp());
        if (allApis.stream().map(o -> o.getApiName()).anyMatch(o -> o.equals(apiName))) {
            return Result.ofFail(-1, "apiName exists: " + entity.getApiName());
        }

        entity.setId(null);
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);

        try {
            entity = repository.save(entity);
            publishApis(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("add gateway api error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }

        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<ApiDefinitionEntity> updateApi(@PathVariable("id") Long id, @RequestBody ApiDefinitionEntity entity) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        Result<ApiDefinitionEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

        ApiDefinitionEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "api does not exist, id=" + id);
        }

        entity.setId(id);
        Date date = new Date();
        entity.setGmtCreate(oldEntity.getGmtCreate());
        entity.setGmtModified(date);

        try {
            entity = repository.save(entity);
            publishApis(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("update gateway api error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }

        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.DELETE_RULE)
    public Result<Long> deleteApi(@PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }

        ApiDefinitionEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofSuccess(null);
        }

        try {
            repository.delete(id);
            publishApis(oldEntity.getApp());
        } catch (Throwable throwable) {
            logger.error("delete gateway api error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }

        return Result.ofSuccess(id);
    }

    private void publishApis(String app) throws Exception {
        List<ApiDefinitionEntity> apis = repository.findAllByApp(app);
        rulePublisher.publish(app, apis);
    }
}