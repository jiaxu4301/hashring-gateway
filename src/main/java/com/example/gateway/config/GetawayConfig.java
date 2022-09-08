package com.example.gateway.config;

import com.example.gateway.filter.CustomLoadBalancerClientFilter;
import com.example.gateway.rule.IChooseRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.config.LoadBalancerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static com.example.gateway.constant.Constant.*;

/**
 * GetawayConfig
 */
@Configuration
public class GetawayConfig {
    private static final Logger log = LoggerFactory.getLogger(HashRingConfig.class);

    @Autowired

    private DiscoveryClient discoveryClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Bean
    public CustomLoadBalancerClientFilter loadBalancerClientFilter(LoadBalancerClient client,
                                                                   LoadBalancerProperties properties,
                                                                   @Autowired
                                                                   HashRingConfig hashRingConfig,
                                                                   List<IChooseRule> chooseRules) {
        return new CustomLoadBalancerClientFilter(client, properties, hashRingConfig, chooseRules);
    }

    /**
     * 启动getway 初始化HashRingConfig对象
     */
    @Bean
    public HashRingConfig initHashRingConfig() {
        log.info("-------------------初始化HashRingConfig对象-----------------");
        HashRingConfig hashRingConfig = new HashRingConfig();
        //初始化hash环，hash环信息存在redis上，重启gateway不会丢失历史状态
        Map serverMap = redisTemplate.opsForHash().entries(WEBSOCKET_REDIS_KEY);
        Map userMap = redisTemplate.opsForHash().entries(USER_REDIS_KEY);
        hashRingConfig.updateHashRing(serverMap, userMap);
        List<ServiceInstance> instances = discoveryClient.getInstances(WS_APP_NAME);
        hashRingConfig.setInstances(instances);
        //增加虚拟节点
        hashRingConfig.addVirtualNode(hashRingConfig.getHashRing());
        return hashRingConfig;
    }

}
