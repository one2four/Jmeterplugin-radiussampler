package com.ipass.jmeterplugin.radiussampler;

import java.util.Hashtable;
import java.util.Random;

import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.TestElementProperty;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusClient;

public class RadiusSampler extends AbstractSampler
{

	private static Random random = new Random();

	private Hashtable<String, RadiusClient> radiusclients = new Hashtable<String, RadiusClient>(300);

	public static void main(String[] args) {
		String str=frameSessionId("pavan@ipass.com");
		System.out.println(str);
	}

	private static String genSessionId(int min,int max) {
		String val="";
	
		for(int i=0; i<8; i++) {
			int s=random.nextInt((max - min) + 1) + min;
			char c = (char)s;
			val+=c;
		}

		return val;
	}

	private static String frameSessionId(String username) {
		String str=genSessionId(97,120);
		return "0U"+str+"/"+username;
	}

	public SampleResult sample(Entry arg0)
	{
	    RadiusClient rcClient = null;
		String userName = getUserName();
		String password = getPassword();
		String serverIp = getServerIp();
		String sharedSecret = getSharedSecret();
		int authPort = getAuthPort();
		int acctPort = getAcctPort();

		int retryCount = getRetryCount();
		int timeout = getSocketTimeout();

		SampleResult res = new SampleResult();
		res.setSampleLabel(getName());
		if(authPort !=0 && acctPort !=0 ){
			res.setSamplerData("Host: " + getServerIp() + " Auth Port: " + getAuthPort() + " Acct Port: "+getAcctPort());
		}



		CollectionProperty collectionProperty=getAttributesManager().getAttributes();

		AddAttributes add = new AddAttributes();

		if((userName==null || userName.length()<=0 ) && collectionProperty!=null){
			userName = add.getRequiredAttribute(collectionProperty,"user-name");
		}

		if((password==null || password.length()<=0) && collectionProperty!=null){
			password = add.getRequiredAttribute(collectionProperty,"user-password");
		}		

        String hashkey = getThreadName() + ":" + getServerIp() + ":" + getAuthPort() + ":" + getAcctPort();

        if (radiusclients.containsKey(hashkey)) {
            rcClient = radiusclients.get(hashkey);
        }
        else {
            rcClient = new RadiusClient(serverIp,sharedSecret);
            rcClient.setAuthPort(authPort);
            rcClient.setAcctPort(acctPort);
            radiusclients.put(hashkey, rcClient);
        }
		
		res.sampleStart();

		if ( (userName!=null && userName.length()>0 && password!=null && password.length()>0 ) && (serverIp != null) && (serverIp.length() > 0) && (authPort!=0 || acctPort !=0) && (sharedSecret!=null && sharedSecret.length()>0))
		{

			if(System.getenv("GEN_SES_ID")!=null && System.getenv("GEN_SES_ID").toLowerCase().equals("true"))
				userName = frameSessionId(userName);

			try {
                String reqType = getRequestType();
                Boolean doAuth = (reqType.equalsIgnoreCase("auth") || reqType.equalsIgnoreCase("both")); 
                Boolean doAcct = (reqType.equalsIgnoreCase("acct") || reqType.equalsIgnoreCase("both"));
                AddAttributes addAttributes = new AddAttributes();

                if (timeout > 0) rcClient.setSocketTimeout(timeout);
                if (retryCount > 0) rcClient.setRetryCount(retryCount);
				
				if (doAuth) {
				    AccessRequest accessReq = new AccessRequest(userName, password);

                    if (collectionProperty != null)
                        accessReq = addAttributes.addAuthRadiusAttribute(accessReq, collectionProperty);

                    RadiusPacket authRadiusPacket = rcClient.authenticate(accessReq);
                    
                    if (authRadiusPacket != null) {
                        res.setSuccessful(true);
                        res.setResponseCodeOK();
                        res.setDataType("text");
                    }
                    else {
                        res.setSuccessful(false);
                        res.setResponseCode("500");
                        res.setResponseMessage("Server Dropped the auth request ");
                        
                        // do not try accounting
                        doAcct = false;
                    }
				}

				if (doAcct) {
                    //Start Records
				    AccountingRequest acctReq = new AccountingRequest(userName, AccountingRequest.ACCT_STATUS_TYPE_START);

                    if (collectionProperty != null)
                        acctReq = addAttributes.addAcctRadiusAttribute(acctReq, collectionProperty);
                    
                    RadiusPacket acctStartRadiusPacket = rcClient.account(acctReq);

                    if (acctStartRadiusPacket != null) {
                        //Stop records
                        acctReq = new AccountingRequest(userName, AccountingRequest.ACCT_STATUS_TYPE_STOP);

                        if (collectionProperty != null)
                            acctReq = addAttributes.addAcctRadiusAttribute(acctReq, collectionProperty);
                        
                        RadiusPacket acctStopRadiusPacket = rcClient.account(acctReq);

                        if (acctStopRadiusPacket != null) {
                            res.setSuccessful(true);
                            res.setResponseCodeOK();
                            res.setDataType("text");
                        }
                        else {
                            res.setSuccessful(false);
                            res.setResponseCode("500");
                            res.setResponseMessage("Server Dropped the Stop request ");
                        }
                    }                        
                    else {   
                        res.setSuccessful(false);
                        res.setResponseCode("500");
                        res.setResponseMessage("Server Dropped the Start request ");
                    }
				}
			}
			catch (Throwable excep)	{
				excep.printStackTrace();
				res.setSuccessful(false);
				res.setResponseMessage(excep.getMessage());
				res.setResponseCode("500");
				
				rcClient.close();
				radiusclients.remove(hashkey);
			}
			finally {
				res.sampleEnd();
			}
		}else{
			throw new NullPointerException("Some of the parameters like serverIp, port, shared secret cannot be null");
		}
		return res;
	}


	public void setAttributesManager(RadiusAttributesManager value)
	{
		setProperty(new TestElementProperty(RadiusSamplerElements.RADIUS_ATTRIBUTES, value));
	}

	public RadiusAttributesManager getAttributesManager()
	{
		return (RadiusAttributesManager)getProperty(RadiusSamplerElements.RADIUS_ATTRIBUTES).getObjectValue();
	}

	public void setServerIp(String serverIp)
	{
		setProperty(RadiusSamplerElements.SERVER_IP, serverIp);
	}

	public String getServerIp()
	{
		return getPropertyAsString(RadiusSamplerElements.SERVER_IP);
	}



	public void setSharedSecret(String sharedSecret)
	{
		setProperty(RadiusSamplerElements.SHARED_SECRET, sharedSecret);
	}

	public String getSharedSecret()
	{
		return getPropertyAsString(RadiusSamplerElements.SHARED_SECRET);
	}

	public int getRetryCount(){
		return getPropertyAsInt(RadiusSamplerElements.RADIUS_RETRY);
	}

	public void setRetryCount(int retryCount){
		setProperty(RadiusSamplerElements.RADIUS_RETRY,retryCount);
	}

	public int getSocketTimeout(){
		return getPropertyAsInt(RadiusSamplerElements.SOCKET_TIMEOUT);
	}

	public void setSocketTimeout(int socketTimeout){
		setProperty(RadiusSamplerElements.SOCKET_TIMEOUT,socketTimeout);
	}

	public void setAuthPort(int authPort){
		setProperty(RadiusSamplerElements.AUTH_PORT,authPort);
	}

	public void setAcctPort(int acctPort){
		setProperty(RadiusSamplerElements.ACCT_PORT,acctPort);
	}

	public int getAuthPort(){
		return getPropertyAsInt(RadiusSamplerElements.AUTH_PORT);
	}

	public int getAcctPort()
	{
		return getPropertyAsInt(RadiusSamplerElements.ACCT_PORT);
	}

	public void setRequestType(String requestType)
	{
		setProperty(RadiusSamplerElements.REQUEST_TYPE, requestType);
	}

	public String getRequestType()
	{
		return getPropertyAsString(RadiusSamplerElements.REQUEST_TYPE);
	}

	public void setUserName(String userName)
	{
		setProperty(RadiusSamplerElements.USER_NAME, userName);
	}

	public String getUserName()
	{
		return getPropertyAsString(RadiusSamplerElements.USER_NAME);
	}
	public void setPassword(String password)
	{
		setProperty(RadiusSamplerElements.PASSWORD, password);
	}

	public String getPassword()
	{
		return getPropertyAsString(RadiusSamplerElements.PASSWORD);
	}

}