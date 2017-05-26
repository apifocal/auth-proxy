# Introduction

Auth proxy is a proxy server which forwards incoming requests to a designated url.
The body of an incoming request might be modified before being forwarded.
This allows adding authentication information in the request (e.g. to make it suitable for safe ElasticSearch usage).

# Compile
```
mvn clean install
```

# Start proxy
```
cd apache-karaf
bin/karaf
feature:repo-add mvn:org.apifocal.authproxy/auth-proxy-features/0.1.0-SNAPSHOT/xml/features
feature:install auth-proxy-jaas
scr:list
#configure the proxy url
auth-proxy:configure http://myurl.com
#show details about installed services
scr:info org.apifocal.authproxy.core.AuthProxyServlet
http:list

```

Proxy should now run on http://localhost:8181/authproxy

# Send request
```
curl -H "Content-Type: application/json" -X POST -d '{"username":"xyz","password":"xyz"}' http://localhost:8181/authproxy
```

Proxy should now forward our (modified) request to http://myurl.com
