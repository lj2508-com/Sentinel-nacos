var app = angular.module('sentinelDashboardApp');

// V2 改造：服务名添加V2后缀
app.service('GatewayFlowServiceV2', ['$http', function ($http) {
    /**
     * 查询规则
     * V2 改造: 从查询单机规则 (queryRules) 变为查询应用规则 (queryRulesForApp)
     * 不再需要 ip 和 port
     */
    this.queryRulesForApp = function (app) {
        var param = {
            app: app
        };

        return $http({
            url: '/v2/gateway/flow/rules', // V2 改造：URL 版本化
            params: param,
            method: 'GET'
        });
    };

    /**
     * 新增规则
     * V2 改造：URL 版本化。方法和数据结构已符合V2模式，予以保留。
     */
    this.newRule = function (rule) {
        return $http({
            url: '/v2/gateway/flow/rule', // V2 改造：URL 版本化
            data: rule,
            method: 'POST'
        });
    };

    /**
     * 更新规则
     * V2 改造：方法改为 PUT，URL 改为 RESTful 风格
     */
    this.saveRule = function (rule) {
        return $http({
            url: '/v2/gateway/flow/rule/' + rule.id, // V2 改造：URL 版本化和 RESTful
            data: rule,
            method: 'PUT' // V2 改造：使用 PUT 表示更新
        });
    };

    /**
     * 删除规则
     * V2 改造：方法改为 DELETE，URL 改为 RESTful 风格，移除参数构建
     */
    this.deleteRule = function (rule) {
        return $http({
            url: '/v2/gateway/flow/rule/' + rule.id, // V2 改造：URL 版本化和 RESTful
            method: 'DELETE' // V2 改造：使用 DELETE 表示删除
        });
    };

    /**
     * 校验逻辑保持不变
     */
    this.checkRuleValid = function (rule) {
        if (rule.resource === undefined || rule.resource === '') {
            alert('API名称不能为空');
            return false;
        }

        if (rule.paramItem != null) {
            if (rule.paramItem.parseStrategy == 2 ||
                rule.paramItem.parseStrategy == 3 ||
                rule.paramItem.parseStrategy == 4) {
                if (rule.paramItem.fieldName === undefined || rule.paramItem.fieldName === '') {
                    alert('当参数属性为Header、URL参数、Cookie时，参数名称不能为空');
                    return false;
                }

                if (rule.paramItem.pattern === '') {
                    alert('匹配串不能为空');
                    return false;
                }
            }
        }

        if (rule.count === undefined || rule.count < 0) {
            alert((rule.grade === 1 ? 'QPS阈值' : '线程数') + '必须大于等于 0');
            return false;
        }

        return true;
    };
}]);