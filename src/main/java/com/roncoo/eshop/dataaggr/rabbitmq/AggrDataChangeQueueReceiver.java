package com.roncoo.eshop.dataaggr.rabbitmq;

import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * 数据聚合服务
 * （1）接收到数据变更的消息
*（2）按照维度从redis中获取数据，聚合成一个维度数据，写入redis中维度聚合数据
 * @author dream
 *
 */
@Component  
@RabbitListener(queues = "aggr-data-change-queue")  
public class AggrDataChangeQueueReceiver {
	@Autowired
	private JedisPool jedisPool;
	
	@RabbitHandler  
    public void process(String message) {  
    	JSONObject messageJSONObject = JSONObject.parseObject(message);
    	
    	String dimType = messageJSONObject.getString("dim_type");  
    	
    	if("brand".equals(dimType)) {
    		processBrandDimDataChange(messageJSONObject); 
    	} else if("category".equals(dimType)) {
    		processCategoryDimDataChange(messageJSONObject); 
    	} else if("product_intro".equals(dimType)) {
    		processProductIntroDimDataChange(messageJSONObject); 
    	} else if("product".equals(dimType)) {
    		processProductDimDataChange(messageJSONObject); 
    	}
    }

	private void processBrandDimDataChange(JSONObject messageJSONObject) {
		Long id=messageJSONObject.getLong("id");
		
		Jedis jedis = jedisPool.getResource();
		
		String dataJSON = jedis.get("brand_" + id);
    	
    	if(dataJSON != null && !"".equals(dataJSON)) {
    		jedis.set("dim_brand_" + id, dataJSON);
    	} else {
    		jedis.del("dim_brand_" + id);
    	}
	} 
	
	 private void processCategoryDimDataChange(JSONObject messageJSONObject) {
	    	Long id = messageJSONObject.getLong("id");  
	    	
	    	Jedis jedis = jedisPool.getResource();
	    	
	    	// 多此一举，看一下，查出来一个品牌数据，然后直接就原样写redis
	    	// 实际上是这样子的，我们这里是简化了数据结构和业务，实际上任何一个维度数据都不可能只有一个原子数据
	    	// 品牌数据，肯定是结构多变的，结构比较复杂，有很多不同的表，不同的原子数据
	    	// 实际上这里肯定是要将一个品牌对应的多个原子数据都从redis查询出来，然后聚合之后写入redis
	    	String dataJSON = jedis.get("category_" + id);
	    	
	    	if(dataJSON != null && !"".equals(dataJSON)) {
	    		jedis.set("dim_category_" + id, dataJSON);
	    	} else {
	    		jedis.del("dim_category_" + id);
	    	}
	    }
	 private void processProductIntroDimDataChange(JSONObject messageJSONObject) {
    	Long id = messageJSONObject.getLong("id");  
    	
    	Jedis jedis = jedisPool.getResource();
    	
    	String dataJSON = jedis.get("product_intro_" + id);
    	
    	if(dataJSON != null && !"".equals(dataJSON)) {
    		jedis.set("dim_product_intro_" + id, dataJSON);
    	} else {
    		jedis.del("dim_product_intro_" + id);
    	}
    }
	 
	 private void processProductDimDataChange(JSONObject messageJSONObject) {
    	Long id = messageJSONObject.getLong("id");  
    	
    	Jedis jedis = jedisPool.getResource();
    	//从redis批量获取mget,在线上部署先不用
//    	List<String> results = jedis.mget("product_" + id, "product_property_" + id, "product_specification_" + id);
//    	String productDataJSON = results.get(0);
    	String productDataJSON = jedis.get("product_" + id);
    	if(productDataJSON != null && !"".equals(productDataJSON)) {
    		JSONObject productDataJSONObject = JSONObject.parseObject(productDataJSON);
    		String productPropertyDataJSON = jedis.get("product_property_" + id);
//    		String productPropertyDataJSON =  results.get(1);
    		if(productPropertyDataJSON != null && !"".equals(productPropertyDataJSON)) {
    			productDataJSONObject.put("product_property", JSONObject.parse(productPropertyDataJSON));
    		} 
    		String productSpecificationDataJSON = jedis.get("product_specification_" + id);
//    		String productSpecificationDataJSON = results.get(2);;
    		if(productSpecificationDataJSON != null && !"".equals(productSpecificationDataJSON)) {
    			productDataJSONObject.put("product_specification", JSONObject.parse(productSpecificationDataJSON));
    		}
    		
    		jedis.set("dim_product_" + id, productDataJSONObject.toJSONString());
    	} else {
    		jedis.del("dim_product_" + id);
    	}
    }
}
