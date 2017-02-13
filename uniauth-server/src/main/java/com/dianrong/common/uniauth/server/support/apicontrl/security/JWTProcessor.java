package com.dianrong.common.uniauth.server.support.apicontrl.security;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dianrong.common.uniauth.common.apicontrol.exp.InvalidTokenException;
import com.dianrong.common.uniauth.common.apicontrol.exp.TokenCreateFailedException;
import com.dianrong.common.uniauth.common.apicontrol.exp.TokenExpiredException;
import com.dianrong.common.uniauth.common.apicontrol.server.CallerCredential;
import com.dianrong.common.uniauth.common.apicontrol.server.TokenProcessor;
import com.dianrong.common.uniauth.common.exp.UniauthCommonException;
import com.dianrong.common.uniauth.common.util.Assert;
import com.dianrong.common.uniauth.common.util.JsonUtil;
import com.dianrong.common.uniauth.server.support.apicontrl.ApiCaller;
import com.dianrong.common.uniauth.server.support.apicontrl.ApiCtlPermission;
import com.dianrong.common.uniauth.server.support.apicontrl.security.exp.JWTVerifierCreateFailedException;
import com.mysql.jdbc.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * jwt token processor 
 * @author wanglin
 */
@Slf4j
@Component
public class JWTProcessor implements TokenProcessor<ApiCtlPermission> {
    // define some constant string
    public static final String ISSUER = "uniauth-server";
    public static final String AUDIENCE = "all-uniauth-integrate-domains";
    public static final String SUBJECT = "uniauth-server-api-call-token";
    
    @Value("#{uniauthConfig['apicall.jwt.security.key']}")
    private String configSecurityKey;
    
    // init jwt 
    private JWTSecurity jwt;
    
    @PostConstruct
	public void init() {
    	 try {
    		 if (StringUtils.isNullOrEmpty(configSecurityKey)) {
    			 this.jwt = new JWTSecurity();
    			 log.info("init JWTSecurity with default security key");
    		 } else {
    			 log.info("init JWTSecurity with configured security key");
    			 this.jwt = new JWTSecurity(this.configSecurityKey);
    		 }
         } catch (JWTVerifierCreateFailedException e) {
             log.error("failed to create JWTSecurity", e);
             throw new UniauthCommonException(e.getMessage(), e);
         }  
	}

    @Override
    public String sign(CallerCredential<ApiCtlPermission> credential) throws TokenCreateFailedException {
        Assert.notNull(credential);
        JwtInfo jwtInfo = new JwtInfo(ISSUER, AUDIENCE, SUBJECT, credential.getCallerName() ,
                credential.getAccount(), JsonUtil.object2Jason(credential.getPermissionInfo()),
                credential.getCreateTime(), credential.getExpireTime());
        return this.jwt.createJwt(jwtInfo);
    }
    @Override
    public CallerCredential<ApiCtlPermission> verify(String token) throws InvalidTokenException, TokenExpiredException {
        JwtInfo jwtInfo = this.jwt.getInfoFromJwt(token);
        // customized token check
        if (!ISSUER.equals(jwtInfo.getIssuer()) || !AUDIENCE.equals(jwtInfo.getAudience())
                || !SUBJECT.equals(jwtInfo.getSubject())) {
            log.error(token + " is a invalid token string");
            throw new InvalidTokenException();
        }
        try {
            ApiCtlPermission permission = JsonUtil.jsonToObject(jwtInfo.getPermission(), ApiCtlPermission.class);
            return new ApiCaller(jwtInfo.getAccount(), jwtInfo.getName(), permission, jwtInfo.getCreateTime(), jwtInfo.getExpireTime());
        } catch (Throwable t) {
            log.error(token + " is a invalid token string");
            throw new InvalidTokenException();
        }
    }
}
