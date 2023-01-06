package com.wxy.rpc.core.discovery.zk;

import com.wxy.rpc.core.common.ServiceInfo;
import com.wxy.rpc.core.discovery.ServiceDiscovery;
import com.wxy.rpc.core.loadbalance.LoadBalance;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceCache;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.apache.curator.x.discovery.details.ServiceCacheListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Zookeeper 实现服务发现实现类
 *
 * @author Wuxy
 * @version 1.0
 * @ClassName ZookeeperServiceDiscovery
 * @Date 2023/1/5 21:07
 * @see org.apache.curator.framework.CuratorFramework
 * @see org.apache.curator.x.discovery.ServiceDiscovery
 * @see org.apache.curator.x.discovery.ServiceCache
 */
@Slf4j
public class ZookeeperServiceDiscovery implements ServiceDiscovery {
    private static final int SESSION_TIMEOUT = 60 * 1000;

    private static final int CONNECT_TIMEOUT = 15 * 1000;

    private static final int BASE_SLEEP_TIME = 3 * 1000;

    private static final int MAX_RETRY = 10;

    private static final String BASE_PATH = "/wxy_rpc";

    private LoadBalance loadBalance;

    private CuratorFramework client;

    private org.apache.curator.x.discovery.ServiceDiscovery<ServiceInfo> serviceDiscovery;

    /**
     * 服务本地缓存，将服务缓存到本地并增加 watch 事件，当远程服务发生改变时自动更新服务缓存
     */
    private final Map<String, ServiceCache<ServiceInfo>> serviceCacheMap = new ConcurrentHashMap<>();


    /**
     * 构造方法，传入 zk 的连接地址，如：127.0.0.1:2181
     *
     * @param registryAddress zookeeper 的连接地址
     */
    public ZookeeperServiceDiscovery(String registryAddress, LoadBalance loadBalance) {
        try {
            this.loadBalance = loadBalance;

            // 创建zk客户端示例
            client = CuratorFrameworkFactory
                    .newClient(registryAddress, SESSION_TIMEOUT, CONNECT_TIMEOUT,
                            new ExponentialBackoffRetry(BASE_SLEEP_TIME, MAX_RETRY));
            // 开启客户端通信
            client.start();

            // 构建 ServiceDiscovery 服务注册中心
            serviceDiscovery = ServiceDiscoveryBuilder.builder(ServiceInfo.class)
                    .client(client)
                    .serializer(new JsonInstanceSerializer<>(ServiceInfo.class))
                    .basePath(BASE_PATH)
                    .build();
            // 开启 服务发现
            serviceDiscovery.start();
        } catch (Exception e) {
            log.error("An error occurred while starting the zookeeper discovery: ", e);
        }
    }

    @Override
    public ServiceInfo discover(String serviceName) throws Exception {

        return loadBalance.chooseOne(getServices(serviceName));
    }

    @Override
    public List<ServiceInfo> getServices(String serviceName) throws Exception {
        if (!serviceCacheMap.containsKey(serviceName)) {
            // 构建本地服务缓存
            ServiceCache<ServiceInfo> serviceCache = serviceDiscovery.serviceCacheBuilder()
                    .name(serviceName)
                    .build();
            // 添加服务监听，当服务发生变化时主动更新本地缓存并通知
            serviceCache.addListener(new ServiceCacheListener() {
                @Override
                public void cacheChanged() {
                    log.info("The service [{}] cache has changed. The current number of service samples is {}."
                            , serviceName, serviceCache.getInstances().size());
                }

                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState) {
                    log.info("The client {} connection status has changed. The current status is: {}."
                            , client, newState);
                }
            });
            // 开启服务缓存监听
            serviceCache.start();
            serviceCacheMap.put(serviceName, serviceCache);
        }
        return serviceCacheMap.get(serviceName).getInstances()
                .stream()
                .map(ServiceInstance::getPayload)
                .collect(Collectors.toList());
    }

    @Override
    public void destroy() throws IOException {
        for (ServiceCache<ServiceInfo> serviceCache : serviceCacheMap.values()) {
            if (serviceCache != null) {
                serviceCache.close();
            }
        }
        serviceDiscovery.close();
        client.close();
    }
}
