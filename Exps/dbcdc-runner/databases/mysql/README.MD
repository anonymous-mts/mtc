# Debugging with MySQL

Use MySQL started with local Docker as the workload generator and debugging target.

## MySQL Installation
The tutorial for installing MySQL can be referred to here:：[Docker Install MySQL](https://www.runoob.com/docker/docker-install-mysql.html?_=1665737981407)

## Configuration File：
```clojure
{:mysql      {:dbtype    "mysql"
              :host      "127.0.0.1"
              :port      3306
              :dbname    "test" 
              :user      "root"
              :password  "123456"}}
```
