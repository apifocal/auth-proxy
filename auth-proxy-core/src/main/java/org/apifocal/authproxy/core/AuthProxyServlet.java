/*
 * Copyright 2017 apifocal LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apifocal.authproxy.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * forwards an http request to a remote server
 *
 * note: RequestDispatcher class only forwards to a local url
 */
@Component(
        immediate = true,
        property = {"alias:String=/authproxy"},
        service = {Servlet.class},
        properties = "OSGI-INF/AuthProxy.properties",
        configurationPid = "org.apifocal.authproxy.AuthProxyServlet",
        configurationPolicy = ConfigurationPolicy.OPTIONAL
)
public class AuthProxyServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AuthProxyServlet.class);
    private static final AtomicInteger EXCHANGE_COUNTER = new AtomicInteger(0);
    
    private URL proxyURL;
    
    private void loadConfig(Map<String, Object> properties) {
        try {
            proxyURL = new URL(properties.get("proxyURL").toString());
        } catch (MalformedURLException ex) {
            LOG.error("invalid proxy url", ex);
        }        
    }

    @Activate
    void onActivate(Map<String, Object> properties) {
        loadConfig(properties);
    }
    
    @Modified
    void onModified(Map<String, Object> properties) {
        loadConfig(properties);
    }
    
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        int id = EXCHANGE_COUNTER.incrementAndGet();

        HttpURLConnection con = (HttpURLConnection) proxyURL.openConnection();

        con.setRequestMethod(req.getMethod());

        for (Enumeration headers = req.getHeaderNames(); headers.hasMoreElements();) {
            String headerName = (String) headers.nextElement();
            StringBuilder headerListValue = null;
            for (Enumeration headerValues = req.getHeaders(headerName); headerValues.hasMoreElements();) {
                String headerValue = (String) headerValues.nextElement();
                if (headerListValue == null) {
                    headerListValue = new StringBuilder(headerValue);
                } else {
                    headerListValue.append(',');
                    headerListValue.append(headerValue);
                }
            }
            con.setRequestProperty(headerName, headerListValue.toString());
        }

        String reqBody = null;
        try (ServletInputStream reqIs = req.getInputStream()) {
            reqBody = readInputStream(reqIs, req.getCharacterEncoding());
        }

        LOG.info("Forwarding request {}: {} {} to {}", id, req.getMethod(), reqBody, proxyURL);

        if (reqBody != null) {
            con.setDoOutput(true);
            try (OutputStream reqOs = con.getOutputStream()) {
                reqOs.write(reqBody.getBytes(req.getCharacterEncoding()));
                reqOs.flush();
            }
        }

        // response processing
        int statusCode = con.getResponseCode();
        resp.setStatus(statusCode);
        boolean responseOk = statusCode >= 200 && statusCode < 300;

        String respBody = null;
        try (InputStream respIs = responseOk ? con.getInputStream() : con.getErrorStream()) {
            respBody = readInputStream(respIs, con.getContentEncoding());
        }
        LOG.info("Forwarding response {}: {} from {}", id, respBody, proxyURL);

        for (Map.Entry<String, List<String>> header : con.getHeaderFields().entrySet()) {
            String headerName = header.getKey();
            if (headerName != null) {
                for (String headerValue : header.getValue()) {
                    resp.addHeader(headerName, headerValue);
                }
            }
        }

        if (respBody != null) {
            try (OutputStream respOs = resp.getOutputStream()) {
                respOs.write(respBody.getBytes(/*con.getContentEncoding()*/));
                respOs.flush();
            }
        }
    }

    private String readInputStream(InputStream is, String charset) throws IOException {
        StringWriter reqStringWriter = new StringWriter();
        IOUtils.copy(is, reqStringWriter, charset);
        return reqStringWriter.toString();
    }
}
