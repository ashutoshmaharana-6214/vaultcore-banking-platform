package com.ashutosh.banking.repository;
import com.ashutosh.banking.entity.SmsNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
public interface SmsNotificationRepository extends JpaRepository<SmsNotification,Long>{
 @Query("select n.id from SmsNotification n where n.status in ('PENDING','RETRY') and n.nextAttemptAt <= :now order by n.nextAttemptAt")
 List<Long> findDueIds(@Param("now") Instant now, Pageable page);
 @Modifying
 @Query("update SmsNotification n set n.status='SENDING' where n.id=:id and n.status in ('PENDING','RETRY') and n.nextAttemptAt <= :now")
 int claim(@Param("id") Long id,@Param("now") Instant now);
 @Modifying
 @Query("update SmsNotification n set n.status='RETRY', n.nextAttemptAt=:when where n.status='SENDING' and n.nextAttemptAt < :stale")
 int releaseStale(@Param("when") Instant when,@Param("stale") Instant stale);
}
