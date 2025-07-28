/**
 * Authority rule service V2.
 */
// V2 改造：服务名添加V2后缀
angular.module('sentinelDashboardApp').service('AuthorityRuleServiceV2', ['$http', function ($http) {
    /**
     * 查询规则
     * V2 改造：URL路径添加/v2前缀
     */
    this.queryMachineRules = function(app, ip, port) {
        var param = {
            app: app,
            ip: ip,
            port: port
        };
        return $http({
            url: '/v2/authority/rules',
            params: param,
            method: 'GET'
        });
    };

    /**
     * 新增规则
     * V2 改造：URL路径添加/v2前缀
     */
    this.addNewRule = function(rule) {
        return $http({
            url: '/v2/authority/rule',
            data: rule,
            method: 'POST'
        });
    };

    /**
     * 保存(更新)规则
     * V2 改造：URL路径添加/v2前缀
     */
    this.saveRule = function (entity) {
        return $http({
            url: '/v2/authority/rule/' + entity.id,
            data: entity,
            method: 'PUT'
        });
    };

    /**
     * 删除规则
     * V2 改造：URL路径添加/v2前缀
     */
    this.deleteRule = function (entity) {
        return $http({
            url: '/v2/authority/rule/' + entity.id,
            method: 'DELETE'
        });
    };

    /**
     * 校验逻辑保持不变
     */
    this.checkRuleValid = function checkRuleValid(rule) {
        if (rule.resource === undefined || rule.resource === '') {
            alert('资源名称不能为空');
            return false;
        }
        if (rule.limitApp === undefined || rule.limitApp === '') {
            alert('流控针对应用不能为空');
            return false;
        }
        if (rule.strategy === undefined) {
            alert('必须选择黑白名单模式');
            return false;
        }
        return true;
    };
}]);