package com.noesis.campaign.summary.manager.reader;

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
import com.noesis.domain.persistence.NgDlrMessage;
import com.noesis.domain.persistence.NgUser;
import com.noesis.domain.service.DlrMisService;
import com.noesis.domain.service.UserService;


public class MisFinalDlrReader{

	private static final Logger logger = LogManager.getLogger(MisFinalDlrReader.class);

	private int maxPollRecordSize;
	
	private CountDownLatch latch = new CountDownLatch(maxPollRecordSize);
	
	public MisFinalDlrReader(int maxPollRecordSize) {
		this.maxPollRecordSize = maxPollRecordSize;
	}

	public CountDownLatch getLatch() {
		return latch;
	}
	
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
    DlrMisService dlrMisService;
	
	@Autowired
	private UserService userService;
	
	@Value("${app.name}")
	private String appName;
	
	@Value("${kafka.mis.dlr.reader.sleep.interval.ms}")
	private String misReaderSleepInterval;
	
	@Value("${mis.summary.expiry.seconds}")
	private String misSummaryExpirySeconds;
	
	@Autowired 
	@Qualifier("redisTemplateForSummary")
	private RedisTemplate<String, Integer> redisTemplateForSummary;
	
	/*@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateDeliveredSummary;
	
	@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateFailedSummary;
	*/
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	
	@KafkaListener(id = "dlr-"+"${app.name}", topics = "${kafka.topic.name.final.dlr.mis.object}")
	  public void receive(List<String> messageList,
	      @Header(KafkaHeaders.RECEIVED_PARTITION_ID) List<Integer> partitions,
	      @Header(KafkaHeaders.OFFSET) List<Long> offsets) {

		logger.info("start of DLR MIS batch read of size: "+messageList.size());
		for (int i = 0; i < messageList.size(); i++) {
			logger.info("received dlr mis message='{}' with partition-offset='{}'", messageList.get(i),
					partitions.get(i) + "-" + offsets.get(i));
			try{
				NgDlrMessage ngDlrMessage = convertReceivedJsonMessageIntoDlrMisObject(messageList.get(i));
				if (ngDlrMessage != null && ngDlrMessage.getMessageSource().toLowerCase().startsWith("webtool")) {
					String campaignId = ngDlrMessage.getMessageSource().split("-")[1];
					String dateKey = sdf.format(ngDlrMessage.getReceivedTs());
					NgUser user = userService.getUserById(ngDlrMessage.getUserId());
					//ngDlrMessage.getStatusId().equals("2") || ngDlrMessage.getStatusId().equals("1")
					if(ngDlrMessage.getStatusId() != null && (ngDlrMessage.getStatusDesc().equalsIgnoreCase("DELIVRD"))){
						String dateDeliveredKey = dateKey+":campaigndelivered:"+user.getUserName();
						redisTemplateForSummary.opsForHash().increment(dateDeliveredKey,campaignId,1);
						logger.info("Redis expire time for key {} is : {}",dateDeliveredKey, redisTemplateForSummary.getExpire(dateDeliveredKey, TimeUnit.SECONDS));
						if(redisTemplateForSummary.getExpire(dateDeliveredKey) == null || redisTemplateForSummary.getExpire(dateDeliveredKey) == -1){
							redisTemplateForSummary.expire(dateDeliveredKey, Long.parseLong(misSummaryExpirySeconds), TimeUnit.SECONDS);
						}
					}/*else if(ngDlrMessage.getStatusId() != null && (ngDlrMessage.getStatusId().equals("8")) && ngDlrMessage.getSmscId()!= null && ngDlrMessage.getSmscId().equalsIgnoreCase("000")){
						String dateRejectedKey = dateKey+":rejected:"+user.getUserName();
						redisTemplateForSummary.opsForHash().increment(dateRejectedKey,ngDlrMessage.getSenderId(),1);
						if(redisTemplateForSummary.getExpire(dateRejectedKey) == null || redisTemplateForSummary.getExpire(dateRejectedKey) == -1){
							redisTemplateForSummary.expire(dateRejectedKey, Long.parseLong(misSummaryExpirySeconds), TimeUnit.SECONDS);
						}
					}*/else if(ngDlrMessage.getStatusId() != null  && (!ngDlrMessage.getStatusId().equals("2") && !ngDlrMessage.getStatusId().equals("1")) 
							&& (ngDlrMessage.getSmscId()!= null && !ngDlrMessage.getSmscId().equalsIgnoreCase("000"))){
						String dateFailedKey = dateKey+":campaignfailed:"+user.getUserName();;
						redisTemplateForSummary.opsForHash().increment(dateFailedKey,campaignId,1);
						if(redisTemplateForSummary.getExpire(dateFailedKey) == null || redisTemplateForSummary.getExpire(dateFailedKey) == -1){
							redisTemplateForSummary.expire(dateFailedKey, Long.parseLong(misSummaryExpirySeconds), TimeUnit.SECONDS);
						}
					}
				}	
				}catch (Exception e){
					logger.error("Exception occured while saving DLR MIS message. Hence skipping this message: {} "+messageList.get(i));
					e.printStackTrace();
				}
			latch.countDown();
		}
		logger.info("End of received DLR MIS batch.");
	    try {
	    	logger.info("DLR MIS Reader Thread Going To Sleep for "+misReaderSleepInterval + "ms.");
	    	Thread.sleep(Integer.parseInt(misReaderSleepInterval));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	  }
	
	private NgDlrMessage convertReceivedJsonMessageIntoDlrMisObject(String dlrMisMessageObjectJsonString) {
		NgDlrMessage ngDlrMessage = null;
		try {
			ngDlrMessage = objectMapper.readValue(dlrMisMessageObjectJsonString, NgDlrMessage.class);
		} catch (Exception e){
			logger.error("Dont retry this message as error while parsing DLR MIS Message json string: "+dlrMisMessageObjectJsonString);
			e.printStackTrace();
		} 
		return ngDlrMessage;
	}
}