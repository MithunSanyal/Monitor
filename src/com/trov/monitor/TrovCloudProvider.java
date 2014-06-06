package com.trov.monitor;

import java.util.Enumeration;
import java.util.Properties;

import org.dasein.cloud.*;

public class TrovCloudProvider
{

	public CloudProvider getCloudProvider(CloudAccount ca,String regionid)throws Exception
	{ 
		String publicKey = ca.getApiSharedKey();
		String privateKey = ca.getApiSecretKey();
		String accountNumber =ca.getCloudAccountNumber();
		String endpoint = ca.getEndPoint();
		String cloudName = ca.getCloudName();
		String providerName = ca.getCloudName();
		String providerClass = "";
		System.out.println("Cloud name  = " + ca.getCloudName() );

		if (ca.getCloudName().equals("aws") ) providerClass = "org.dasein.cloud.aws.AWSCloud";
		if (ca.getCloudName().equals("opsource") ) providerClass = "org.dasein.cloud.opsource.OpSource";
		if (ca.getCloudName().equals("google") ) providerClass = "org.dasein.cloud.google.Google";		
		
		CloudProvider provider = (CloudProvider) Class.forName(providerClass).newInstance();
		ProviderContext ctx = new ProviderContext();
		Properties props = new Properties();
		
		if( publicKey != null && privateKey != null ) 
		{
			ctx.setAccessKeys(publicKey.getBytes(), privateKey.getBytes());
			System.out.println(ctx.getAccessPrivate().toString());
		}
		accountNumber = ca.getCloudAccountNumber();
		ctx.setAccountNumber(accountNumber);
		ctx.setCloudName(cloudName);
		ctx.setEndpoint(endpoint);
		ctx.setProviderName(providerName);
		ctx.setRegionId(regionid);
		Properties custom = new Properties();
		Enumeration<?> names = props.propertyNames();
		while( names.hasMoreElements() ) {
			String name = (String)names.nextElement();
			custom.setProperty(name, props.getProperty(name));
		}
		ctx.setCustomProperties(custom);
		provider.connect(ctx);
		return provider;
	}

}
