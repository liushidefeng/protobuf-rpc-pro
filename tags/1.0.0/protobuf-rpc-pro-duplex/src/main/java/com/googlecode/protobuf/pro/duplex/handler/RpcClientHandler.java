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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import com.googlecode.protobuf.pro.duplex.RpcClient;
import com.googlecode.protobuf.pro.duplex.listener.TcpConnectionEventListener;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.WirePayload;

/**
 * Handles returning RpcResponse and RpcError messages
 * in the IO-Layer, delegating them to the Netty
 * Channel's RpcClient.
 * 
 * @author Peter Klauser
 *
 */
public class RpcClientHandler extends SimpleChannelUpstreamHandler {

    private RpcClient rpcClient;
    private TcpConnectionEventListener eventListener;
    
    public RpcClientHandler(RpcClient rpcClient, TcpConnectionEventListener eventListener ) {
    	if ( rpcClient == null ) {
    		throw new IllegalArgumentException("rpcClient");
    	}
    	if ( eventListener == null ) {
    		throw new IllegalArgumentException("eventListener");
    	}
    	this.eventListener = eventListener;
    	this.rpcClient = rpcClient;
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if ( e.getMessage() instanceof WirePayload) {
        	WirePayload payload = (WirePayload)e.getMessage();
        	if ( payload.hasRpcResponse() ) {
        		rpcClient.response(payload.getRpcResponse());
        		return;
        	} else if ( payload.hasRpcError() ) {
        		rpcClient.error(payload.getRpcError());
        		return;
        	}
        	// rpcRequest, rpcCancel go further up to the RpcServerHandler
        }
        ctx.sendUpstream(e);
    }

    @Override
    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
        rpcClient.handleClosure();
        notifyClosed();
    }

    public void notifyClosed() {
    	eventListener.connectionClosed(rpcClient);
    }

    public void notifyOpened() {
    	eventListener.connectionOpened(rpcClient);
    }
    
	/**
	 * @return the rpcClient
	 */
	public RpcClient getRpcClient() {
		return rpcClient;
	}

}
