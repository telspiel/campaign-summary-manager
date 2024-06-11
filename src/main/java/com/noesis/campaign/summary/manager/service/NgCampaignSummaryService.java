package com.noesis.campaign.summary.manager.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.noesis.domain.persistence.NgCampaignReport;
import com.noesis.domain.persistence.NgUser;
import com.noesis.domain.repository.NgCampaignReportRepository;
import com.noesis.domain.service.UserService;

@Service
public class NgCampaignSummaryService {
	
	@Autowired
	private NgCampaignReportRepository ngCampaignReportRepository;
	
	@Autowired
	@Qualifier("redisTemplateForSummary")
	@Resource(name = "redisTemplateForSummary")
	private RedisTemplate<String, Integer> redisTemplateForSummary;
	
	@Autowired
	private UserService userService;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
		
	private List<NgCampaignReport> updateCampaignDataForCurrentDateFromRedisToDB(Date currentDate, String username, String date) {
		NgUser ngUser = userService.getUserByNameFromDb(username);
		logger.info("Getting summary for date {} and username {}.", date, username);

		String submittedKey = date + ":campaignsubmitted:" + username;
		String deliveredKey = date + ":campaigndelivered:" + username;
		String failedKey = date + ":campaignfailed:" + username;
		String rejectedKey = date + ":campaignrejected:" + username;
		String totalReqKey = date + ":campaigntotalrequest:" + username;

		Map<Object, Object> submittedMap = (Map<Object, Object>) redisTemplateForSummary.opsForHash()
				.entries(submittedKey);
		Map<Object, Object> deliveredMap = (Map<Object, Object>) redisTemplateForSummary.opsForHash()
				.entries(deliveredKey);
		Map<Object, Object> failedMap = (Map<Object, Object>) redisTemplateForSummary.opsForHash().entries(failedKey);
		Map<Object, Object> rejectedMap = (Map<Object, Object>) redisTemplateForSummary.opsForHash()
				.entries(rejectedKey);
		Map<Object, Object> totalReqMap = (Map<Object, Object>) redisTemplateForSummary.opsForHash()
				.entries(totalReqKey);
		Set<Object> uniqueCampaignIdKeys = totalReqMap.keySet();
		logger.info("campaign id key set : {} ", uniqueCampaignIdKeys);
		List<NgCampaignReport> ngCampaignListToBeUpdated = new ArrayList<>();
		List<NgCampaignReport> existingCampaignObjectsList = ngCampaignReportRepository.findByUserNameAndDateGreaterThanEqualAndDateLessThanEqual(username, currentDate, currentDate);
		
		for (Object campaignIdObject : uniqueCampaignIdKeys) {
			String submitted = "0";
			String delivered = "0";
			String failed = "0";
			String rejected = "0";
			String totalReq = "0";
			String awaited = "0";

			String campaignId = (String) campaignIdObject;
			logger.info("Going to update campaign summary for campaign id {} and date {} : ", campaignId, date);

			if (submittedMap.containsKey(campaignId)) {
				submitted = (String) submittedMap.get(campaignId);
			}
			if (deliveredMap.containsKey(campaignId)) {
				delivered = (String) deliveredMap.get(campaignId);
			}
			if (failedMap.containsKey(campaignId)) {
				failed = (String) failedMap.get(campaignId);
			}
			if (rejectedMap.containsKey(campaignId)) {
				rejected = (String) rejectedMap.get(campaignId);
			}
			if (totalReqMap.containsKey(campaignId)) {
				totalReq = (String) totalReqMap.get(campaignId);
			}

			awaited = "" + (Integer.parseInt(submitted) - (Integer.parseInt(delivered) + Integer.parseInt(failed)));

			//NgSummaryReport existingSummaryReportObject = ngMisLogSummaryRepository.findByUserNameAndDateAndSenderId(username, currentDate, senderIdString);
			NgCampaignReport existingCampaignReportObject = existingCampaignObjectsList.stream().
					filter(p -> p.getCampaignId()==(Integer.parseInt(campaignId))).
					findAny().orElse(null);
			if (existingCampaignReportObject != null) {
				logger.info("Found campaign Report Object in DB for date {}, username {}, campaign id {}", date, username,
						campaignId);
				existingCampaignReportObject.setAwaited(awaited);
				existingCampaignReportObject.setDelivered(delivered);
				existingCampaignReportObject.setFailed(failed);
				existingCampaignReportObject.setRejected(rejected);
				existingCampaignReportObject.setSubmitted(submitted);
				//existingCampaignReportObject.setTotalRequest(totalReq);
				ngCampaignListToBeUpdated.add(existingCampaignReportObject);
			} else {
				logger.info("Creating campaign Report Object in DB for date {}, username {}, senderid {}", date,
						username, campaignId);
				NgCampaignReport newCampaignReportObject = new NgCampaignReport();
				newCampaignReportObject.setAdId(ngUser.getAdId());
				newCampaignReportObject.setAwaited(awaited);
				newCampaignReportObject.setDate(currentDate);
				newCampaignReportObject.setDelivered(delivered);
				newCampaignReportObject.setFailed(failed);
				newCampaignReportObject.setPaId(ngUser.getParentId());
				newCampaignReportObject.setReId(ngUser.getReId());
				newCampaignReportObject.setRejected(rejected);
				newCampaignReportObject.setSaId(ngUser.getSaId());
				newCampaignReportObject.setSeId(ngUser.getSeId());
				newCampaignReportObject.setSenderId("");
				newCampaignReportObject.setSubmitted(submitted);
				newCampaignReportObject.setTotalRequest(totalReq);
				newCampaignReportObject.setUserId(ngUser.getId());
				newCampaignReportObject.setUserName(username);
				ngCampaignListToBeUpdated.add(newCampaignReportObject);
			}
		}
		logger.info("Going to save total {} campaign objects for user {} and date {}", ngCampaignListToBeUpdated.size(),
				date, username);
		//ngMisLogSummaryRepository.save(ngSummaryListToBeUpdated);
		logger.info("Summary updated successfully for current date.");
		return ngCampaignListToBeUpdated;
	}
	

	public void updateAllUsersCampaignsForCurrentDateFromRedisToDB() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDateTime currentDateTime = LocalDateTime.now();
		String currentDate = dtf.format(currentDateTime);
		Date currDate;
		List<NgCampaignReport> finalListToBeUpdated = new LinkedList<NgCampaignReport>();
		try {
			currDate = sdf.parse(currentDate);
			// update summary data for current date.
			Iterable<NgUser> usersList = userService.getAllUserList();
			for (NgUser ngUser : usersList) {
				try{
					logger.info("Going to update campaign summary for user {} for date {}", ngUser.getUserName(), currentDate);
					List<NgCampaignReport> listToBeUpdated = updateCampaignDataForCurrentDateFromRedisToDB(currDate, ngUser.getUserName(), currentDate);
					finalListToBeUpdated.addAll(listToBeUpdated);
				}catch (Exception e) {
					logger.error("Error while update user {} summary data for date {}", ngUser.getUserName(), currentDate);
				}
			}
			if(finalListToBeUpdated.size() > 0){
				ngCampaignReportRepository.save(finalListToBeUpdated);
			}
		} catch (ParseException e) {
			logger.error("Error while updating all users campaign summary data in db.");
			e.printStackTrace();
		}
	}
}
