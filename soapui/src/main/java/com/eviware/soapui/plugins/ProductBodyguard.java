package com.eviware.soapui.plugins;

import com.eviware.soapui.SoapUI;

import java.io.File;
import java.security.Provider;

public final class ProductBodyguard extends Provider {

    public ProductBodyguard() {
        super("SoapUIOSPluginSignChecker", 1.0, "Plugin signature validity checker");
    }

    public final synchronized boolean isKnown(File plugin) {
        return true;
    }
}
