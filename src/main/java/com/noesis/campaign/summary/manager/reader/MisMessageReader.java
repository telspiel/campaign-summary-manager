package com.noesis.campaign.summary.manager.reader;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noesis.domain.persistence.NgMisMessage;
import com.noesis.domain.persistence.NgUser;
import com.noesis.domain.service.MisService;
import com.noesis.domain.service.UserService;


public class MisMessageReader{

	private static final Logger logger = LogManager.getLogger(MisMessageReader.class);

	private int maxPollRecordSize;
	
	private CountDownLatch latch = new CountDownLatch(maxPollRecordSize);
	
	public MisMessageReader(int maxPollRecordSize) {
		this.maxPollRecordSize = maxPollRecordSize;
	}

	public CountDownLatch getLatch() {
		return latch;
	}
	
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
    MisService misService;
	
	@Autowired
	private UserService userService;
	
	
	@Value("${app.name}")
	private String appName;
	
	@Value("${kafka.misreader.sleep.interval.ms}")
	private String misReaderSleepInterval;
	
	@Autowired 
	@Qualifier("redisTemplateForSummary")
	private RedisTemplate<String, Integer> redisTemplateForSummary;
	
	@Value("${mis.summary.expiry.seconds}")
	private String misSummaryExpirySeconds;
	
	/*@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateSubmittedSummary;
	
	@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateRejectedSummary;
	*/
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	
	@KafkaListener(id = "mis-"+"${app.name}", topics = "${kafka.topic.name.mis.object}")
	  public void receive(List<String> messageList,
	      @Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
	      @Header(KafkaHeaders.OFFSET) List<Long> offsets) {

		logger.info("start of MIS Message batch read of size: "+messageList.size());
		for (int i = 0; i < messageList.size(); i++) {
			logger.info("received mis message='{}' with partition-offset='{}'", messageList.get(i),
					partitions.get(i) + "-" + offsets.get(i));
			try{
				NgMisMessage ngMisMessage = convertReceivedJsonMessageIntoMisObject(messageList.get(i));
					if (ngMisMessage != null && ngMisMessage.getMessageSource().toLowerCase().startsWith("webtool")) {
						String dateKey = sdf.format(ngMisMessage.getReceivedTs());
						NgUser user = userService.getUserById(ngMisMessage.getUserId());
						logger.info("mis message received for user is : {}", user.getUserName());
						if(ngMisMessage.getFailedRetryCount() == null || ngMisMessage.getFailedRetryCount() ==0){
							String dateTotalRequestKey = dateKey+":campaigntotalrequest:"+user.getUserName();
							logger.info("increasing campaign summary count for key : {} and user {}",dateTotalRequestKey, user.getUserName());
							redisTemplateForSummary.opsForHash().increment(dateTotalRequestKey,ngMisMessage.getCampaignId().toString(),1);
							if(redisTemplateForSummary.getExpire(dateTotalRequestKey) == null || redisTemplateForSummary.getExpire(dateTotalRequestKey) == -1){
								redisTemplateForSummary.expire(dateTotalRequestKey, Long.parseLong(misSummaryExpirySeconds), TimeUnit.SECONDS);
							}
						}
						if(ngMisMessage.getStatus().equalsIgnoreCase("SUBMITTED") && (ngMisMessage.getFailedRetryCount() == null || ngMisMessage.getFailedRetryCount() ==0 )){
							String dateSubmittedKey = dateKey+":campaignsubmitted:"+user.getUserName();
							logger.info("increasing summary count for key : {} and user {}",dateSubmittedKey, user.getUserName());
							redisTemplateForSummary.opsForHash().increment(dateSubmittedKey,ngMisMessage.getCampaignId().toString(),1);
							if(redisTemplateForSummary.getExpire(dateSubmittedKey) == null || redisTemplateForSummary.getExpire(dateSubmittedKey) == -1){
								redisTemplateForSummary.expire(dateSubmittedKey, Long.parseLong(misSummaryExpirySeconds), TimeUnit.SECONDS);
							}
						}else if(!ngMisMessage.getStatus().equalsIgnoreCase("SUBMITTED") && (ngMisMessage.getFailedRetryCount() == null || ngMisMessage.getFailedRetryCount() ==0 )){
							String dateRejectedKey = dateKey+":campaignrejected:"+user.getUserName();
							logger.info("increasing summary count for key : {} and user {}",dateRejectedKey, user.getUserName());
							redisTemplateForSummary.opsForHash().increment(dateRejectedKey,ngMisMessage.getCampaignId().toString(),1);
							if(redisTemplateForSummary.getExpire(dateRejectedKey) == null || redisTemplateForSummary.getExpire(dateRejectedKey) == -1){
								redisTemplateForSummary.expire(dateRejectedKey, Long.parseLong(misSummaryExpirySeconds), TimeUnit.SECONDS);
							}
						}
					}	
				}catch (Exception e){
					logger.error("Exception occured while processing MIS message. Hence skipping this message: {} "+messageList.get(i));
					e.printStackTrace();
				}
			latch.countDown();
		}
		logger.info("End of received MIS batch.");
	    try {
	    	logger.info("MIS Reader Thread Going To Sleep for "+misReaderSleepInterval + "ms.");
	    	Thread.sleep(Integer.parseInt(misReaderSleepInterval));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	  }
	
	private NgMisMessage convertReceivedJsonMessageIntoMisObject(String misMessageObjectJsonString) {
		NgMisMessage ngMisMessageObject = null;
		try {
			ngMisMessageObject = objectMapper.readValue(misMessageObjectJsonString, NgMisMessage.class);
		} catch (Exception e){
			logger.error("Dont retry this message as error while parsing MIS Message json string: "+misMessageObjectJsonString);
			e.printStackTrace();
		} 
		return ngMisMessageObject;
	}
	
	public static void main(String[] args) {
		Timestamp t = new Timestamp(1632215552394L);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		System.out.println(sdf.format(t));
	}
}