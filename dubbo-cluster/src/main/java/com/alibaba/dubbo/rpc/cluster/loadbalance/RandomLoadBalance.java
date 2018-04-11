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
package com.alibaba.dubbo.rpc.cluster.loadbalance;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import java.util.List;
import java.util.Random;

/**
 * random load balance.
 *
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "random";

    private final Random random = new Random();

    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size(); // invokers总数 Number of invokers
        int totalWeight = 0; // 权重总数 The sum of weights
        boolean sameWeight = true; // 一开始假设每个invoker拥有相同的权重 Every invoker has the same weight?
        for (int i = 0; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation); // 获取每个invoker的权重，方法在AbstractLoadBalance中，默认100，通过参数weight配置
            totalWeight += weight; // 遍历每个invoker的权重并计入总权重 Sum
            if (sameWeight && i > 0
                    && weight != getWeight(invokers.get(i - 1), invocation)) { // i > 0开始，如果i位置的权重不等于 i+1 位置的权重，则sameWeight=false
                sameWeight = false;
            }
        }
        if (totalWeight > 0 && !sameWeight) { // 若总权重大于0且如果存在不同的权重
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offset = random.nextInt(totalWeight); // 按总权重获取随机数
            // Return a invoker based on the random value.
            for (int i = 0; i < length; i++) {
                offset -= getWeight(invokers.get(i), invocation); // 根据随机数和遍历中的invoker的权重相减，如果结果小于0，则确定该invoker为调用点
                if (offset < 0) {
                    return invokers.get(i); // 权重越大，offset -= i的权重 的值 越可能小于零
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(random.nextInt(length)); // 如果所有invokers权重相等，则从中随机选择
    }

}