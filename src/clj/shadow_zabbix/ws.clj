(ns shadow-zabbix.ws
  (:require [compojure.core :refer [GET defroutes POST]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [shadow-zabbix.jsch :as jsch]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant
             :refer [sente-web-server-adapter]]))

(let [conn (sente/make-channel-socket!
            sente-web-server-adapter
            {:user-id-fn
             (fn [ring-req]
               (get-in ring-req [:params :client-id]))})]
  (def ring-ajax-post (:ajax-post-fn conn))
  (def ring-ajax-get-or-ws-hanleshake (:ajax-get-or-ws-handshake-fn conn))
  (def ch-chsk (:ch-recv conn))
  (def chsk-send! (:send-fn conn))
  (def connected-uids (:connected-uids conn)))

(def project-map {:basic :t_pre_basic_atop_backend
                  :atop :t_pre_basic_atop_backend
                  :smart :s_pre_smart_mercury_jupiter
                  :mercury :s_pre_smart_mercury_jupiter
                  :jupiter :s_pre_smart_mercury_jupiter
                  :backend :s_pre_task_proxy_backend
                  :smart-task :s_pre_task_proxy_backend
                  :cloud-proxy :s_pre_task_proxy_backend})

(def deploying-project (atom nil))

(defn send-deploy-success! []
  (let [{:keys [server project action branch]}
        @deploying-project
        msg (str "服务器" server "工程 " project " 操作" action
                 " 分支 " branch "部署成功!")]
    (doseq [uid (:any @connected-uids)]
      ;; 给所有连接上的websocket发送信息
      (chsk-send! uid [:shadow-zabbix/toast-info msg]))))

(defn send-deploy-failure! []
  (let [{:keys [server project action branch]}
        @deploying-project
        msg (str "服务器" server "工程 " project " 操作" action
                 " 分支 " branch "部署成功!")]
    (doseq [uid (:any @connected-uids)]
      ;; 给所有连接上的websocket发送信息
      (chsk-send! uid [:shadow-zabbix/toast (str msg "老柯的脚本返回失败，那应该是失败了！")]))))

(defn handle-error-output [e]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid [:shadow-zabbix/error (str "发生了错误" e)] true)))

(defn handle-output
  [{:keys [server project action branch] :as ?data}]
  (clojure.core.async/thread
    (log/info (.getName (Thread/currentThread)) " 线程处理发布")
    (reset! deploying-project ?data)
    (try
      (let [[channel session]
            (jsch/exec-deploy
             (jsch/connect-proxy! server)
             (name project) (name action) (name branch))
            in (.getInputStream channel)
            out (.getOutputStream channel)]
        (try
          (binding [*in* (clojure.java.io/reader in)]
            (while (not (.isClosed channel))
              (let [msg (read-line)]
                (doseq [uid (:any @connected-uids)]
                  ;; 给所有连接上的websocket发送信息
                  (chsk-send! uid [:shadow-zabbix/msg msg] true)))))

          (if (zero? (.getExitStatus channel))
            (send-deploy-success!)
            (send-deploy-failure!))

          (catch Exception e
            (throw e))
          (finally
            (println "\n服务器返回值" (.getExitStatus channel)
                     "\n开始断开连接..")
            (.disconnect channel)
            (.disconnect session)
            (println "断开连接成功")
            (reset! deploying-project nil)
            )))
      (catch Exception e
        (log/error e)
        (handle-error-output e)
        (reset! deploying-project nil)))))


;; (->> (keys (Thread/getAllStackTraces))
;;      (map #(.getName %))
;;      (filter #(.contains % "async-thread")))
;; (jsch/connect-proxy! :t_pre_basic_atop_backend)

(defn handle-runpre [id client-id ?data]
  (when (and (= id :shadow-zabbix/runpre)
             ?data)
    (try
      (if @deploying-project
        (let [{:keys [server project action branch]} @deploying-project]
          (chsk-send!
           client-id
           [:shadow-zabbix/toast
            (str "服务器 " server " 项目" project " 正在部署,操作 "
                 action " 分支 " branch)]))

        (when ?data
          (handle-output ?data)))

      (catch Exception e
        (handle-error-output e)))))

(defn handle-message [{:keys [id client-id ?data]}]
  (log/info "\n\n Got message: " id (keys ?data))
  (handle-runpre id client-id ?data))


(defn stop-router! [stop-fn]
  (when stop-fn (stop-fn)))

(defn start-router! []
  (log/info "++++++++++++++ starting router +++++++++++++++")
  (sente/start-chsk-router! ch-chsk handle-message))

(defstate router
  :start (start-router!)
  :stop (stop-router! router))

(defroutes websocket-routes
  (GET "/ws" req (ring-ajax-get-or-ws-hanleshake req))
  (POST "/ws" req (ring-ajax-post req)))
