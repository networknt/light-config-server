package com.networknt.configserver.model;

import org.jose4j.jwt.JwtClaims;

public class Authorization {

    private String authorization;
    private JwtClaims claims;

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    public JwtClaims getClaims() {
        return claims;
    }

    public void setClaims(JwtClaims claims) {
        this.claims = claims;
    }
}
