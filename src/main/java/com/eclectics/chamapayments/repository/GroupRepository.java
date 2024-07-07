package com.eclectics.chamapayments.repository;

import com.eclectics.chamapayments.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Group findGroupsById(long id);

    Optional<Group> findByName(String name);

    @Query(value = "select * from groups_tbl where reg_date is not null and reg_number is not null and name is not null and client_group_no <> '000000000000000'  and client_group ='Y' and first_signatory='Y' and CLIENT_NO_SIGNATORY_1 <> '000000000000000' and CLIENT_NO_SIGNATORY_2 <> '000000000000000' and CLIENT_NO_SIGNATORY_3 <> '000000000000000' and second_signatory='Y' and third_signatory='Y' and cbs_account <> '000000000000000' and cbs_satisfied='Y' order by id desc", nativeQuery = true)
    List<Group> findAllCBSSatisfied();

    Group findFirstByCbsAccountAndSoftDeleteFalse(String cbsAccount);
}
