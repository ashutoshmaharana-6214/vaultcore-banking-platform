package com.ashutosh.banking.sms;
import com.ashutosh.banking.repository.SmsNotificationRepository;
import com.ashutosh.banking.service.SmsDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Component
public class SmsOutboxScheduler {
 private final SmsNotificationRepository alerts;
 private final SmsDeliveryService delivery;
 private final boolean enabled;
 public SmsOutboxScheduler(SmsNotificationRepository alerts,SmsDeliveryService delivery,
    @Value("${vaultcore.sms.enabled:false}") boolean enabled){
  this.alerts=alerts;this.delivery=delivery;this.enabled=enabled;
 }
 @Scheduled(fixedDelayString="${vaultcore.sms.poll-ms:3000}")
 public void dispatchDue(){
  if(!enabled)return;
  alerts.findDueIds(Instant.now(),PageRequest.of(0,50)).forEach(delivery::dispatch);
 }
 @Scheduled(fixedDelayString="${vaultcore.sms.stale-poll-ms:120000}")
 @Transactional public void releaseStale(){
  if(enabled)alerts.releaseStale(Instant.now(),Instant.now().minusSeconds(120));
 }
}
