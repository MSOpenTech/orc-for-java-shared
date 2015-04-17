/*******************************************************************************
 * Copyright (c) Microsoft Open Technologies, Inc.
 * All Rights Reserved
 * See License.txt in the project root for license information.
 ******************************************************************************/
package com.microsoft.services.orc;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.microsoft.services.orc.interfaces.Credentials;
import com.microsoft.services.orc.interfaces.DependencyResolver;
import com.microsoft.services.orc.interfaces.HttpTransport;
import com.microsoft.services.orc.interfaces.LogLevel;
import com.microsoft.services.orc.interfaces.Logger;
import com.microsoft.services.orc.interfaces.OrcResponse;
import com.microsoft.services.orc.interfaces.Request;
import com.microsoft.services.orc.interfaces.Response;

import java.util.Map;

/**
 * The type BaseOrcContainer.
 */
public abstract class BaseOrcContainer extends OrcExecutable {

    private String url;
    private DependencyResolver resolver;

    public BaseOrcContainer(String url, DependencyResolver resolver) {
        this.url = url;
        this.resolver = resolver;
    }

    @Override
    protected ListenableFuture<OrcResponse> oDataExecute(final Request request) {
        final SettableFuture<OrcResponse> result = SettableFuture.create();
        final Logger logger = resolver.getLogger();

        try {
            request.getUrl().setBaseUrl(this.url);
            String fullUrl = request.getUrl().toString();

            String executionInfo = String.format("URL: %s - HTTP VERB: %s", fullUrl, request.getVerb());
            logger.log("Start preparing OData execution for " + executionInfo, LogLevel.INFO);

            if (request.getContent() != null) {
                logger.log("With " + request.getContent().length + " bytes of payload", LogLevel.INFO);
            } else if (request.getStreamedContent() != null) {
                logger.log("With stream of bytes for payload", LogLevel.INFO);
            }

            HttpTransport httpTransport = resolver.getHttpTransport();

            String userAgent = resolver.getPlatformUserAgent(this.getClass().getCanonicalName());
            request.addHeader(Constants.USER_AGENT_HEADER, userAgent);
            request.addHeader(Constants.TELEMETRY_HEADER, userAgent);
            if (!request.getHeaders().containsKey(Constants.CONTENT_TYPE_HEADER)) {
                request.addHeader(Constants.CONTENT_TYPE_HEADER, Constants.JSON_CONTENT_TYPE);
            }
            request.addHeader(Constants.ACCEPT_HEADER, Constants.JSON_CONTENT_TYPE);
            request.addHeader(Constants.ODATA_VERSION_HEADER, Constants.ODATA_VERSION);
            request.addHeader(Constants.ODATA_MAXVERSION_HEADER, Constants.ODATA_MAXVERSION);

            if (request.getHeaders() != null) {
                for (String key : request.getHeaders().keySet()) {
                    request.addHeader(key, request.getHeaders().get(key));
                }
            }

            boolean credentialsSet = false;

            Credentials cred = resolver.getCredentials();
            if (cred != null) {
                cred.prepareRequest(request);
                credentialsSet = true;
            }

            if (!credentialsSet) {
                logger.log("Executing request without setting credentials", LogLevel.WARNING);
            }


            logger.log("Request Headers: ", LogLevel.VERBOSE);
            for (String key : request.getHeaders().keySet()) {
                logger.log(key + " : " + request.getHeaders().get(key), LogLevel.VERBOSE);
            }

            final ListenableFuture<Response> future = httpTransport.execute(request);
            logger.log("OData request executed", LogLevel.INFO);

            Futures.addCallback(future, new FutureCallback<Response>() {

                @Override
                public void onSuccess(Response response) {
                    boolean readBytes = true;
                    if (request.getOptions().get(Request.MUST_STREAM_RESPONSE_CONTENT) != null) {
                        readBytes = false;
                    }

                    OrcResponse odataResponse = new OrcResponseImpl(response);

                    try {
                        logger.log("OData response received", LogLevel.INFO);

                        int status = response.getStatus();
                        logger.log("Response Status Code: " + status, LogLevel.INFO);

                        if (readBytes) {
                            logger.log("Reading response data...", LogLevel.VERBOSE);
                            byte[] data = odataResponse.getPayload();
                            logger.log(data.length + " bytes read from response", LogLevel.VERBOSE);

                            try {
                                logger.log("Closing response", LogLevel.VERBOSE);
                                response.close();
                            } catch (Throwable t) {
                                logger.log("Error closing response: " + t.toString(), LogLevel.ERROR);
                                result.setException(t);
                                return;
                            }

                        }

                        if (status < 200 || status > 299) {
                            logger.log("Invalid status code. Processing response content as String", LogLevel.VERBOSE);
                            String responseData = new String(odataResponse.getPayload(), Constants.UTF8_NAME);
                            String message = "Response status: " + response.getStatus() + "\n" + "Response content: " + responseData;
                            logger.log(message, LogLevel.ERROR);
                            result.setException(new ODataException(odataResponse, message));
                            return;
                        }
                        result.set(odataResponse);
                    } catch (Throwable t) {
                        logger.log("Unexpected error: " + t.toString(), LogLevel.ERROR);
                        result.setException(new ODataException(odataResponse, t));
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    result.setException(throwable);
                }
            });
        } catch (Throwable t) {
            result.setException(t);
        }
        return result;

    }

    /**
     * Generate parameters payload.
     *
     * @param parameters the parameters
     * @param resolver   the resolver
     * @return the string
     */
    public static String generateParametersPayload(Map<String, Object> parameters, DependencyResolver resolver) {
        return resolver.getJsonSerializer().serialize(parameters);
    }

    @Override
    protected DependencyResolver getResolver() {
        return resolver;
    }
}