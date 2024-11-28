(defproject jepsen.stolon "0.1.0"
  :description "Jepsen tests for Stolon, a PostgreSQL HA system."
  :url "https://github.com/jepsen-io/jepsen"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [jepsen "0.2.0"]
                 [jepsen.etcd "0.2.1"]
                 [elle "0.1.2"]
                 [seancorfield/next.jdbc "1.0.445"]
                 [org.postgresql/postgresql "42.2.12"]
                 [cheshire "5.10.0"]
                 [clj-wallhack "1.0.1"]]
  :mirrors {#"clojars"
            {:name "Clojar Mirror"
             :url "https://mirrors.tuna.tsinghua.edu.cn/clojars"
             :repo-manager true}
            #"central"
            {:name "Maven aliyun"
             :url "https://maven.aliyun.com/repository/public"
             :repo-manager true}}
  :jvm-opts ["-Djava.awt.headless=true"]
  :main jepsen.stolon
  :repl-options {:init-ns jepsen.stolon})
