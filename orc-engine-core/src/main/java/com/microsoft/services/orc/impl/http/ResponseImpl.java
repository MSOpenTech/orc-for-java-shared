/*******************************************************************************
 * Copyright (c) Microsoft Open Technologies, Inc.
 * All Rights Reserved
 * See License.txt in the project root for license information.
 ******************************************************************************/
package com.microsoft.services.orc.impl.http;

import com.microsoft.services.orc.interfaces.Response;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response implementation based on an InputStream
 */
public class ResponseImpl implements Response {
    private InputStream mStream;
    private int mStatus;
    /**
     * The M headers.
     */
    Map<String, List<String>> mHeaders;
    /**
     * The M client.
     */
    Closeable mClient;

    /**
     * Instantiates a new Response impl.
     *
     * @param stream the stream
     * @param status the status
     * @param headers the headers
     * @param client the client
     */
    public ResponseImpl(InputStream stream, int status, Map<String, List<String>> headers,
                        Closeable client) {
        mHeaders = new HashMap<String, List<String>>(headers);
        mStream = stream;
        mStatus = status;
        mClient = client;
    }

    @Override
    public int getStatus() {
        return mStatus;
    }

    @Override
    public InputStream getStream() {
        return mStream;
    }

    @Override
    public void close() throws IOException {
        mClient.close();
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return new HashMap<String, List<String>>(mHeaders);
    }

    @Override
    public List<String> getHeaders(String headerName) {
        return mHeaders.get(headerName);
    }
}
