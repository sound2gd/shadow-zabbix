(ns shadow-zabbix.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [shadow-zabbix.layout :refer [error-page]]
            [shadow-zabbix.routes.home :refer [home-routes]]
            [shadow-zabbix.routes.services :refer [service-routes]]
            [shadow-zabbix.ws :as ws]
            [compojure.route :as route]
            [shadow-zabbix.env :refer [defaults]]
            [mount.core :as mount]
            [shadow-zabbix.middleware :as middleware]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (-> #'home-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    #'ws/websocket-routes
    #'service-routes
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))


(defn app [] (middleware/wrap-base #'app-routes))
