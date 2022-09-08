package com.example.gateway.rule;

import com.example.gateway.config.HashRingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.List;

/**
 * 解析websocket连接一致性hash路由到匹配的节点
 **/
@Component
@Slf4j
public class ConsistencyChooseRule implements IChooseRule {
    /**
     * 重写choose方法 传入HashRingConfig对象
     */
    @Override
    public ServiceInstance choose(ServerWebExchange exchange, HashRingConfig hashRingConfig) {
        URI originalUrl = (URI) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String instancesId = originalUrl.getHost();
        if (instancesId.equals("WS-CLIENT")) {
            try {
                log.info("解析转发url:{}", exchange.getRequest().getURI());
                String userId = exchange.getRequest().getQueryParams().get("userId").stream().findFirst().orElse(null);
                return hashRingConfig.getServer(userId, hashRingConfig.getHashRing().getServerMap());
            } catch (Exception e) {
                log.info("解析转发url异常", e);
            }
        }
        return null;
    }
}
