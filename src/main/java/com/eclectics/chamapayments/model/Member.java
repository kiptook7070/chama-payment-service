package com.eclectics.chamapayments.model;

import com.eclectics.chamapayments.model.jpaAudit.Auditable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.Date;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Builder
@Entity
@Table(name = "members_tbl")
public class Member extends Auditable {
    @Column(unique = true)
    private String imsi;
    private String userDeviceId;
    @Column(columnDefinition = "boolean default false")
    private boolean isregisteredmember;
    @Column(columnDefinition = "boolean default false")
    private boolean ussdplatform;
    @Column(columnDefinition = "boolean default false")
    private boolean androidplatform;
    @Column(columnDefinition = "boolean default false")
    private boolean iosplatform;
    private boolean active;
    private Date deactivationdate;
    @Column(unique = true)
    private String esbwalletaccount;
    @Column(columnDefinition = "boolean default false")
    private boolean walletexists;

    @OneToMany(mappedBy = "member")
    private List<AccruedInterest> accruedInterests;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String linkedAccounts;
}
