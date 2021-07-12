package com.networknt.configserver.helper;

import com.networknt.configserver.model.Authorization;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.utility.Constants;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.jose4j.jwt.JwtClaims;

import java.util.Map;

public class AuthorizationHelper {
    public static Authorization getAuthorization(HttpServerExchange exchange) {
        Authorization authorization = new Authorization();
        authorization.setAuthorization(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION));
        Map<String, Object> auditInfo = (Map) exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
        if(auditInfo != null) {
            authorization.setClaims((JwtClaims) auditInfo.getOrDefault(Constants.SUBJECT_CLAIMS, null));
        }
        return authorization;
    }
}
