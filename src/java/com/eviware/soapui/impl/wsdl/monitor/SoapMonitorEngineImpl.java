/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.monitor;

import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.thread.BoundedThreadPool;

import com.eviware.soapui.impl.wsdl.actions.monitor.SoapMonitorAction;
import com.eviware.soapui.impl.wsdl.monitor.jettyproxy.HttpsProxyServlet;
import com.eviware.soapui.impl.wsdl.monitor.jettyproxy.ProxyServlet;
import com.eviware.soapui.impl.wsdl.monitor.jettyproxy.Server;
import com.eviware.soapui.model.settings.Settings;
import com.eviware.soapui.support.UISupport;

public class SoapMonitorEngineImpl implements SoapMonitorEngine
{

	Server server = new Server();
	SocketConnector connector = new SocketConnector();
	private SslSocketConnector sslConnector;
	private String sslEndpoint = null;

	public boolean isRunning()
	{
		return server.isRunning();
	}

	public void start(SoapMonitor soapMonitor, int localPort)
	{

		Settings settings = soapMonitor.getProject().getSettings();
		BoundedThreadPool threadPool = new BoundedThreadPool();
		threadPool.setMaxThreads(100);
		server.setThreadPool(threadPool);
		Context context = new Context(server, "/", 0);

		if (sslEndpoint != null)
		{
			sslConnector = new SslSocketConnector();
			sslConnector.setKeystore(settings.getString(SoapMonitorAction.LaunchForm.SSLTUNNEL_KEYSTORE, ""));
			sslConnector.setPassword(settings.getString(SoapMonitorAction.LaunchForm.SSLTUNNEL_PASSWORD, ""));
			sslConnector.setKeyPassword(settings.getString(SoapMonitorAction.LaunchForm.SSLTUNNEL_KEYPASSWORD, ""));
			sslConnector.setTruststore(settings.getString(SoapMonitorAction.LaunchForm.SSLTUNNEL_TRUSTSTORE, ""));
			sslConnector.setTrustPassword(settings.getString(SoapMonitorAction.LaunchForm.SSLTUNNEL_TRUSTSTORE_PASSWORD, ""));
			sslConnector.setMaxIdleTime(30000);
			sslConnector.setNeedClientAuth(false);
			sslConnector.setPort(localPort);

			server.addConnector(sslConnector);
			context.addServlet(new ServletHolder(new HttpsProxyServlet(soapMonitor, sslEndpoint)), "/");
		}
		else
		{
			connector.setPort(localPort);
			server.addConnector(connector);
			context.addServlet(new ServletHolder(new ProxyServlet(soapMonitor)), "/");
		}
		try
		{
			server.start();
		}
		catch (Exception e)
		{
			UISupport.showErrorMessage("Error starting monitor: " + e.getMessage());
		}

	}

	public void stop()
	{

		try
		{
			if (server != null)
			{
				server.stop();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (server != null)
			{
				server.destroy();
			}
		}

	}

	protected void setSslEndpoint(String sslEndpoint)
	{
		this.sslEndpoint = sslEndpoint;
	}

}
