/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.remoting.zookeeper.support;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractZookeeperClient<TargetChildListener> implements ZookeeperClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractZookeeperClient.class);

    private final URL url;

    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<StateListener>();

    private final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();

    private volatile boolean closed = false;

    public AbstractZookeeperClient(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }
//    问：zookeeper的有哪些节点,他们有什么区别?讲一下应用场景？
    // 创建zookeeper节点，zookeeper中的节点是有生命周期的，具体生命周期取决于节点的类型，节点主要分为持久节点（Persistent)和临时节点(Ephemeral)，还有时序节点（Sequential)
    // 创建节点往往组合使用，所有总共有4种节点，持久节点，持久顺序节点，临时节点，临时顺序节点
    // 持久节点：指在节点创建后，就一直存在，直到有删除操作来主动删除这个节点，就算创建该节点的客户端会话失效这个节点也不会消失
    // 临时节点：指节点的生命周期和客户端会话绑定，如果客户端会话失效，那么这个节点就会被自动清除掉
    public void create(String path, boolean ephemeral) { //ephemeral朝生夕死的
        int i = path.lastIndexOf('/');
        if (i > 0) {
            String parentPath = path.substring(0, i);//parentPath = /dubbo/com.alibaba.dubbo.demo.DemoService/providers
            if (!checkExists(parentPath)) { //调用子类ZkClientZookeeperClient的checkExists判断父节点路径存不存在
                create(parentPath, false);//父节点路径不存在，则创建一个持久节点
            }
        }
        if (ephemeral) { //ephemeral = false
            createEphemeral(path);//创建临时节点，在zookeeper的分布式锁时用到
        } else {
            createPersistent(path);//创建持久节点
        }
    }
    /*
    问：服务提供者能实现失效踢出是什么原理(高频题)？
    答案：在分布式系统中,我们常常需要知道某个机器是否可用,传统的开发中,可以通过Ping某个主机来实现,
    Ping得通说明对方是可用的,相反是不可用的,ZK 中我们让所有的机器都注册一个临时节点,
    我们判断一个机器是否可用,我们只需要判断这个节点在ZK中是否存在就可以了,
    不需要直接去连接需要检查的机器,降低系统的复杂度
     */

    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public Set<StateListener> getSessionListeners() {
        return stateListeners;
    }

    /**
     *  1 根据path从ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners获取ConcurrentMap<ChildListener, TargetChildListener>，没有就创建
     *  2 根据ChildListener获取TargetChildListener，没有就创建，TargetChildListener是真正的监听path的子节点变化的监听器
     *  createTargetChildListener(String path, final ChildListener listener)：创建一个真正的用来执行当path节点的子节点发生变化时的逻辑
     *  3 addTargetChildListener(path, targetListener)：将刚刚创建出来的子节点监听器订阅path的变化，这样之后，path的子节点发生了变化时，TargetChildListener才会执行相应的逻辑。
     *  而实际上TargetChildListener又会调用ChildListener的实现类的childChanged(String parentPath, List<String> currentChilds)方法，而该实现类，正好是ZookeeperRegistry中实现的匿名内部类，
     *  在该匿名内部类的childChanged(String parentPath, List<String> currentChilds)方法中，调用了ZookeeperRegistry.notify(URL url, NotifyListener listener, List<URL> urls)
     * @param path
     * @param listener
     * @return
     */
    public List<String> addChildListener(String path, final ChildListener listener) {
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners == null) {
            childListeners.putIfAbsent(path, new ConcurrentHashMap<ChildListener, TargetChildListener>());
            listeners = childListeners.get(path);
        }
        TargetChildListener targetListener = listeners.get(listener);
        if (targetListener == null) {
            listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
            targetListener = listeners.get(listener);
        }
        return addTargetChildListener(path, targetListener);
    }

    public void removeChildListener(String path, ChildListener listener) {
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners != null) {
            TargetChildListener targetListener = listeners.remove(listener);
            if (targetListener != null) {
                removeTargetChildListener(path, targetListener);
            }
        }
    }

    protected void stateChanged(int state) {
        for (StateListener sessionListener : getSessionListeners()) {
            sessionListener.stateChanged(state);
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            doClose();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    protected abstract void doClose();

    protected abstract void createPersistent(String path);

    protected abstract void createEphemeral(String path);

    protected abstract boolean checkExists(String path);

    protected abstract TargetChildListener createTargetChildListener(String path, ChildListener listener);

    protected abstract List<String> addTargetChildListener(String path, TargetChildListener listener);

    protected abstract void removeTargetChildListener(String path, TargetChildListener listener);

}
