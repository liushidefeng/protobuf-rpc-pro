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
package com.googlecode.protobuf.pro.duplex.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.googlecode.protobuf.pro.duplex.RpcClient;

/**
 * An RpcClientRegistry keeps an account of all connected RpcClients of an RpcServer.
 * 
 * Note: on the server side, an RpcClient's clientInfo is the server itself, and the
 * serverInfo represents the client.
 * 
 * @author Peter Klauser
 *
 */
public class RpcClientRegistry {
	
	private static Log log = LogFactory.getLog(RpcClientRegistry.class);
	
	private Map<String, RpcClient> clientNameMap = new ConcurrentHashMap<String, RpcClient>();
	
	public RpcClientRegistry() {
	}

	/**
	 * Attempt to register an RpcClient which has newly connected, during
	 * connection handshake. If the client is already connected, return false.
	 * 
	 * @param rpcClient
	 * @return true if registration is successful, false if already connected.
	 */
	public boolean registerRpcClient( RpcClient rpcClient ) {
		RpcClient existingClient = clientNameMap.get(rpcClient.getServerInfo().getName());
		if ( existingClient == null ) {
			clientNameMap.put(rpcClient.getServerInfo().getName(), rpcClient);
			return true;
		}
		if ( log.isDebugEnabled() ) {
			log.debug("RpcClient " + rpcClient.getServerInfo() + " is already registered with " + existingClient.getServerInfo());
		}
		return false;
	}
	
	/**
	 * Remove the RpcClient from the registry, at connection close time.
	 * 
	 * @param rpcClient
	 */
	public void removeRpcClient( RpcClient rpcClient ) {
		clientNameMap.remove(rpcClient.getServerInfo().getName());
	}
}
