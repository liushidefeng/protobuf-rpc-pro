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
package com.googlecode.protobuf.pro.duplex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import com.googlecode.protobuf.pro.duplex.execute.PendingServerCallState;
import com.googlecode.protobuf.pro.duplex.execute.RpcServerCallExecutor;
import com.googlecode.protobuf.pro.duplex.execute.RpcServerExecutorCallback;
import com.googlecode.protobuf.pro.duplex.execute.ServerRpcController;
import com.googlecode.protobuf.pro.duplex.logging.RpcLogEntry.RpcPayloadInfo;
import com.googlecode.protobuf.pro.duplex.logging.RpcLogger;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.RpcCancel;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.RpcError;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.RpcRequest;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.RpcResponse;
import com.googlecode.protobuf.pro.duplex.wire.DuplexProtocol.WirePayload;

/**
 * An RpcServer handles incoming RpcRequests from the IO-Layer
 * by looking up the Service implementation in the RpcServiceRegistry 
 * and using the RpcServerCallExecutor to perform the service call.
 * The final RpcResponse is then sent back to the client over the
 * IO-Layer.
 *  
 * @author Peter Klauser
 * 
 */
public class RpcServer implements RpcServerExecutorCallback {

	private static Log log = LogFactory.getLog(RpcServer.class);

	private final Map<Integer, PendingServerCallState> pendingServerCallMap = new ConcurrentHashMap<Integer, PendingServerCallState>();

	private final RpcClient rpcClient;
	private final RpcServiceRegistry rpcServiceRegistry;
	private final RpcServerCallExecutor callExecutor;
	private final RpcLogger logger;
	
	public RpcServer(RpcClient rcpClient, RpcServiceRegistry rpcServiceRegistry, RpcServerCallExecutor callExecutor, RpcLogger logger ) {
		this.rpcClient = rcpClient;
		this.rpcServiceRegistry = rpcServiceRegistry;
		this.callExecutor = callExecutor;
		this.logger = logger;
	}

	public void request(RpcRequest rpcRequest) {
		long startTS = System.currentTimeMillis();
		int correlationId = rpcRequest.getCorrelationId();

		if ( log.isDebugEnabled() ) {
			log.debug("Received ["+rpcRequest.getCorrelationId()+"]RpcRequest.");
		}

		if (callExecutor == null) {
			String errorMessage = "No Executor";
			RpcError rpcError = RpcError.newBuilder()
					.setCorrelationId(correlationId)
					.setErrorMessage(errorMessage).build();
			WirePayload payload = WirePayload.newBuilder()
					.setRpcError(rpcError).build();

			if ( log.isDebugEnabled() ) {
				log.debug("Sending ["+rpcError.getCorrelationId()+"]RpcError.");
			}
			rpcClient.getChannel().write(payload);

			doErrorLog(correlationId, "Unknown", rpcRequest, rpcError, errorMessage);
			return;
		}
		
		if (pendingServerCallMap.containsKey(correlationId)) {
			throw new IllegalStateException("correlationId " + correlationId
					+ " already registered as PendingServerCall.");
		}

		Service service = rpcServiceRegistry.resolveService(rpcRequest
				.getServiceIdentifier());
		if (service == null) {
			String errorMessage = "Unknown Service";
			RpcError rpcError = RpcError.newBuilder()
					.setCorrelationId(correlationId)
					.setErrorMessage(errorMessage).build();
			WirePayload payload = WirePayload.newBuilder()
					.setRpcError(rpcError).build();

			if ( log.isDebugEnabled() ) {
				log.debug("Sending ["+rpcError.getCorrelationId()+"]RpcError.");
			}
			rpcClient.getChannel().write(payload);

			doErrorLog(correlationId, "Unknown", rpcRequest, rpcError, errorMessage);
			return;
		}
		MethodDescriptor methodDesc = service.getDescriptorForType()
				.findMethodByName(rpcRequest.getMethodIdentifier());
		if (methodDesc == null) {
			String errorMessage = "Unknown Method";
			RpcError rpcError = RpcError.newBuilder()
					.setCorrelationId(correlationId)
					.setErrorMessage(errorMessage).build();
			WirePayload payload = WirePayload.newBuilder()
					.setRpcError(rpcError).build();

			if ( log.isDebugEnabled() ) {
				log.debug("Sending ["+rpcError.getCorrelationId()+"]RpcError.");
			}
			rpcClient.getChannel().write(payload);

			doErrorLog(correlationId, "Unknown", rpcRequest, rpcError, errorMessage);
			return;
		}
		Message requestPrototype = service.getRequestPrototype(methodDesc);

		Message request = null;
		try {
			request = requestPrototype.newBuilderForType()
					.mergeFrom(rpcRequest.getRequestBytes()).build();

		} catch (InvalidProtocolBufferException e) {
			String errorMessage = "Invalid Request Protobuf";

			RpcError rpcError = RpcError.newBuilder()
					.setCorrelationId(correlationId)
					.setErrorMessage(errorMessage).build();
			WirePayload payload = WirePayload.newBuilder()
					.setRpcError(rpcError).build();

			if ( log.isDebugEnabled() ) {
				log.debug("Sending ["+rpcError.getCorrelationId()+"]RpcError.");
			}
			rpcClient.getChannel().write(payload);

			doErrorLog(correlationId, methodDesc.getFullName(), rpcRequest, rpcError, errorMessage);
			return;
		}
		ServerRpcController controller = new ServerRpcController(rpcClient,
					correlationId);

		PendingServerCallState state = new PendingServerCallState(this,
					service, controller, methodDesc, request, startTS);
		pendingServerCallMap.put(correlationId, state);

		callExecutor.execute(state);
	}

	/**
	 * On cancel from the client, the RpcServer does not expect to receive a
	 * callback anymore from the RpcServerCallExecutor.
	 * 
	 * @param rpcCancel
	 */
	public void cancel(RpcCancel rpcCancel) {
		int correlationId = rpcCancel.getCorrelationId();

		if (callExecutor == null) {
			return;
		}
		PendingServerCallState state = pendingServerCallMap
				.remove(correlationId);
		if (state != null) {
			// we only issue one cancel to the Executor
			callExecutor.cancel(state.getExecutor());

			if ( log.isDebugEnabled() ) {
				log.debug("Received ["+rpcCancel.getCorrelationId()+"]RpcCancel.");
			}
			doLog(state, rpcCancel, "Cancelled");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.googlecode.protobuf.pro.duplex.execute.RpcServerExecutorCallback#onFinish
	 * (int,Message,String)
	 */
	@Override
	public void onFinish(int correlationId, Message message) {
		PendingServerCallState state = pendingServerCallMap.remove(correlationId);
		if (state != null) {
			// finished successfully, or failed - respond
			if (message != null) {
				RpcResponse rpcResponse = RpcResponse.newBuilder()
						.setCorrelationId(correlationId)
						.setResponseBytes(message.toByteString()).build();
				WirePayload payload = WirePayload.newBuilder()
						.setRpcResponse(rpcResponse).build();

				if ( log.isDebugEnabled() ) {
					log.debug("Sending ["+rpcResponse.getCorrelationId()+"]RpcResponse.");
				}
				rpcClient.getChannel().write(payload);

				doLog(state, message, null);
			} else {
				String errorMessage = state.getController().getFailed();
				RpcError rpcError = RpcError.newBuilder()
						.setCorrelationId(correlationId)
						.setErrorMessage(errorMessage).build();
				WirePayload payload = WirePayload.newBuilder()
						.setRpcError(rpcError).build();

				if ( log.isDebugEnabled() ) {
					log.debug("Sending ["+rpcError.getCorrelationId()+"]RpcError.");
				}
				rpcClient.getChannel().write(payload);
				
				doLog(state, rpcError, errorMessage);
			}
		} else {
			// RPC call canceled by client - we don't respond
		}
	}

	/**
	 * Cancel any pending server calls due to closure of the RpcClient.
	 */
	public void handleClosure() {
		List<Integer> pendingCallIds = new ArrayList<Integer>();
		pendingCallIds.addAll(pendingServerCallMap.keySet());
		do {
			for( Integer correlationId : pendingCallIds ) {
				PendingServerCallState state = pendingServerCallMap.remove(correlationId);
				if (state != null) {
					// we only issue one cancel to the Executor
					callExecutor.cancel(state.getExecutor());

					RpcCancel rpcCancel = RpcCancel.newBuilder().setCorrelationId(correlationId).build();

					if ( log.isDebugEnabled() ) {
						log.debug("Cancel on close ["+rpcCancel.getCorrelationId()+"]RpcCancel.");
					}
					doLog(state, rpcCancel, "Cancelled on Close");
				}
			}
		} while( pendingServerCallMap.size() > 0 );
	}
	
	protected void doErrorLog( int correlationId, String signature, Message request, Message response, String errorMessage ) {
		if ( logger != null ) {
			RpcPayloadInfo reqInfo = RpcPayloadInfo.newBuilder().setSize(request.getSerializedSize()).setTs(System.currentTimeMillis()).build();
			RpcPayloadInfo resInfo = RpcPayloadInfo.newBuilder().setSize(response.getSerializedSize()).setTs(System.currentTimeMillis()).build();
			logger.logCall(rpcClient.getClientInfo(), rpcClient.getServerInfo(), signature, request, response, errorMessage, correlationId, reqInfo, resInfo);
		}
	}
	
	protected void doLog( PendingServerCallState state, Message response, String errorMessage ) {
		if ( logger != null ) {
			RpcPayloadInfo reqInfo = RpcPayloadInfo.newBuilder().setSize(state.getRequest().getSerializedSize()).setTs(state.getStartTS()).build();
			RpcPayloadInfo resInfo = RpcPayloadInfo.newBuilder().setSize(response.getSerializedSize()).setTs(System.currentTimeMillis()).build();
			logger.logCall(rpcClient.getClientInfo(), rpcClient.getServerInfo(), state.getMethodDesc().getFullName(), state.getRequest(), response, errorMessage, state.getController().getCorrelationId(), reqInfo, resInfo);
		}
	}
	
	/**
	 * @return the rcpClient
	 */
	public RpcClient getRcpClient() {
		return rpcClient;
	}

	/**
	 * @return the rpcServiceRegistry
	 */
	public RpcServiceRegistry getRpcServiceRegistry() {
		return rpcServiceRegistry;
	}

}
