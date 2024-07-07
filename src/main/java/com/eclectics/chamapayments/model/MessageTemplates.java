package com.eclectics.chamapayments.model;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * Entity to hold message templates for SMS and Email.
 */

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_templates",
        indexes = {@Index(name = "index_message_templates_tbl", columnList = "id, type", unique = true)})
public class MessageTemplates extends BaseEntity {
    String template;
    String type;
    String language;
}
