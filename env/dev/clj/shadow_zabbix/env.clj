(ns shadow-zabbix.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [shadow-zabbix.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[shadow-zabbix started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[shadow-zabbix has shut down successfully]=-"))
   :middleware wrap-dev})
