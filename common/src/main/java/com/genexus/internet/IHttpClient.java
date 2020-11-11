package com.genexus.internet;

import java.io.*;

public interface IHttpClient {
	public byte getBasic();
	public byte getDigest();
	public byte getNtlm();
	public short getErrCode();
	public String getErrDescription();
	public void setProxyServerHost(String host);
	public String getProxyServerHost();
	public void setProxyServerPort(long port);
	public short getProxyServerPort();
	public void setIncludeCookies(boolean value);
	public boolean getIncludeCookies();
	public void setURL(String stringURL);
	public void setHost(String host);
	public String getHost();
	public void setWSDLURL(String WSDLURL);
	public void setBaseURL(String baseURL);
	public String getWSDLURL();
	public String getBaseURL();
	public void setPort(int port);
	public int getPort();
	public byte getSecure();
	public void setSecure(int secure);
	public void setTimeout(int timeout);
	public int getTimeout();
	public void setTcpNoDelay(boolean tcpNoDelay);
	public void addAuthentication(int type, String realm, String name, String value);
	public void addProxyAuthentication(int type, String realm, String name, String value);
	public void addCertificate(String fileName);
	public void addHeader(String name, String value);
	public void addVariable(String name, String value);
	public void addBytes(byte[] value);
	public void addString(String value);
	public void addFile(String fileName);
	public void addFile(String fileName, String varName);
	public void addStringWriter(StringWriter writer, StringBuffer encoding);
	public void execute(String method, String url);
	public int getStatusCode();
	public String getReasonLine();
	public void getHeader(String name, long[] value);
	public String getHeader(String name);
	public void getHeader(String name, String[] value);
	public void getHeader(String name, java.util.Date[] value);
	public void getHeader(String name, double[] value);
	public InputStream getInputStream() throws IOException;
	public String getString();
	public void toFile(String fileName);
	public void cleanup();
}