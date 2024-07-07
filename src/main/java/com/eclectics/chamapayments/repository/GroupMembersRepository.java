package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupMembersRepository extends JpaRepository<GroupMembership, Long> {

    List<GroupMembership> findAllByGroupId(Long groupId);

    List<GroupMembership> findByActivemembershipAndGroupId(Boolean active, Long groupId);

    List<GroupMembership> findAllByGroupIdAndActivemembershipTrue(long groupId);

    @Query(value = "select * from GROUP_MEMBERSHIP_TBL where GROUP_ID=:groupId and MEMBERS_ID=:memberId", nativeQuery = true)
    Optional<GroupMembership> getGroupMember(Long groupId, Long memberId);

}
