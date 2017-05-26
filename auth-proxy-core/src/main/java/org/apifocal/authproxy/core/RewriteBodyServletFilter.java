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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apifocal.authproxy.api.QueryEditor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * http://www.journaldev.com/1933/java-servlet-filter-example-tutorial
 * http://www.oracle.com/technetwork/java/filters-137243.html#close
 *
 * http://www.codejava.net/java-ee/servlet/webfilter-annotation-examples
 *
 * https://github.com/ops4j/org.ops4j.pax.web/blob/master/samples/whiteboard-blueprint/src/main/resources/OSGI-INF/blueprint/blueprint.xml
 *
 * https://forums.adobe.com/thread/1183328
 * http://lists.amdatu.org/pipermail/users/2015-May/000326.html
 *
 * http://blog.vogella.com/2016/09/26/configuring-osgi-declarative-services/
 * https://web.liferay.com/community/forums/-/message_boards/message/82706558
 * https://github.com/liferay/liferay-portal/blob/master/modules/apps/foundation/frontend-js/frontend-js-spa-web/src/main/java/com/liferay/frontend/js/spa/web/internal/servlet/filter/SPAFilter.java#L38
 *
 */
//@WebFilter(
//        urlPatterns = {"/*"}
//)
@Component(
        immediate = true,
        property = {
            "dispatcher=REQUEST",
            "servlet-context-name=",
            "servlet-filter-name=RewriteBodyServletFilter",
            "urlPatterns=/*"
        },
        service = Filter.class
)
public class RewriteBodyServletFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthProxyServlet.class);

    private ServletContext context;

    private QueryEditor queryEditor;

    private String updatedBody;

    public QueryEditor getQueryEditor() {
        return queryEditor;
    }

    @Reference
    void setQueryEditor(QueryEditor qe) {
        this.queryEditor = qe;
    }

    String getRequestBody() {
        return updatedBody;
    }

    void setRequestBody(String body) {
        updatedBody = body;
    }

    @Override
    public void init(FilterConfig fc) throws ServletException {
        this.context = fc.getServletContext();
        //this.context.log("QueryEditorFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain fc) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        ResettableStreamHttpServletRequest wrappedRequest = new ResettableStreamHttpServletRequest(req, this);

        //consume body, for testing purposes only - TODO - remove this
        String body = IOUtils.toString(wrappedRequest.getReader());
        wrappedRequest.resetInputStream();
        LOG.info("Rewrite request body: {}", body);

        fc.doFilter(wrappedRequest, response);
    }

    @Override
    public void destroy() {
        queryEditor = null;
    }

    private static class ResettableStreamHttpServletRequest extends HttpServletRequestWrapper {

        private final HttpServletRequest request;
        private final RewriteBodyServletFilter filter;
        private ResettableServletInputStream servletStream;
        private byte[] rawData;

        public ResettableStreamHttpServletRequest(HttpServletRequest request, RewriteBodyServletFilter filter) {
            super(request);
            this.request = request;
            this.filter = filter;
            this.servletStream = new ResettableServletInputStream();
        }

        public void resetInputStream() {
            servletStream.stream = new ByteArrayInputStream(rawData);
        }

        private void rewriteInputStream() {
            String updatedBody = filter.getQueryEditor().updateRequest(this.request);
            filter.setRequestBody(updatedBody);

            rawData = updatedBody.getBytes();
            resetInputStream();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (rawData == null) {
                rewriteInputStream();
            }
            return servletStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (rawData == null) {
                rewriteInputStream();
            }
            return new BufferedReader(new InputStreamReader(servletStream));
        }

        private class ResettableServletInputStream extends ServletInputStream {

            private InputStream stream;

            @Override
            public boolean isFinished() {
                try {
                    return stream.available() == 0;
                } catch (IOException ex) {
                    return false;
                }
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return stream.read();
            }

        }

    }

}
