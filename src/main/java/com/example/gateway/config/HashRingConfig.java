package com.example.gateway.config;

import com.example.gateway.entiy.HashRingEntity;
import com.example.gateway.model.ResetUser;
import com.example.gateway.utils.HashRingUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HashRingConfig
 **/
@Data
@Slf4j
public class HashRingConfig {

    //真实结点需要对应的虚拟节点个数
    private static final int VIRTUAL_NODES = 100;

    private HashRingEntity hashRing;

    private List<ServiceInstance> instances;

    private List<ServiceInstance> lastTimeInstances;

    /**
     * 修改hash环，将上次的节点hash值赋值给 lastTimeServerMap 保存
     * 把redis上存储的hash环信息，初始化到本地
     */
    public void updateHashRing(Map<String, String> serverMap, Map<String, String> userMap) {
        HashRingEntity hashRingEntity = new HashRingEntity();
        SortedMap<Integer, String> serverSortedMap = new TreeMap<>();
        SortedMap<Integer, String> userSortedMap = new TreeMap<>();
        for (String key : serverMap.keySet()) {
            serverSortedMap.put(Integer.parseInt(key), serverMap.get(key));
        }
        for (String key : userMap.keySet()) {
            userSortedMap.put(Integer.parseInt(key), userMap.get(key));
        }
        hashRingEntity.setLastTimeServerMap(Objects.nonNull(hashRing) ? hashRing.getServerMap() : null);
        hashRingEntity.setServerMap(serverSortedMap);
        hashRingEntity.setUserMap(userSortedMap);
        this.hashRing = hashRingEntity;
    }

    public HashRingEntity getHashRing() {
        return this.hashRing;
    }

    /**
     * 计算userId的hash值求得需要路由到的节点
     */
    public ServiceInstance getServer(String userId, SortedMap<Integer, String> serverMap) {
        if (userId == null) {
            return null;
        }
        int userHash = HashRingUtil.getHash(userId);
//        SortedMap<Integer, String> serverMap = hashRing.getServerMap();
        // 遍历 有序Map serverHash从小到大  如果 serverHash  大于 userHash  则被视为第一个大于userHash的 hash值,
        // 第一个大于userHash 的 节点hash 就是需要路由到的节点如果是虚拟节点需解析获得真实节点。
        for (Integer serverHash : serverMap.keySet()) {
            if (serverHash > userHash) {
                ServiceInstance serviceInstance = getServiceInstance(serverMap, serverHash);
                log.info("用户{}: hash:{},在serverMap有序SortedMap(hash环)上距离节点 hash:{} 最近", userId, userHash, serverHash);
                return serviceInstance;
            }
        }
        // 遍历完发现 userHash最大, 则路由到 serverHash节点环的第一个节点。
        Integer serverHash = serverMap.firstKey();
        log.info("用户{}: hash:{},在serverMap有序SortedMap(hash环)上自己最大则指向serverMap的最小值即为第一个节点hash:{}", userId, userHash, serverHash);
        return getServiceInstance(serverMap, serverHash);
    }

    /**
     * 解析虚拟节点对应的真实节点，匹配对应的 instance 返回
     */
    private ServiceInstance getServiceInstance(SortedMap<Integer, String> serverMap, Integer serverHash) {
        String node = serverMap.get(serverHash);
        String server;
        if (node.contains(("&&"))) {
            //是虚拟节点
            server = node.substring(0, node.indexOf("&&"));
        } else {
            server = node;
        }
        String host = server.substring(0, server.indexOf(":"));
        String port = server.substring(server.indexOf(":") + 1);
        for (ServiceInstance instance : instances) {
            if (instance.getHost().equals(host) && instance.getPort() == Integer.parseInt(port)) {
                log.info("通过解析最近的节点hash:{}，得到节点:{},路由到websocket实例:{}", serverHash, node, instance.getUri());
                return instance;
            }
        }
        return null;
    }

    /**
     * 加入虚拟节点
     * 给每个真实节点尾部拼上&&VNi
     */
    public void addVirtualNode(HashRingEntity hashRing) {
        SortedMap<Integer, String> serverMap = hashRing.getServerMap();
        SortedMap<Integer, String> virtualServerMap = new TreeMap<>();
        for (String realNode : serverMap.values()) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                String virtualNode = realNode + "&&VN" + i;
                int virtualNodeHash = HashRingUtil.getHash(virtualNode);
                virtualServerMap.put(virtualNodeHash, virtualNode);
            }
        }
        serverMap.putAll(virtualServerMap);
        hashRing.setServerMap(serverMap);
        this.hashRing = hashRing;
    }

    /**
     * 增加真实节点后重置一部分user的连接
     */
    public List<ResetUser> getResetUserList() {
        if (instances.size() < lastTimeInstances.size()) {
            //没有新增
            return null;
        }

        //需要重置的用户
        List<ResetUser> resetUsers = new ArrayList<>();

        //本次的服务节点
        List<String> nowServer = instances.stream().map(instance -> instance.getHost() + ":" + instance.getPort()).collect(Collectors.toList());

        //原来的服务节点
        List<String> lastServer = lastTimeInstances.stream().map(instance -> instance.getHost() + ":" + instance.getPort()).collect(Collectors.toList());

        //新增的服务节点
        List<String> newServer = nowServer.stream().filter(server -> !lastServer.contains(server)).collect(Collectors.toList());

        // 当前在线用户hash集合
        SortedMap<Integer, String> userMap = hashRing.getUserMap();

        // 服务节点hash集合
        SortedMap<Integer, String> serverMap = hashRing.getServerMap();

        //准备用户集合的迭代器
        Iterator<Map.Entry<Integer, String>> it = userMap.entrySet().iterator();

        //外层遍历新增的服务节点(看每个新节点都有哪些用户对应到了)
        for (String server : newServer) {
            //内层遍历用户
            while (it.hasNext()) {
                Map.Entry<Integer, String> user = it.next();
                int userHash = user.getKey();
                String userId = user.getValue();

                //把用户和服务节点hash集合临时放在一起（为了下面做截取）
                serverMap.put(userHash, userId);

                //大于user hash的部分map，截取出该用户之后部分的服务节点
                SortedMap<Integer, String> thanUserMap = serverMap.tailMap(userHash);
                //把刚才临时放入服务节点的用户信息remove掉
                thanUserMap.remove(userHash, userId);

                //SortedMap取出第一个元素(hash最小的那个) 这样就拿到了 用户顺时针方向的第一个服务节点，也就是用户需要对应的节点
                Integer firstKey = thanUserMap.firstKey();
                //对应的服务节点信息（内容是ip:port&VN）
                String value = thanUserMap.get(firstKey);

                //如果该节点是新加进来的（这时候需要通知ws更新映射关系）
                if (value.contains(server)) {
                    log.info("用户{}需要重新进行链接到{}", userId, server);
                    ResetUser resetUser = new ResetUser();
                    resetUser.setUserId(userId);
                    resetUser.setMessageId(UUID.randomUUID().toString());
                    resetUser.setName("用户:" + userId);

                    //用户本次需要对应的服务节点
                    resetUser.setResetLink(getServer(userId, hashRing.getServerMap()).getUri().toString());

                    //用户上次对应的服务节点
                    ServiceInstance original = getServer(userId, hashRing.getLastTimeServerMap());
                    resetUser.setOriginalLink(original.getUri().toString());

                    resetUser.setRoutingKey(original.getHost() + ":" + original.getPort());
                    resetUsers.add(resetUser);
                }
            }
        }
        return resetUsers;
    }

}
