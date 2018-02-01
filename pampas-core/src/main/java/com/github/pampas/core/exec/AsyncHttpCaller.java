/*
 *
 *  *  Copyright 2009-2018.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package com.github.pampas.core.exec;

import com.github.pampas.core.exec.payload.HttpRequestInfo;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import org.asynchttpclient.*;
import org.asynchttpclient.uri.Uri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Async Http Caller.
 * Base on AsyncHttpClient
 *
 * @author: darrenfu
 * @date: 18-1-24
 */
public class AsyncHttpCaller implements Caller<HttpRequestInfo, Response> {
    private static final Logger log = LoggerFactory.getLogger(AsyncHttpCaller.class);

    private AsyncHttpClient client;

    @Override
    public Response call(HttpRequestInfo req) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " not support sync execute method");
    }

    @Override
    public CompletableFuture<Response> asyncCall(HttpRequestInfo req) {
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        FullHttpRequest httpRequest = req.getRequestData();
        try {


            final AsyncHttpClient httpClient = this.client;
            BoundRequestBuilder requestBuilder = new BoundRequestBuilder(httpClient,
                    httpRequest.getMethod().name(),
                    true);
            //TODO 路由,Filter
            requestBuilder.setUri(Uri.create("http://localhost:9001/test1"));
            requestBuilder.setHeaders(httpRequest.headers());
            requestBuilder.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

            if (httpRequest.content() != null && httpRequest.content().isReadable()) {
                //请求body转换为ByteBuffer，并且设置为只读，ByteBuf复用 堆内存中的数据 zero copy
                ByteBuffer readOnlyBuffer = httpRequest.content().nioBuffer().asReadOnlyBuffer();
                requestBuilder.setBody(readOnlyBuffer);
            }


            /**
             * listenableFuture.toCompletableFuture
             */
            ListenableFuture<Response> listenableFuture = requestBuilder.execute(new AsyncCompletionHandler<Response>() {
                @Override
                public Response onCompleted(Response response) throws Exception {
                    FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(httpRequest.getProtocolVersion(), HttpResponseStatus.valueOf(response.getStatusCode()),
                            //response.getResponseBodyAsByteBuffer是HeapByteBuf
                            // zero-copy 设置FullHttpResponse的body
                            Unpooled.wrappedBuffer(response.getResponseBodyAsByteBuffer()));
                    fullHttpResponse.headers().set(response.getHeaders());

                    sendResp(ctx, fullHttpResponse, HttpHeaders.isKeepAlive(httpRequest));

                    ReferenceCountUtil.release(req);
                    return response;
                }

                @Override
                public void onThrowable(Throwable t) {
                    System.out.println("httpclient-exception:" + t.getMessage());
                    System.out.println("httpclient--" + req.hashCode());
                    System.out.println("httpclient--" + req.getUri());
//                    System.out.println("httpclient--" + getBody(req));
//                    send(ctx, t.getMessage(), HttpResponseStatus.BAD_REQUEST);

                    ReferenceCountUtil.release(req);

                }

            });


        } catch (Exception ex) {
            System.out.println("exXXXXXXXXXXXXX");
            ex.printStackTrace();
        }


        return null;
    }


    private void sendResp(ChannelHandlerContext ctx, FullHttpResponse resp, boolean keepalive) {
        if (keepalive) {
            ctx.writeAndFlush(resp);
        } else {
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
