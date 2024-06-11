package com.noesis.campaign.summary.manager.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.noesis.campaign.summary.manager.service.NgCampaignSummaryService;
import com.noesis.domain.service.UserService;
 

@RestController
public class UserController {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	UserService userService;
	
	@Autowired
	NgCampaignSummaryService summaryService;
	
	@Autowired
	@Qualifier("redisTemplateForSummary")
	private RedisTemplate<String, Integer> redisTemplateForSummary;
	
	/*@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateSubmittedSummary;
	
	@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateRejectedSummary;
	
	@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateDeliveredSummary;
	
	@Autowired 
	private RedisTemplate<String, Integer> redisTemplateForDateFailedSummary;*/
	
	@RequestMapping(value = "/getUserCampaignSummaryForDate/{date}/{username}", method = RequestMethod.GET)
	public String getUser(@PathVariable String date, @PathVariable String username, @RequestParam(required=false) Optional<String> campaignid) {
		logger.info("Getting summary for date {} and username {}.", date, username);
		SummaryMisFormResponseDataGrid gridData = new SummaryMisFormResponseDataGrid();
		
		String campaignId = null;
		if(campaignid.isPresent()){
			campaignId = campaignid.get();
		}
		
		String submittedKey = date+":campaignsubmitted:"+username;
		String deliveredKey = date+":campaigndelivered:"+username;
		String failedKey = date+":campaignfailed:"+username;
		String rejectedKey = date+":campaignrejected:"+username;
		String totalReqKey = date+":campaigntotalrequest:"+username;
		
		String submitted = "0";
		String delivered = "0";
		String failed = "0";
		String rejected = "0";
		String totalReq = "0";
		
		Integer sub=0;
		Integer del = 0;
		Integer fail = 0;
		Integer rej = 0;
		Integer tot = 0;
		
		if(campaignId == null) {
			Map<Object, Object> submittedMap = (Map<Object, Object>)redisTemplateForSummary.opsForHash().entries(submittedKey);
			Map<Object, Object> deliveredMap = (Map<Object, Object>)redisTemplateForSummary.opsForHash().entries(deliveredKey);
			Map<Object, Object> failedMap = (Map<Object, Object>)redisTemplateForSummary.opsForHash().entries(failedKey);
			Map<Object, Object> rejectedMap = (Map<Object, Object>)redisTemplateForSummary.opsForHash().entries(rejectedKey);
			Map<Object, Object> totalReqMap = (Map<Object, Object>)redisTemplateForSummary.opsForHash().entries(totalReqKey);
			Set<Object> uniqueCampaignIdKeys = totalReqMap.keySet();
			
			for (Object campaignIdObject : uniqueCampaignIdKeys) {
				String campaignIdString = (String)campaignIdObject;
				if(submittedMap.containsKey(campaignIdString)){
					sub = sub + Integer.parseInt((String)submittedMap.get(campaignIdString)); 
				}
				if(deliveredMap.containsKey(campaignIdString)){
					del = del + Integer.parseInt((String)deliveredMap.get(campaignIdString)); 
				}
				if(failedMap.containsKey(campaignIdString)){
					fail = fail + Integer.parseInt((String)failedMap.get(campaignIdString)); 
				}
				if(rejectedMap.containsKey(campaignIdString)){
					rej = rej + Integer.parseInt((String)rejectedMap.get(campaignIdString)); 
				}
				if(totalReqMap.containsKey(campaignIdString)){
					tot = tot + Integer.parseInt((String)totalReqMap.get(campaignIdString)); 
				}
			}
			submitted = ""+sub;
			delivered = ""+del;
			failed = ""+fail;
			rejected = ""+rej;
			totalReq = ""+tot;
		}else {
			submitted = (String)redisTemplateForSummary.opsForHash().get(submittedKey, campaignId);
			delivered = (String)redisTemplateForSummary.opsForHash().get(deliveredKey, campaignId);
			failed =     (String)redisTemplateForSummary.opsForHash().get(failedKey, campaignId);
			rejected = (String)redisTemplateForSummary.opsForHash().get(rejectedKey, campaignId);
			totalReq = (String)redisTemplateForSummary.opsForHash().get(totalReqKey, campaignId);
		}
		
		
		logger.info("Campaign Data is submitted: {}.",submitted);
		logger.info("Campaign Data is delivered: {}.",delivered);
		logger.info("Campaign Data is failed: {}.",failed);
		logger.info("Campaign Data is rejected: {}.",rejected);
		logger.info("Campaign Data is totalReq: {}.",totalReq);
		
		if(submitted == null ){
			submitted = "0";
		}
		if(delivered == null ){
			delivered = "0";
		}
		if(failed == null ){
			failed = "0";
		}
		if(rejected == null ){
			rejected = "0";
		}
		if(totalReq == null ){
			totalReq = "0";
		}
		
		
		gridData.setSummaryDate(date);
		gridData.setTotalRequest(""+totalReq);
		gridData.setTotalSubmit(""+submitted);
		gridData.setTotalRejected(""+rejected);
		gridData.setTotalDelivered(""+delivered);
		gridData.setTotalFailed(""+failed);
		gridData.setTotalAwaited(""+(Integer.parseInt(submitted) - (Integer.parseInt(delivered)+ Integer.parseInt(failed))));
	 
		return gridData.toString();
	}
	
	public static void main(String[] args) {
		Map<Object, Object> hm = new HashMap<>();
		String key = "test";
		Integer value = 5;
		hm.put(key, value);
		System.out.println(hm.containsKey(key));
		System.out.println(hm.get(key));
	}
	
	@RequestMapping(value = "/updateAllUsersCampaignSummaryData", method = RequestMethod.GET)
	public String updateAllUsersSummaryData() {
	summaryService.updateAllUsersCampaignsForCurrentDateFromRedisToDB();
	
	return "Users summary updated successfully.";
}


}

class SummaryMisFormResponseDataGrid {
	
	private String summaryDate;
	private String totalRequest;
	private String totalRejected;
	private String totalSubmit;
	private String totalDelivered;
	private String totalFailed;
	private String totalAwaited;
	
	
	public String getSummaryDate() {
		return summaryDate;
	}
	public void setSummaryDate(String summaryDate) {
		this.summaryDate = summaryDate;
	}
	public String getTotalRequest() {
		return totalRequest;
	}
	public void setTotalRequest(String totalRequest) {
		this.totalRequest = totalRequest;
	}
	public String getTotalRejected() {
		return totalRejected;
	}
	public void setTotalRejected(String totalRejected) {
		this.totalRejected = totalRejected;
	}
	public String getTotalSubmit() {
		return totalSubmit;
	}
	public void setTotalSubmit(String totalSubmit) {
		this.totalSubmit = totalSubmit;
	}
	public String getTotalDelivered() {
		return totalDelivered;
	}
	public void setTotalDelivered(String totalDelivered) {
		this.totalDelivered = totalDelivered;
	}
	public String getTotalFailed() {
		return totalFailed;
	}
	public void setTotalFailed(String totalFailed) {
		this.totalFailed = totalFailed;
	}
	public String getTotalAwaited() {
		return totalAwaited;
	}
	public void setTotalAwaited(String totalAwaited) {
		this.totalAwaited = totalAwaited;
	}
	@Override
	public String toString() {
		return "Summary - [summaryDate=" + summaryDate + ", totalRequest=" + totalRequest
				+ ", totalRejected=" + totalRejected + ", totalSubmit=" + totalSubmit + ", totalDelivered="
				+ totalDelivered + ", totalFailed=" + totalFailed + ", totalAwaited=" + totalAwaited + "]";
	}
	
}



