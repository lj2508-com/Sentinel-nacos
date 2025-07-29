var app = angular.module('sentinelDashboardApp');

// V2 改造：服务名添加V2后缀
app.service('GatewayApiServiceV2', ['$http', function ($http) {
  /**
   * 查询应用的全部 API 分组
   * V2 改造: 从查询单机 (queryApis) 变为查询应用 (queryApisForApp)，不再需要 ip 和 port
   */
  this.queryApisForApp = function (app) {
    var param = {
      app: app
    };
    return $http({
      url: '/v2/gateway/api/rules', // V2 改造：URL 版本化和统一命名
      params: param,
      method: 'GET'
    });
  };

  /**
   * 新增 API 定义
   * V2 改造：URL 版本化，方法名统一
   */
  this.newRule = function (api) {
    return $http({
      url: '/v2/gateway/api/rule', // V2 改造：URL 版本化和统一命名
      data: api,
      method: 'POST'
    });
  };

  /**
   * 更新 API 定义
   * V2 改造：方法改为 PUT，URL 改为 RESTful 风格，方法名统一
   */
  this.saveRule = function (api) {
    return $http({
      url: '/v2/gateway/api/rule/' + api.id, // V2 改造：URL 版本化和 RESTful
      data: api,
      method: 'PUT' // V2 改造：使用 PUT 表示更新
    });
  };

  /**
   * 删除 API 定义
   * V2 改造：方法改为 DELETE，URL 改为 RESTful 风格，移除参数构建，方法名统一
   */
  this.deleteRule = function (api) {
    return $http({
      url: '/v2/gateway/api/rule/' + api.id, // V2 改造：URL 版本化和 RESTful
      method: 'DELETE' // V2 改造：使用 DELETE 表示删除
    });
  };

  /**
   * 校验逻辑保持不变，方法名统一
   */
  this.checkRuleValid = function (api, apiNames) {
    if (api.apiName === undefined || api.apiName === '') {
      alert('API名称不能为空');
      return false;
    }

    if (api.predicateItems == null || api.predicateItems.length === 0) {
      alert('至少有一个匹配规则');
      return false;
    }

    for (var i = 0; i < api.predicateItems.length; i++) {
      var predicateItem = api.predicateItems[i];
      var pattern = predicateItem.pattern;
      if (pattern === undefined || pattern === '') {
        alert('匹配串不能为空，请检查');
        return false;
      }
    }

    if (apiNames.indexOf(api.apiName) !== -1) {
      alert('API名称(' + api.apiName + ')已存在');
      return false;
    }

    return true;
  };
}]);