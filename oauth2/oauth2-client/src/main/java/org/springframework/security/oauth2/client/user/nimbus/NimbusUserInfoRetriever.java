/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.oauth2.client.user.nimbus;

import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.client.user.UserInfoRetriever;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * An implementation of a {@link UserInfoRetriever} that uses the <b>Nimbus OAuth 2.0 SDK</b> internally.
 *
 * @author Joe Grandja
 * @since 5.0
 * @see UserInfoRetriever
 * @see <a target="_blank" href="https://connect2id.com/products/nimbus-oauth-openid-connect-sdk">Nimbus OAuth 2.0 SDK</a>
 */
public class NimbusUserInfoRetriever implements UserInfoRetriever {
	private static final String INVALID_USER_INFO_RESPONSE_ERROR_CODE = "invalid_user_info_response";
	private final HttpMessageConverter jackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();

	@Override
	public Map<String, Object> retrieve(OAuth2ClientAuthenticationToken clientAuthentication) throws OAuth2AuthenticationException {
		URI userInfoUri = URI.create(clientAuthentication.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri());
		BearerAccessToken accessToken = new BearerAccessToken(clientAuthentication.getAccessToken().getTokenValue());

		UserInfoRequest userInfoRequest = new UserInfoRequest(userInfoUri, accessToken);
		HTTPRequest httpRequest = userInfoRequest.toHTTPRequest();
		httpRequest.setConnectTimeout(30000);
		httpRequest.setReadTimeout(30000);
		HTTPResponse httpResponse;

		try {
			httpResponse = httpRequest.send();
		} catch (IOException ex) {
			throw new AuthenticationServiceException("An error occurred while sending the UserInfo Request: " +
				ex.getMessage(), ex);
		}

		if (httpResponse.getStatusCode() != HTTPResponse.SC_OK) {
			UserInfoErrorResponse userInfoErrorResponse;
			try {
				userInfoErrorResponse = UserInfoErrorResponse.parse(httpResponse);
			} catch (ParseException ex) {
				OAuth2Error oauth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE,
					"An error occurred parsing the UserInfo Error response: " + ex.getMessage(), null);
				throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString(), ex);
			}
			ErrorObject errorObject = userInfoErrorResponse.getErrorObject();

			StringBuilder errorDescription = new StringBuilder();
			errorDescription.append("An error occurred while attempting to access the UserInfo Endpoint -> ");
			errorDescription.append("Error details: [");
			errorDescription.append("UserInfo Uri: ").append(userInfoUri.toString());
			errorDescription.append(", Http Status: ").append(errorObject.getHTTPStatusCode());
			if (errorObject.getCode() != null) {
				errorDescription.append(", Error Code: ").append(errorObject.getCode());
			}
			if (errorObject.getDescription() != null) {
				errorDescription.append(", Error Description: ").append(errorObject.getDescription());
			}
			errorDescription.append("]");

			OAuth2Error oauth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE, errorDescription.toString(), null);
			throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
		}

		try {
			return (Map<String, Object>) this.jackson2HttpMessageConverter.read(Map.class, new NimbusClientHttpResponse(httpResponse));
		} catch (IOException ex) {
			OAuth2Error oauth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE,
				"An error occurred reading the UserInfo Success response: " + ex.getMessage(), null);
			throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString(), ex);
		}
	}
}
