package com.noesis.campaign.summary.manager.config;

import java.nio.charset.Charset;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.JedisPoolConfig;


@Configuration
@EnableCaching
public class CacheConfig2 {

	//private static final Logger logger = LogManager.getLogger(CacheConfig.class);
	private final Logger logger = LoggerFactory.getLogger(getClass());
	@Value("${spring.redis.host}")
	private String redisHost;

	@Value("${spring.redis.port}")
	private String redisPort;
	
	@Value("${spring.redis.password}")
	private String redisPassword;

	@Value("${spring.redis.max.total.con}")
	private String redisMaxTotalConnection;

	@Value("${spring.redis.min.idle.con}")
	private String redisMinIdleCon;
	
	@Value("${spring.redis.max.idle.con}")
	private String redisMaxIdleCon;

	/*@Value("${spring.redis.database.index}")
	private String redisDatabaseIndex;*/

	@Bean 
	public JedisConnectionFactory redisConnectionFactory2() {
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(Integer.parseInt(redisMaxTotalConnection));
		poolConfig.setMinIdle(Integer.parseInt(redisMinIdleCon));
		poolConfig.setMaxIdle(Integer.parseInt(redisMaxIdleCon));
		
		JedisConnectionFactory redisConnectionFactory = new JedisConnectionFactory(poolConfig);
		redisConnectionFactory.setHostName(redisHost);
		redisConnectionFactory.setPort(Integer.parseInt(redisPort));
		redisConnectionFactory.setPassword(redisPassword);
		redisConnectionFactory.setPassword("1nAw5@aam");
		//redisConnectionFactory.setDatabase(Integer.parseInt(redisDatabaseIndex));
		return redisConnectionFactory;
	}

	@Bean
	public Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
		final Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(
				Object.class);
		final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
		objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
		objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
		jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
		return jackson2JsonRedisSerializer;
	}
	 
	@Bean(name = "redisTemplateForSummary")
	@Autowired
	public RedisTemplate<String, Integer> redisTemplateForSummary() {
		final RedisTemplate<String, Integer> temp = new RedisTemplate<String, Integer>();
		temp.setKeySerializer(new StringRedisSerializer());
		temp.setValueSerializer(new StringRedisSerializer());
		// add this
		temp.setHashKeySerializer(new StringRedisSerializer(Charset.forName("UTF-8")));
		temp.setHashValueSerializer(new StringRedisSerializer(Charset.forName("UTF-8")));
		temp.setConnectionFactory(redisConnectionFactory2());
		return temp;
	}
}
