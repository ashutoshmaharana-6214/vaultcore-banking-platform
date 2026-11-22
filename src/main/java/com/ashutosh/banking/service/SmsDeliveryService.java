package com.ashutosh.banking.service;
import com.ashutosh.banking.entity.SmsNotification;
import com.ashutosh.banking.repository.CustomerUserRepository;
import com.ashutosh.banking.repository.SmsNotificationRepository;
import com.ashutosh.banking.sms.TwilioMessagingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
@Service
public class SmsDeliveryService {
 private final SmsNotificationRepository alerts;
 private final CustomerUserRepository users;
 private final TwilioMessagingClient provider;
 private final boolean enabled;
 public SmsDeliveryService(SmsNotificationRepository alerts,CustomerUserRepository users,
    TwilioMessagingClient provider,@Value("${vaultcore.sms.enabled:false}") boolean enabled){
  this.alerts=alerts;this.users=users;this.provider=provider;this.enabled=enabled;
 }
 @Transactional public Optional<SmsNotification> claim(Long id){
  if(!enabled||alerts.claim(id,Instant.now())!=1)return Optional.empty();
  return alerts.findById(id);
 }
 @Transactional public void complete(Long id,String result){
  alerts.findById(id).ifPresent(n->{
   if(!"SENDING".equals(n.getStatus()))return;
   switch(result){case "sent"->n.sent();case "suppressed"->n.suppressed();default->n.failed();}
  });
 }
 public String submit(SmsNotification n){
  try{
   var user=users.findByEmail(n.getRecipientEmail());
   if(user.isEmpty()||!user.get().isSmsAlertsEnabled()
       ||!n.getPhoneNumber().equals(user.get().getVerifiedPhoneNumber()))return "suppressed";
   provider.sendTransactionAlert(n.getPhoneNumber(),n.getBody());
   return "sent";
  }catch(RuntimeException exception){
   // Never expose phone numbers, message content or credentials in logs.
   return "failed";
  }
 }
}

