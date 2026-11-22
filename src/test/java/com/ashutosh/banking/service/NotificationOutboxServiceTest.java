package com.ashutosh.banking.service;
import com.ashutosh.banking.entity.CustomerUser;
import com.ashutosh.banking.entity.SmsNotification;
import com.ashutosh.banking.repository.CustomerUserRepository;
import com.ashutosh.banking.repository.SmsNotificationRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationOutboxServiceTest {
 @Test void onlyVerifiedConsentingUsersReceiveAlerts(){
  var users=mock(CustomerUserRepository.class);
  var notifications=mock(SmsNotificationRepository.class);
  CustomerUser sender=new CustomerUser("sender@example.com","hashed");
  sender.verifyPhoneNumber("+919876543210");
  sender.setSmsAlertsEnabled(true);
  CustomerUser receiver=new CustomerUser("receiver@example.com","hashed");
  receiver.verifyPhoneNumber("+919876543211");
  when(users.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
  when(users.findByEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
  new NotificationOutboxService(users,notifications).enqueueTransferAlerts("TXN-123","sender@example.com","receiver@example.com");
  verify(notifications,times(1)).save(any(SmsNotification.class));
 }
 @Test void sameUserIsEnqueuedOnlyOnce(){
  var users=mock(CustomerUserRepository.class);
  var notifications=mock(SmsNotificationRepository.class);
  CustomerUser user=new CustomerUser("sender@example.com","hashed");
  user.verifyPhoneNumber("+919876543210");
  user.setSmsAlertsEnabled(true);
  when(users.findByEmail("sender@example.com")).thenReturn(Optional.of(user));
  new NotificationOutboxService(users,notifications).enqueueTransferAlerts("TXN-123","sender@example.com","sender@example.com");
  verify(notifications,times(1)).save(any(SmsNotification.class));
 }
 @Test void smsConsentCannotBeEnabledWithoutVerification(){
  CustomerUser user=new CustomerUser("sender@example.com","hashed");
  assertThrows(IllegalStateException.class,()->user.setSmsAlertsEnabled(true));
 }
 @Test void changingVerifiedPhoneResetsConsent(){
  CustomerUser user=new CustomerUser("sender@example.com","hashed");
  user.verifyPhoneNumber("+919876543210");user.setSmsAlertsEnabled(true);
  user.verifyPhoneNumber("+919876543211");
  assertFalse(user.isSmsAlertsEnabled());
 }
}
