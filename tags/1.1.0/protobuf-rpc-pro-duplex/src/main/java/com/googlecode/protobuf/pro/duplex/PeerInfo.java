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

import java.net.InetSocketAddress;

/**
 * PeerInfo is a value-object which represents a communicating peer.
 * 
 * The name of the PeerInfo represents a unique identifier of the Peer.
 *   for TcpServer's this is the hostName and TCP port on which the server is bound.
 *   for TcpClient's there is no local binding to a TCP port for incoming connection
 *    listening, however we can still assign or name a port which is unique
 *    for the client for naming purposes.
 * 
 * The PID of the process in which the TcpClient or TcpServer peer is running.
 * The PID is used to ascertain if the process has restarted, in which case the
 * previous in-memory state of the peer is lost. The knowledge that the peer has lost
 * previous state is important in scenarios where data is duplicated on both peers, or
 * one peer remembers what another peer knows.
 *  
 * @author Peter Klauser
 *
 */
public class PeerInfo {

	private String hostName;
	private int port;
	private String pid;
	
	/**
	 * 
	 * @param address
	 */
	public PeerInfo( InetSocketAddress localAddress ) {
		this(localAddress.getHostName(), localAddress.getPort());
	}
	
	/**
	 * Constructor to construct a PeerInfo of one's own process.
	 * 
	 * @param hostName
	 * @param port
	 */
	public PeerInfo( String hostName, int port ) {
		this.hostName = hostName;
		this.port = port;
		String pid = System.getProperty("pid","<NONE>");
		this.pid = pid;
	}

	/**
	 * Constructor used to construct a PeerInfo of some other process.
	 * 
	 * @param hostName
	 * @param port
	 * @param pid
	 */
	public PeerInfo( String hostName, int port, String pid ) {
		this.hostName = hostName;
		this.port = port;
		this.pid = pid;
	}
	
	/**
	 * The full PeerInfo description.
	 */
	public String toString() {
		return getName() + "[" + getPid() + "]";
	}
	
	/**
	 * Return the peer's name. Does not include PID, since the
	 * peer name is constant over process restarts.
	 * 
	 * @return hostName + ":" + port.
	 */
	public String getName() {
		return getHostName() + ":" + getPort();
	}
	
	/**
	 * @return the hostName
	 */
	public String getHostName() {
		return hostName;
	}
	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}
	/**
	 * @return the pid
	 */
	public String getPid() {
		return pid;
	}
	
	
}
