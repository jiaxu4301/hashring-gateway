package com.example.gateway.filter;

import com.example.gateway.config.HashRingConfig;
import com.example.gateway.rule.ConsistencyChooseRule;
import com.example.gateway.rule.IChooseRule;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.config.LoadBalancerProperties;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * 定义自定义过滤器
 **/
public class CustomLoadBalancerClientFilter extends LoadBalancerClientFilter implements BeanPostProcessor {
    private final HashRingConfig hashRingConfig;
    private final List<IChooseRule> chooseRules;

    public CustomLoadBalancerClientFilter(LoadBalancerClient loadBalancer, LoadBalancerProperties properties, HashRingConfig hashRingConfig, List<IChooseRule> chooseRules) {
        super(loadBalancer, properties);
        this.hashRingConfig = hashRingConfig;
        this.chooseRules = chooseRules;
        chooseRules.add(new ConsistencyChooseRule());
    }

    @Override
    protected ServiceInstance choose(ServerWebExchange exchange) {
        if (!CollectionUtils.isEmpty(chooseRules)) {
            for (IChooseRule chooseRule : chooseRules) {
                ServiceInstance choose = chooseRule.choose(exchange, hashRingConfig);
                if (choose != null) {
                    return choose;
                }
            }
        }
        return super.choose(exchange);
    }
}
