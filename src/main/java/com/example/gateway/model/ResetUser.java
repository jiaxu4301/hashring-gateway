package com.example.gateway.model;

import lombok.Data;

import java.io.Serializable;

/**
 * ResetUser
 */

@Data
public class ResetUser implements Serializable {
    private Integer id;

    private String name;

    private String messageId;

    private String userId;

    private String resetLink;

    private String originalLink;

    private String routingKey;

    private static final long serialVersionUID = 1L;

    public void setOriginalLink(String originalLink) {
        this.originalLink = originalLink == null ? null : originalLink.trim();
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey == null ? null : routingKey.trim();
    }

}