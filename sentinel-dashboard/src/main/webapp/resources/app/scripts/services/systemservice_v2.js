var app = angular.module('sentinelDashboardApp');

app.service('SystemServiceV2', ['$http', function ($http) {

  function buildPayload(rule) {
    var payload = {
      id: rule.id,
      app: rule.app,
      ip: rule.ip,
      port: rule.port,
      grade: rule.grade
    };

    // 根据grade，只添加一个阈值属性，并确保其为数字类型
    switch (rule.grade) {
      case 0: // LOAD
        // V2 最终修正：使用 parseFloat 确保值为数字类型
        payload.highestSystemLoad = parseFloat(rule.highestSystemLoad);
        break;
      case 1: // RT
        payload.avgRt = parseFloat(rule.avgRt);
        break;
      case 2: // 线程数
        payload.maxThread = parseFloat(rule.maxThread);
        break;
      case 3: // QPS
        payload.qps = parseFloat(rule.qps);
        break;
      case 4: // CPU
        payload.highestCpuUsage = parseFloat(rule.highestCpuUsage);
        break;
    }
    return payload;
  }

  this.queryMachineRules = function (app, ip, port) {
    var param = {
      app: app,
      ip: ip,
      port: port
    };
    return $http({
      url: '/v2/system/rules',
      params: param,
      method: 'GET'
    });
  };

  this.newRule = function (rule) {
    var payload = buildPayload(rule);
    return $http({
      url: '/v2/system/rule',
      data: payload,
      method: 'POST'
    });
  };

  this.saveRule = function (rule) {
    var payload = buildPayload(rule);
    return $http({
      url: '/v2/system/rule/' + rule.id,
      data: payload,
      method: 'PUT'
    });
  };

  this.deleteRule = function (rule) {
    return $http({
      url: '/v2/system/rule/' + rule.id,
      method: 'DELETE'
    });
  };

  this.checkRuleValid = function (rule) {
    // ... (checkRuleValid 函数保持不变)
    if (rule.grade === undefined || rule.grade < 0 || rule.grade > 4) {
      return false;
    }
    function notNumberAtLeastZero(num) {
      return num === undefined || num === '' || isNaN(num) || num < 0;
    }

    if (rule.grade == 0) {
      if (notNumberAtLeastZero(rule.highestSystemLoad)) {
        return false;
      }
    } else if (rule.grade == 1) {
      if (notNumberAtLeastZero(rule.avgRt)) {
        return false;
      }
    } else if (rule.grade == 2) {
      if (notNumberAtLeastZero(rule.maxThread)) {
        return false;
      }
    } else if (rule.grade == 3) {
      if (notNumberAtLeastZero(rule.qps)) {
        return false;
      }
    } else if (rule.grade == 4) {
      if (notNumberAtLeastZero(rule.highestCpuUsage)) {
        return false;
      }
      if (rule.highestCpuUsage > 1) {
        return false;
      }
    }
    return true;
  };
}]);