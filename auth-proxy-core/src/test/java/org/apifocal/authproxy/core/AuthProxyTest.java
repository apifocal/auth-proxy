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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.net.URL;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apifocal.authproxy.api.QueryEditor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class AuthProxyTest extends Mockito {
    
    private static final String FILTER_TERM = "term";
    private static final String FILTER_VALUE = "testMe";

    //can't mock ctors, but we can mock methods
    //see also http://stackoverflow.com/questions/40420462/mockito-doanswer-thenreturn-in-one-method
    private BufferedReader getFileReader(String filename) throws Exception {
        URL url = this.getClass().getResource("/" + filename);
        File file = new File(url.getFile());
        FileReader fr = new FileReader(file);
        //FileReader fr = new FileReader(filename);
        BufferedReader br = new BufferedReader(fr);
        return br;
    }

    QueryEditor createSampleQueryEditor(String value) {
        return new ElasticSearchAuthQueryEditor() {
            @Override
            public ObjectNode createAuthFilterElement(HttpServletRequest req) {
                ObjectNode myFilter = mapper.createObjectNode();
                myFilter.put(FILTER_VALUE, new TextNode(value));

                ObjectNode f = mapper.createObjectNode();
                f.put(FILTER_TERM, myFilter);
                return f;
            }
        };
    }

    @Test
    public void testServletFilter() throws Exception {
        
        final String VALUE = "myValue";

        RewriteBodyServletFilter filter = new RewriteBodyServletFilter();
        filter.setQueryEditor(createSampleQueryEditor(VALUE));

        FilterConfig mockFilterConfig = Mockito.mock(FilterConfig.class);
        filter.init(mockFilterConfig);

        HttpServletRequest mockReq = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockReq.getReader()).thenReturn(getFileReader("sample.json"));

        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockFilterChain = Mockito.mock(FilterChain.class);
        filter.doFilter(mockReq, mockResp, mockFilterChain);
        
        final String newJson = filter.getRequestBody();

        Configuration conf = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();
        DocumentContext context = JsonPath.using(conf).parse(newJson);

        ObjectNode f = context.read("$.query.bool.filter");
        Assert.assertNotNull(f);

        ObjectNode term = (ObjectNode) f.get(FILTER_TERM);
        Assert.assertNotNull(term);

        JsonNode val = term.get(FILTER_VALUE);
        Assert.assertNotNull(val.asText().equals(VALUE));
    }

}
