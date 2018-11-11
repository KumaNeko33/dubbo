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
package com.alibaba.dubbo.rpc;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.SPI;

/**
 * Protocol. (API/SPI, Singleton, ThreadSafe)
 */
@SPI("dubbo") //指定一个接口为SPI接口（可扩展接口）
public interface Protocol {

    /**
     * 获取缺省端口，当前用户没有配置端口时使用。Get default port when user doesn't config the port.
     *
     * @return default port 缺省端口
     */
    int getDefaultPort();

    /**
     * 暴露远程服务
     * Export service for remote invocation: <br>
     * 1. 协议在接收请求时，应记录请求来源方地址信息 Protocol should record request source address after receive a request:
     * RpcContext.getContext().setRemoteAddress();<br>
     * 2. export()必须是幂等的，也就是暴露同一个URL的Invoker两次，和暴露一次没有区别。
     * export() must be idempotent, that is, there's no difference between invoking once and invoking twice when
     * export the same URL<br>
     * 3. export()传入的Invoker实例由框架实现并传递，协议无需关系
     * Invoker instance is passed in by the framework, protocol needs not to care <br>
     *
     * @param <T>     Service type 服务的类型
     * @param invoker Service invoker 服务的执行体
     * @return exporter 暴露服务的引用，用于取消暴露 reference for exported service, useful for unexport the service later
     * @throws RpcException 当暴露服务出错时抛出，比如端口已被占用 thrown when error occurs during export the service, for example: port is occupied
     */
    @Adaptive //接口上：例如AdaptiveExtensionFactory（该类不是工厂类，有特殊的逻辑）  AdaptiveCompiler（实际上也是工厂类，但是不能靠动态生成，否则会形成死循环）
//    接口的方法上：会动态生成相应的动态类（实际上是一个工厂类，工厂设计模式），例如Protocol$Adapter
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    /**
     * 引用远程服务：<br>
     * 1. 当用户调用refer()所返回的Invoker对象的invoke()方法时，协议需相应执行同URL远端export()传入的Invoker对象的invoke()方法。<br>
     * 2. refer()返回的Invoker由协议实现，协议通常需要在此Invoker中发送远程请求。<br>
     * 3. 当url中有设置check=false时，连接失败不能抛出异常，并内部自动恢复。<br>
     *
     * @param <T>  服务的类型
     * @param type 服务的类型
     * @param url  远程服务的URL地址
     * @return invoker 服务的本地代理
     * @throws RpcException 当连接服务提供方失败时抛出
     */
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

    /**
     * 释放协议：<br>
     * 1. 取消该协议所有已经暴露和引用的服务。<br>
     * 2. 释放协议所占用的所有资源，比如连接和端口。<br>
     * 3. 协议在释放后，依然能暴露和引用新的服务。<br>
     */
    void destroy();

}