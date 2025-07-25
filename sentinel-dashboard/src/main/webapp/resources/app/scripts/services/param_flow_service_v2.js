var app = angular.module('sentinelDashboardApp');

/**
 * Service for V2 Parameter Flow Rule, for Nacos persistence. (ES5-compatible)
 */
app.service('ParamFlowServiceV2', ['$http', function ($http) {

    // Query rules for the application from Nacos
    this.queryMachineRules = function(app, ip, port) {
        var param = {
            app: app,
            ip: ip,
            port: port
        };
        return $http({
            url: '/v2/paramFlow/rules',
            params: param,
            method: 'GET'
        });
    };


    // Add a new rule to Nacos
    this.addNewRule = function (rule) {
        return $http.post('/v2/paramFlow/rule', rule);
    };

    // Save an existing rule to Nacos
    this.saveRule = function (rule) {
        return $http.put('/v2/paramFlow/rule/' + rule.id, rule);
    };

    // Delete a rule from Nacos
    this.deleteRule = function (rule) {
        return $http.delete('/v2/paramFlow/rule/' + rule.id);
    };

    // Client-side validation
    this.checkRuleValid = function (rule) {
        if (!rule.resource || rule.resource.trim() === '') {
            alert('资源名称不能为空');
            return false;
        }
        if (rule.grade !== 1) {
            alert('热点规则目前只支持QPS模式');
            return false;
        }
        if (rule.count === undefined || rule.count < 0) {
            alert('限流阈值必须大于等于0');
            return false;
        }
        if (rule.paramIdx === undefined || rule.paramIdx < 0) {
            alert('参数索引必须大于等于0');
            return false;
        }
        if (rule.paramFlowItemList) {
            // --- ES5 CONVERSION: Replaced 'let' with 'var' ---
            for (var i = 0; i < rule.paramFlowItemList.length; i++) {
                var item = rule.paramFlowItemList[i];
                if (!item.object || item.object.trim() === '') {
                    alert('例外项的参数值不能为空');
                    return false;
                }
                if (item.count === undefined || item.count < 0) {
                    alert('例外项的限流阈值必须大于等于0');
                    return false;
                }
            }
        }
        return true;
    };
}]);