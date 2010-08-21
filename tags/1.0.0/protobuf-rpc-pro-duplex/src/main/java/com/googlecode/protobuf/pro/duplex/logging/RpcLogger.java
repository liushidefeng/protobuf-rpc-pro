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
package com.googlecode.protobuf.pro.duplex.logging;

import com.google.protobuf.Message;
import com.googlecode.protobuf.pro.duplex.PeerInfo;
import com.googlecode.protobuf.pro.duplex.logging.RpcLogEntry.RpcPayloadInfo;

/**
 * Interface of a Logging facility which logs each RPC call at completion time.
 * 
 * Since we only log at call completion time, a "log-file" will not list 
 * calls as they happen sequentially. The true call sequence and how calls
 * overlap will can be determined from the timestamp of the request and
 * response payloads.
 * 
 * @author Peter Klauser
 *
 */
public interface RpcLogger {

	/**
	 * Log a single RPC call.
	 * 
	 * @param client the initiator of the RPC call.
	 * @param server the server of the RPC all.
	 * @param signature the service method called.
	 * @param request protobuf.
	 * @param response protobuf.
	 * @param errorMessage if an error was signaled
	 * @param correlationId the correlationId.
	 * @param reqInfo payload information of the request.
	 * @param resInfo payload information of the response.
	 */
	public void logCall( PeerInfo client, PeerInfo server, String signature, Message request, Message response, String errorMessage, int correlationId, RpcPayloadInfo reqInfo, RpcPayloadInfo resInfo );
	
}
