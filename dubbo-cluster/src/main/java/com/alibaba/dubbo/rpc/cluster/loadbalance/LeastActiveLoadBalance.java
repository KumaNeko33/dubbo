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

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.Random;

/**
 * LeastActiveLoadBalance
 *
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";

    private final Random random = new Random();

    /**
     * 数学分析
     * 假设A,B,C,D节点的最小活跃数分别是1,1,2,3,权重为1,2,3,4.则leastIndexs(该数组是最小活跃数组,因为A,B的活跃数是1,均为最小)数组内容为[A,B].A,B的权重是1和2,
     * 所以调用A的概率为 1/(1+2) = 1/3,B的概率为 2/(1+2) = 2/3
     * 敲黑板划重点
     * 活跃数的变化是在com.alibaba.dubbo.rpc.filter.ActiveLimitFilter中,如果没有配置dubbo:reference的actives属性,默认是调用前活跃数+1,调用结束-1,
     * 鉴于很多人可能没用过这个属性,所以我把文档截图贴出来
     * 另外如果使用该种负载均衡算法,则dubbo:service中还需要配置filter="activelimit"
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size(); // Number of invokers
        int leastActive = -1; // The least active value of all invokers
        int leastCount = 0; // The number of invokers having the same least active value (leastActive)
        int[] leastIndexs = new int[length]; // The index of invokers having the same least active value (leastActive)
        int totalWeight = 0; // The sum of weights
        int firstWeight = 0; // Initial value, used for comparision
        boolean sameWeight = true; // Every invoker has the same weight value?
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive(); // 活跃数 Active number
            int weight = invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.WEIGHT_KEY, Constants.DEFAULT_WEIGHT); // 权重 Weight
            if (leastActive == -1 || active < leastActive) { // Restart, when find a invoker having smaller least active value.
                leastActive = active; // 一开始最小活跃数leastActive赋值为第0个invoker的活跃数active，之后遍历invokers过程中赋值为比之前leastActive小的invoker的活跃数active  Record the current least active value
                leastCount = 1; // Reset leastCount, count again based on current leastCount
                leastIndexs[0] = i; // Reset
                totalWeight = weight; // Reset
                firstWeight = weight; // Record the weight the first invoker
                sameWeight = true; // Reset, every invoker has the same weight value?
            } else if (active == leastActive) { // 存在相同最小活跃数的两个invoker If current invoker's active value equals with leaseActive, then accumulating.
                leastIndexs[leastCount++] = i; // 最小活跃数个数统计leastCount++加一  Record index number of this invoker
                totalWeight += weight; // Add this invoker's weight to totalWeight.
                // If every invoker has the same weight?
                if (sameWeight && i > 0
                        && weight != firstWeight) {
                    sameWeight = false; // 拥有相同最小活跃数的两个invoker的权重 不同
                }
            }
        }
        // assert(leastCount > 0)
        if (leastCount == 1) { // 只有一个最小活跃数，直接调用
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexs[0]);
        }
        if (!sameWeight && totalWeight > 0) { // 拥有最小活跃数的invokers的权重不等且总权重大于0，按权重比例调用
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on totalWeight.
            int offsetWeight = random.nextInt(totalWeight); // 根据总权重数获取随机数，下面跟RandomLoadBalance的随机算法类似
            // Return a invoker based on the random value.
            for (int i = 0; i < leastCount; i++) { // 遍历拥有最小活跃数的invokers的下标
                int leastIndex = leastIndexs[i];
                offsetWeight -= getWeight(invokers.get(leastIndex), invocation); // 遍历invokers，用随机数减去权重，出现小于0的invoker则直接返回这个invoker
                if (offsetWeight <= 0)
                    return invokers.get(leastIndex);
            }
        }
        // 权重相等，则均等随机调用 If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(leastIndexs[random.nextInt(leastCount)]);
    }
}