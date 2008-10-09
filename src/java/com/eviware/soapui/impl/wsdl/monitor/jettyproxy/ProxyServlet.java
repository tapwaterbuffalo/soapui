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
package com.eviware.soapui.impl.wsdl.monitor.jettyproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.HashSet;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.HttpSchemes;
import org.mortbay.jetty.client.HttpClient;
import org.mortbay.util.ajax.Continuation;
import org.mortbay.util.ajax.ContinuationSupport;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.monitor.JProxyServletWsdlMonitorMessageExchange;
import com.eviware.soapui.impl.wsdl.monitor.SoapMonitor;

public class ProxyServlet implements Servlet
{

	protected ServletConfig config;
	protected ServletContext context;
	protected HttpClient client;
	protected JProxyServletWsdlMonitorMessageExchange capturedData;
	protected SoapMonitor monitor;
	protected WsdlProject project;

	static HashSet<String> dontProxyHeaders = new HashSet<String>();
	{
		dontProxyHeaders.add("proxy-connection");
		dontProxyHeaders.add("connection");
		dontProxyHeaders.add("keep-alive");
		dontProxyHeaders.add("transfer-encoding");
		dontProxyHeaders.add("te");
		dontProxyHeaders.add("trailer");
		dontProxyHeaders.add("proxy-authorization");
		dontProxyHeaders.add("proxy-authenticate");
		dontProxyHeaders.add("upgrade");
	}

	public ProxyServlet(SoapMonitor soapMonitor)
	{
		this.monitor = soapMonitor;
		this.project = monitor.getProject();
	}

	public void destroy()
	{
	}

	public ServletConfig getServletConfig()
	{
		return config;
	}

	public String getServletInfo()
	{
		return "SoapUI Monitor";
	}

	public void init(ServletConfig config) throws ServletException
	{

		this.config = config;
		this.context = config.getServletContext();

		client = new HttpClient();
		client.setConnectorType(HttpClient.CONNECTOR_SOCKET);// CONNECTOR_SELECT_CHANNEL);
		try
		{
			client.start();
		}
		catch (Exception e)
		{
			throw new ServletException(e);
		}

	}

	public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException
	{

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		if (capturedData == null)
		{
			capturedData = new JProxyServletWsdlMonitorMessageExchange(project);
			capturedData.setRequestHost(httpRequest.getServerName());
			capturedData.setTargetHost(httpRequest.getRemoteHost());
			capturedData.setRequestHeader(httpRequest);
		}

		CaptureInputStream capture = new CaptureInputStream(httpRequest.getInputStream());
		Continuation continuation = ContinuationSupport.getContinuation(httpRequest, httpRequest);

		if (!continuation.isPending())
		{
			String uri = httpRequest.getRequestURI();
			if (httpRequest.getQueryString() != null)
				uri += "?" + httpRequest.getQueryString();

			SoapUIHttpExchange exchange = new SoapUIHttpExchange(monitor, httpRequest, httpResponse, capture, capturedData);

			exchange.setScheme(HttpSchemes.HTTPS.equals(request.getScheme()) ? HttpSchemes.HTTPS_BUFFER
					: HttpSchemes.HTTP_BUFFER);
			exchange.setMethod(httpRequest.getMethod());
			exchange.setURI(uri);
			capturedData.setTargetURL(httpRequest.getRequestURL().toString());

			exchange.setVersion(httpRequest.getProtocol());
			InetSocketAddress address = new InetSocketAddress(httpRequest.getServerName(), httpRequest.getServerPort());
			exchange.setAddress(address);

			// check connection header
			String connectionHeader = httpRequest.getHeader("Connection");
			if (connectionHeader != null)
			{
				connectionHeader = connectionHeader.toLowerCase();
				if (connectionHeader.indexOf("keep-alive") < 0 && connectionHeader.indexOf("close") < 0)
					connectionHeader = null;
			}

			// copy headers
			boolean xForwardedFor = false;
			boolean hasContent = false;
			@SuppressWarnings("unused")
			long contentLength = -1;
			Enumeration<?> headerNames = httpRequest.getHeaderNames();
			while (headerNames.hasMoreElements())
			{
				String hdr = (String) headerNames.nextElement();
				String lhdr = hdr.toLowerCase();

				if (dontProxyHeaders.contains(lhdr))
					continue;
				if (connectionHeader != null && connectionHeader.indexOf(lhdr) >= 0)
					continue;

				if ("content-type".equals(lhdr))
					hasContent = true;
				if ("content-length".equals(lhdr))
					contentLength = request.getContentLength();

				Enumeration<?> vals = httpRequest.getHeaders(hdr);
				while (vals.hasMoreElements())
				{
					String val = (String) vals.nextElement();
					if (val != null)
					{
						exchange.setRequestHeader(lhdr, val);
						xForwardedFor |= "X-Forwarded-For".equalsIgnoreCase(hdr);
					}
				}
			}

			// Proxy headers
			exchange.setRequestHeader("Via", "SoapUI Monitor");
			if (!xForwardedFor)
				exchange.addRequestHeader("X-Forwarded-For", request.getRemoteAddr());

			if (hasContent)
			{
				exchange.setRequestContentSource(capture);
			}
			client.send(exchange);
			continuation.suspend(3000);
		}
		//wait for transaction to end and store it.
		while(capturedData.isStopCapture());
		if (!capturedData.isStopCapture())
		{
			monitor.addMessageExchange(capturedData);
			capturedData = null;
		}
	}

}
