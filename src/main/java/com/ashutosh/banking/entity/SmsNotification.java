package com.ashutosh.banking.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="sms_notifications",uniqueConstraints=@UniqueConstraint(name="uk_sms_tx_recipient",columnNames={"transaction_reference","recipient_email"}),indexes=@Index(name="idx_sms_pending",columnList="status,next_attempt_at"))
public class SmsNotification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="transaction_reference",nullable=false,length=40) private String transactionReference;
 @Column(name="recipient_email",nullable=false,length=254) private String recipientEmail;
 @Column(name="phone_number",nullable=false,length=16) private String phoneNumber;
 @Column(nullable=false,length=300) private String body;
 @Column(nullable=false,length=20) private String status;
 @Column(nullable=false) private int attempts;
 @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="sent_to_provider_at") private Instant sentToProviderAt;
 protected SmsNotification(){}
 public SmsNotification(String reference,String email,String phone,String message){
  transactionReference=reference; recipientEmail=email; phoneNumber=phone; body=message;
  status="PENDING"; nextAttemptAt=Instant.now(); createdAt=Instant.now();
 }
 public Long getId(){return id;} public String getRecipientEmail(){return recipientEmail;}
 public String getPhoneNumber(){return phoneNumber;} public String getBody(){return body;}
 public String getStatus(){return status;}
 public void sent(){ status="SENT_TO_PROVIDER";sentToProviderAt=Instant.now(); }
 public void suppressed(){status="SUPPRESSED";}
 public void failed(){attempts++;if(attempts>=5){status="EXHAUSTED";}else{status="RETRY";nextAttemptAt=Instant.now().plusSeconds(15L*(1L<<(attempts-1)));}}
}
