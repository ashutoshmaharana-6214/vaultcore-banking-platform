package com.ashutosh.banking.service;
import com.ashutosh.banking.entity.CustomerUser;
import com.ashutosh.banking.entity.SmsNotification;
import com.ashutosh.banking.repository.CustomerUserRepository;
import com.ashutosh.banking.repository.SmsNotificationRepository;
import org.springframework.stereotype.Service;
import java.util.LinkedHashSet;
import java.util.Set;
@Service
public class NotificationOutboxService {
 private final CustomerUserRepository users;
 private final SmsNotificationRepository alerts;
 public NotificationOutboxService(CustomerUserRepository users,SmsNotificationRepository alerts){
  this.users=users;this.alerts=alerts;
 }
 // Caller must invoke within the SAME DB transaction as the transfer.
 public void enqueueTransferAlerts(String reference,String sender,String receiver){
  Set<String> emails=new LinkedHashSet<>();
  if(sender!=null)emails.add(sender);if(receiver!=null)emails.add(receiver);
  for(String email:emails)users.findByEmail(email)
    .filter(CustomerUser::isSmsAlertsEnabled)
    .filter(u->u.getVerifiedPhoneNumber()!=null)
    .ifPresent(u->alerts.save(new SmsNotification(reference,u.getEmail(),u.getVerifiedPhoneNumber(),
      "VaultCore demo transfer completed. Reference: "+reference+". No real money moved.")));
 }
}
