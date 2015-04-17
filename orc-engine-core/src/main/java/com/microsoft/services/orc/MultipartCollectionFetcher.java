package com.microsoft.services.orc;

import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.ListenableFuture;
import com.microsoft.services.orc.interfaces.HttpVerb;
import com.microsoft.services.orc.interfaces.OrcResponse;
import com.microsoft.services.orc.interfaces.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.microsoft.services.orc.Helpers.transformToVoidListenableFuture;


public class MultipartCollectionFetcher<TEntity, TFetcher extends OrcEntityFetcher, TOperations extends OrcOperations>
        extends OrcCollectionFetcher<TEntity, TFetcher, TOperations> {

    /**
     * Instantiates a new OrcCollectionFetcher.
     *
     * @param urlComponent   the url component
     * @param parent         the parent
     * @param clazz          the clazz
     * @param operationClazz the operation clazz
     */
    public MultipartCollectionFetcher(String urlComponent, OrcExecutable parent, Class<TEntity> clazz, Class<TOperations> operationClazz) {
        super(urlComponent, parent, clazz, operationClazz);
    }

    /**
     * Add listenable future.
     *
     * @param multiPartElements the parts
     * @return the listenable future
     */
    public ListenableFuture<Void> add(List<MultiPartElement> multiPartElements) {
        String random = UUID.randomUUID().toString();

        Request request = getResolver().createRequest();

        byte[][] content = new byte[multiPartElements.size()][];

        for (int i = 0; i < multiPartElements.size(); i++) {
            MultiPartElement element = multiPartElements.get(i);

            List<Byte> header = new ArrayList<Byte>();
            String line = "--" + Constants.MULTIPART_BOUNDARY_NAME + random + Constants.HTTP_NEW_LINE;
            line += "Content-Disposition:form-data; name=" + element.getName() + Constants.HTTP_NEW_LINE;
            line += "Content-type:" + element.getContentType() + Constants.HTTP_NEW_LINE + Constants.HTTP_NEW_LINE;

            for (byte b : line.getBytes()) {
                header.add(b);
            }

            content[i] = Bytes.concat(Bytes.toArray(header), element.getContent(), Constants.HTTP_NEW_LINE.getBytes());
        }

        String closeLine = Constants.HTTP_NEW_LINE + "--" + Constants.MULTIPART_BOUNDARY_NAME + random + "--";


        request.addHeader(Constants.CONTENT_TYPE_HEADER, Constants.MULTIPART_CONTENT_TYPE + random );
        request.setContent(Bytes.concat(Bytes.concat(content), closeLine.getBytes()));

        request.setVerb(HttpVerb.POST);
        ListenableFuture<OrcResponse> future = oDataExecute(request);

        return transformToVoidListenableFuture(future);
    }


}

