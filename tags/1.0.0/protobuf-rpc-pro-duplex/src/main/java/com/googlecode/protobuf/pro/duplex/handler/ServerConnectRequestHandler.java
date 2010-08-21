/**
 *   Copyright 2010 Peter Klauser
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
*/
package com.googlecode.protobuf.pro.duplex.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import com.googlecode.protobuf.pro.duplex.PeerInfo;
import com.googlecode.protobuf.pro.duplex.RpcClient;
import com.googlecode.protobuf.pro.duplex.logging.RpcLogger;
import com.googlecode.protobuf.pro.duplex.server.DuplexTcpServerPipelineFactory;
import com.googlecode.protobuf.pro.duplex.server.RpcClientRegistry;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.ConnectErrorCode;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.ConnectRequest;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.ConnectResponse;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.WirePayload;

@Sharable
public class ServerConnectRequestHandler extends SimpleChannelUpstreamHandler {

	private static Log log = LogFactory.getLog(ServerConnectRequestHandler.class);

    private final PeerInfo serverInfo;
    private final RpcClientRegistry rpcClientRegistry;
    private final DuplexTcpServerPipelineFactory pipelineFactory;
    private final RpcLogger logger;
    
    public ServerConnectRequestHandler( PeerInfo serverInfo, RpcClientRegistry rpcClientRegistry, DuplexTcpServerPipelineFactory pipelineFactory, RpcLogger logger ) {
    	this.serverInfo = serverInfo;
    	this.rpcClientRegistry = rpcClientRegistry;
    	this.pipelineFactory = pipelineFactory;
    	this.logger = logger;
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if ( e.getMessage() instanceof WirePayload) {
        	ConnectRequest connectRequest = ((WirePayload)e.getMessage()).getConnectRequest();
    		if ( log.isDebugEnabled() ) {
    			log.debug("Received ["+connectRequest.getCorrelationId()+"]ConnectRequest.");
    		}
        	if ( connectRequest != null ) {
        		PeerInfo connectingClientInfo = new PeerInfo(connectRequest.getClientHostName(), connectRequest.getClientPort(), connectRequest.getClientPID());
        		ConnectResponse connectResponse = null;
        		
        		RpcClient rpcClient = new RpcClient(ctx.getChannel(), serverInfo, connectingClientInfo );
        		rpcClient.setCallLogger(logger);
        		if ( rpcClientRegistry.registerRpcClient(rpcClient) ) {
        			connectResponse = ConnectResponse.newBuilder().setCorrelationId(connectRequest.getCorrelationId()).setServerPID(serverInfo.getPid()).build();
            		WirePayload payload = WirePayload.newBuilder().setConnectResponse(connectResponse).build();
            		
            		if ( log.isDebugEnabled() ) {
            			log.debug("Sending ["+connectResponse.getCorrelationId()+"]ConnectResponse.");
            		}
            		ctx.getChannel().write(payload);
            		
            		// now we swap this Handler out of the pipeline and complete the server side pipeline.
            		RpcClientHandler clientHandler = pipelineFactory.completePipeline(rpcClient);
            		clientHandler.notifyOpened();
        		} else {
        			connectResponse = ConnectResponse.newBuilder().setCorrelationId(connectRequest.getCorrelationId()).setErrorCode(ConnectErrorCode.ALREADY_CONNECTED).build();
            		WirePayload payload = WirePayload.newBuilder().setConnectResponse(connectResponse).build();
            		
            		if ( log.isDebugEnabled() ) {
            			log.debug("Sending ["+connectResponse.getCorrelationId()+"]ConnectResponse. Already Connected.");
            		}
            		ctx.getChannel().write(payload);
            		
            		ctx.getChannel().close();
        		}
        		return;
        	}
        }
        ctx.sendUpstream(e);
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    	log.warn("Exception caught during RPC connection handshake.", e.getCause());
    	if ( ctx.getChannel().isConnected() ) {
    		ctx.getChannel().close();
    	}
    }
}
