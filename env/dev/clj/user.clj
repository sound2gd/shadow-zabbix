(ns user
  (:require [mount.core :as mount]
            shadow-zabbix.core))

(defn start []
  (mount/start-without #'shadow-zabbix.core/repl-server))

(defn stop []
  (mount/stop-except #'shadow-zabbix.core/repl-server))

(defn restart []
  (stop)
  (start))


