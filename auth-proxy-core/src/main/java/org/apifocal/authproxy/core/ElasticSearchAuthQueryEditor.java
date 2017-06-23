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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apifocal.authproxy.api.QueryEditor;
import org.slf4j.LoggerFactory;

/**
 * query editor implementation that adds a filter for authentication purposes
 */
public abstract class ElasticSearchAuthQueryEditor implements QueryEditor {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AuthProxyServlet.class);

    protected static final ObjectMapper mapper = new ObjectMapper();

    abstract protected ObjectNode createAuthFilterElement(HttpServletRequest req);

    @Override
    public String updateRequest(HttpServletRequest req) {

        try {
            final String body = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

            Configuration conf = Configuration.builder()
                    .jsonProvider(new JacksonJsonNodeJsonProvider())
                    .mappingProvider(new JacksonMappingProvider())
                    .build();
            DocumentContext context = JsonPath.using(conf).parse(body);

            ObjectNode filterElement = createAuthFilterElement(req);

            if (filterElement != null) {
                //update 'aggs' element
                try {
                    ObjectNode elemAggs = context.read("$.aggs");

                    Iterator<Map.Entry<String, JsonNode>> it = elemAggs.fields();

                    while (it.hasNext()) {

                        Map.Entry<String, JsonNode> agg = it.next();

                        String aggName = agg.getKey();
                        ObjectNode aggObject = (ObjectNode) agg.getValue();

                        if (aggObject.get("filter") != null) {
                            context.add("$.aggs." + aggName + "filter", filterElement);
                        } else {
                            context.put("$.aggs." + aggName, "filter", filterElement);
                        }
                    }
                } catch (PathNotFoundException ex) {
                }

                //update 'query' element
                try {
                    ObjectNode elemQueryBool = context.read("$.query.bool");

                    if (elemQueryBool.get("filter") != null) {
                        context.add("$.query.bool.filter", filterElement);
                    } else {
                        context.put("$.query.bool", "filter", filterElement);
                    }

                } catch (PathNotFoundException ex) {
                }

            } else {
                LOG.warn("Request was not altered, no filter element was added");
            }

            return context.jsonString();

        } catch (IOException ex) {
            LOG.error("Exception while adding filter element to request", ex);
        }

        return null;
    }

}
