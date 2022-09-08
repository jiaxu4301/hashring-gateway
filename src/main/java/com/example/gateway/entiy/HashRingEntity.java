package com.example.gateway.entiy;

import lombok.Data;

import java.util.SortedMap;

/**
 * 哈希环本环
 **/
@Data
public class HashRingEntity {
    // 服务节点hash集合
    // serverHash -> ip:port&&VN1
    private SortedMap<Integer, String> serverMap;
    // 当前在线用户hash集合
    private SortedMap<Integer, String> userMap;
    // 上次服务节点hash集合
    private SortedMap<Integer, String> lastTimeServerMap;
}
