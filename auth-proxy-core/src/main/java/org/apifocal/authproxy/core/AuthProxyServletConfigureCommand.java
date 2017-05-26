/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apifocal.authproxy.core;

import java.io.IOException;
import java.util.Hashtable;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/*
 * command that allows dynamic configuration for the servlet
 */
@Component(
    property = {
        "osgi.command.scope=auth-proxy",
        "osgi.command.function=configure"
    },
    service=AuthProxyServletConfigureCommand.class
)
public class AuthProxyServletConfigureCommand {
    
    //see AuthProxyServlet class' configurationPid
    private static final String CONFIGURATION_PID = "org.apifocal.authproxy.AuthProxyServlet";
 
    ConfigurationAdmin cm;
 
    @Reference
    void setConfigurationAdmin(ConfigurationAdmin cm) {
        this.cm = cm;
    }
 
    public void configure(String proxyURL) throws IOException {
        Configuration config = cm.getConfiguration(CONFIGURATION_PID);
        Hashtable<String, Object> props = new Hashtable<>();
        props.put("proxyURL", proxyURL);
        config.update(props);
    }
}