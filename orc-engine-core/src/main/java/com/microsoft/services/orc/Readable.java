/*******************************************************************************
 * Copyright (c) Microsoft Open Technologies, Inc.
 * All Rights Reserved
 * See License.txt in the project root for license information.
 ******************************************************************************/
package com.microsoft.services.orc;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * The interface Readable.
 * @param <T>  the type parameter
 */
public interface Readable<T> {
    public ListenableFuture<T> read();
    public ListenableFuture<String> readRaw();
}
