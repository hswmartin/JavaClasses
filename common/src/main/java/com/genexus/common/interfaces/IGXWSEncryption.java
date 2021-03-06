package com.genexus.common.interfaces;

public interface IGXWSEncryption
{
	String getAlias();
	void setAlias(String alias);
	IGXWSSecurityKeyStore getKeystore();
	void setKeystore(IGXWSSecurityKeyStore keystore);
	int getKeyIdentifierType();
	void setKeyIdentifierType(int keyIdentifierType);	
}

