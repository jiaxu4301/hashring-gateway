package com.example.gateway.config;

import com.example.gateway.model.ResetUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.gateway.constant.Constant.*;

@Service
@Slf4j
public class RedisReceiver {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private HashRingConfig hashRingConfig;
    @Autowired
    private DiscoveryClient discoveryClient;

    /**
     * 监听到websocket 频道的消息,将本次获取到的实例serverMap增加虚拟节点更新hashConfig对象;
     * 将上次节点的服务节点集合更新到lastTimeServerMap;
     * 计算需要重置的user通过resetUserService.resetUserSend 通知对应的websocket 服务实例  执行session.close() 方法断开连接
     * web页面重新连接,重新路由到匹配的节点
     */
    public void receiveMessage(String message) throws Exception {
        log.info("监听到websocket服务实例上下线 ");
        //更新上次服务节点信息
        hashRingConfig.setLastTimeInstances(hashRingConfig.getInstances());

        //更新本次次服务节点信息
        List<ServiceInstance> instances = discoveryClient.getInstances(WS_APP_NAME);
        hashRingConfig.setInstances(instances);

        //更新hash环，hash环信息存在redis上，重启gateway不会丢失历史状态
        Map userMap = redisTemplate.opsForHash().entries(USER_REDIS_KEY);
        Map serverMap = redisTemplate.opsForHash().entries(WEBSOCKET_REDIS_KEY);
        hashRingConfig.updateHashRing(serverMap, userMap);
        hashRingConfig.addVirtualNode(hashRingConfig.getHashRing());

        log.info("本次节点变动-虚拟节点插入完毕 {} ", hashRingConfig.getHashRing().getServerMap());
        log.info("上次节点变动 {} ", hashRingConfig.getHashRing().getLastTimeServerMap());

        //更新因hash环变化而影响到的用户节点信息
        List<ResetUser> resetUserList = hashRingConfig.getResetUserList();
        if (Objects.nonNull(resetUserList)) {
            //todo 收集到需要重置连接的用户和节点信息
            // 通知给ws服务断开用户连接，web页面重新连接,重新路由到匹配的节点（节点减少的情况同理）
        }
    }
}
