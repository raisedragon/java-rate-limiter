/**
 * Copyright (c) 2013-2020, cpthack 成佩涛 (cpt@jianzhimao.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cpthack.commons.ratelimiter.limiter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cpthack.commons.ratelimiter.bean.LimiterBean;
import com.github.cpthack.commons.ratelimiter.config.RateLimiterConfig;

import redis.clients.jedis.Jedis;

/**
 * <b>DistributedLimiter.java</b></br>
 * 
 * <pre>
 * 限流接口 - 分布式实现
 * </pre>
 *
 * @author cpthack cpt@jianzhimao.com
 * @date May 16, 2017 6:03:13 PM
 * @since JDK 1.7
 */
public class DistributedLimiter implements Limiter {
	
	private final static Logger				logger		   = LoggerFactory.getLogger(DistributedLimiter.class);
	
	@SuppressWarnings("rawtypes")
	private static Jedis				redisClient	   = null;
	private static Map<String, LimiterBean>	limiterBeanMap = null;
	
	public DistributedLimiter() {
		this(null);
	}
	
	
	public DistributedLimiter(RateLimiterConfig rateLimiterConfig) {
		if (null == rateLimiterConfig) {
			rateLimiterConfig = new RateLimiterConfig();
		}
		initLimiterConfig(rateLimiterConfig);
	}
	
	/**
	 * 
	 * <b>initLimiterConfig</b> <br/>
	 * <br/>
	 * 
	 * 初始化限流配置<br/>
	 * 
	 * @author cpthack cpt@jianzhimao.com
	 * @param rateLimiterConfig
	 *            限流配置类
	 * @param redisConfig
	 *            缓存配置类
	 *
	 */
	protected void initLimiterConfig(RateLimiterConfig rateLimiterConfig) {
		if (null != redisClient)	// 当redisClient不为空，意味着限流配置已经初始化到缓存中
			return;
		
//		redisClient = RedisClientFactory.getClient(redisConfig);
		limiterBeanMap = new HashMap<String, LimiterBean>();
		
		List<LimiterBean> limiterList = rateLimiterConfig.getLimiterList();
		for (LimiterBean limiterBean : limiterList) {
			redisClient.set(limiterBean.getRouter(), "0", "NX", "EX", limiterBean.getTime());
//			redisClient.setnx(limiterBean.getRouter(), "0", limiterBean.getTime());
			limiterBeanMap.put(limiterBean.getRouter(), limiterBean);
			logger.debug("分布式限流-加载限流配置>>>router = [{}],time = [{}],count = [{}]", limiterBean.getRouter(), limiterBean.getTime(), limiterBean.getCount());
		}
	}
	
	@Override
	public boolean execute(String routerName) {
		LimiterBean limiterBean = limiterBeanMap.get(routerName);
		if (null == limiterBean)// 表示没有相关限流配置，直接返回成功
			return true;
		int limiterCount = limiterBean.getCount();
		
		/**
		 * 每次根据路由地址强制设置缓存和过期时间，防止缓存过期后导致限流失效<br>
		 * 倘若已经存在该路由的缓存KEY，不会设置新值
		 */
		redisClient.set(limiterBean.getRouter(), "0", "NX", "EX", limiterBean.getTime());
//		redisClient.setnx(limiterBean.getRouter(), "0", limiterBean.getTime());
		long currentCount = redisClient.incr(routerName);
		
		if (currentCount > limiterCount)// 如果超过限流值，则直接返回false
			return false;
		
		return true;
	}
	
	@Override
	public boolean execute(String routerName, int limitCount) {
		return execute(routerName, limitCount, Integer.MAX_VALUE);
	}
	
	@Override
	public boolean execute(String routerName, int limitCount, int time) {
		LimiterBean limiterBean = limiterBeanMap.get(routerName);
		/**
		 * 此处无需担心并发问题，当多个线程同时进行该模块代码，不影响限流配置。<br/>
		 * limiterBeanMap对象中只会存在一条 [KEY = routerName] 的数据。
		 */
		if (null == limiterBean) {
			limiterBean = new LimiterBean();
			limiterBean.setRouter(routerName);
			limiterBean.setCount(limitCount);
			limiterBean.setTime(time);
			limiterBeanMap.put(routerName, limiterBean);
			logger.debug("分布式限流-动态限流配置>>>router = [{}],time = [{}],count = [{}]", routerName, time, limitCount);
		}
		return execute(routerName);
	}
	
}
