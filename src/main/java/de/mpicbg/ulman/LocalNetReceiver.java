/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;

import net.imagej.ImgPlus;

import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import de.mpicbg.ulman.ImgPacker;

@Plugin(type = Command.class, menuPath = "DAIS>Local Network Image Receiver")
public class LocalNetReceiver implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter(type = ItemIO.OUTPUT)
	private ImgPlus<?> imgP;

	@Parameter(visibility = ItemVisibility.MESSAGE, initializer="getHostURL")
	private String hostURLmsg = "";

	private String hostURL = "";
	void getHostURL() throws UnknownHostException
	{
		hostURL = InetAddress.getLocalHost().getHostAddress();
		hostURLmsg = "Please, tell your sending partner to use this for the address: ";
		hostURLmsg += hostURL;
	}

	@Parameter(label = "port to listen at:",
			description = "The port number should be higher than"
			+" 1024 such as 54545. It is important not to use any spaces.")
	private String portNo = "54545";

	@Parameter(label = "listening timeout in seconds:",
			description = "The maximum time in seconds during which Fiji waits"
			+" for incomming connection. If nothing comes after this period of time,"
			+" the listening is stopped until this command is started again.",
			min="1")
	private int timeoutTime = 60;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String firewallMsg = "Make sure the firewall is not blocking incomming connections to Fiji.";

	@Override
	public void run()
	{
		log.info("receiver started");

		//init the communication side
		ZMQ.Context zmqContext = ZMQ.context(1);
		ZMQ.Socket listenerSocket = null;
		try {
			//port to listen for incomming data
			listenerSocket = zmqContext.socket(ZMQ.PULL);
			//listenerSocket.bind("tcp://"+hostURL+":"+portNo);
			listenerSocket.bind("tcp://*:" + portNo); //until hostURL is retrieved reliably

			log.info("receiver waiting");

			//"an entry point" for the input data
			byte[] incomingData = null;

			//"busy wait" up to the given period of time
			int timeAlreadyWaited = 0;
			while (timeAlreadyWaited < timeoutTime && incomingData == null) {
				log.info("receiver read attempt no. " + timeAlreadyWaited);

				//check if there is some data from a sender
				incomingData = listenerSocket.recv(ZMQ.NOBLOCK);

				//if nothing found, wait a while before another checking attempt
				try {
					if (incomingData == null) Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				++timeAlreadyWaited;
			}

			//process incoming data if there is some...
			if (incomingData != null) {
				final ImgPacker<?> ip = new ImgPacker<>();
				//this guy returns the ImgPlus that we desire...
				imgP = ip.receiveAndUnpack(new String(incomingData), listenerSocket);
			}

			log.info("receiver closed");
		}
		catch (ZMQException e) {
			//log.error(e);
			log.info("receiver crashed");
		}
		catch (RuntimeException e) {
			System.out.println("Error: " + e.getMessage());
			e.printStackTrace();
		}
		finally {
			if (listenerSocket != null)
				listenerSocket.close();
			zmqContext.term();
		}
	}

	private void runWithZmqContext() {
	}
}
