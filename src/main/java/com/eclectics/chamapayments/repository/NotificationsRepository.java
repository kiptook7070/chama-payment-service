package com.eclectics.chamapayments.repository;


import com.eclectics.chamapayments.model.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    @Query("SELECT n FROM Notifications n WHERE n.groupName = ?1 ORDER BY n.createdOn DESC")
    List<Notifications> findAllByGroupNameAndActiveTrue(String groupName);

}
