(ns shadow-zabbix.jsch
  (:require [mount.core :refer [defstate]]
            [clojure.core.async :refer [go chan <! >! >!! <!! go-loop] :as async]
            [clojure.tools.logging :as log])
  (:import (com.jcraft.jsch
            JSch ConfigRepository OpenSSHConfig
            Channel Session)))

;; 这个ns的主要功能是
;; 链接远程的预发机器，并且执行操作
(JSch/setConfig "StrictHostKeyChecking", "no")
(JSch/setConfig "PreferredAuthentications", "publickey")

(def ssh-config-local
  (OpenSSHConfig/parseFile "/Users/cris/.ssh/config"))

(def ssh-private-key-local-path
  "/Users/cris/.ssh/id_rsa")

(defn get-ssh-config
  "从ssh-config获取key配置信息，信息里包含了user,host,port"
  [key]
  (let [conf (if (keyword? key)
               (.getConfig ssh-config-local (name key))
               (.getConfig ssh-config-local key))]
    (when (.getHostname conf)
      {:host (.getHostname conf)
       :port (.getPort conf)
       :user (.getUser conf)})))

(defmacro with-connection
  [binding & body]
  `(let [session# ~(second binding)
         ~(first binding) session#]
     (try
       (when-not (.isConnected session#)
         (.connect session#))
       ~@body
       (finally
         (.disconnect session#)))))

(defn get-jsch []
  (doto (JSch.)
    (.addIdentity ssh-private-key-local-path)
    (.setConfigRepository ssh-config-local)))

(def shared-jsch (get-jsch))

(def shared-gateway-session (atom {}))

;; (def messages (atom nil))
;; (let [msg "\r app4"
;;       last-msg (peek @messages)]
;;   (if (.contains last-msg "\r")
;;     (swap! messages #(conj (pop %) msg))
;;     (swap! messages conj msg)))

(defn dispatch-gateway
  "分配连接机器的gateway"
  [server-name]
  (let [server-name# (if (keyword? server-name)
                       (name server-name) server-name)]
    (cond
      (= server-name# "aws.gateway") :aws.gateway
      (= server-name# "ali-gateway") :ali.gateway
      (.contains server-name# "eu") :aws.gateway
      (.contains server-name# "w2") :aws.gateway
      :else :ali.gateway
      )))

(defn keyword->str [key]
  (if (keyword? key)
    (name key) key))

(defn yes-and-flush
  "从outputstream 里输出y"
  [out]
  (.write out (.getBytes "y\n"))
  (.flush out))

(defn new-connection!
  [server-name]
  (let [jsch shared-jsch
        {:keys [host port user]} (get-ssh-config server-name)
        session (.getSession jsch  user host port)
        ]
    (reset! shared-gateway-session
            (assoc @shared-gateway-session
                   (keyword server-name) session))
    session))

(defn connect!
  "根据服务器名称链接到服务器，支持使用keyword"
  [server-name]
  (let [session (new-connection! server-name)]
    (when-not (.isConnected session)
      (.connect session))
    session)
  ;; (if-let [s (get @shared-gateway-session
  ;;                 (keyword server-name))]
  ;;   ;; 链接机器
  ;;   (if-not (.isConnected s)
  ;;     (try (do (.connect s) s)
  ;;          (catch Exception e
  ;;            (log/warn e)
  ;;            (reset! shared-gateway-session
  ;;                    (dissoc @shared-gateway-session
  ;;                            (keyword server-name)))
  ;;            (new-connection! (keyword server-name))))
  ;;     s)

  ;;   (new-connection! server-name))
  )

(defn server-exist? [server-name]
  (when (get-ssh-config server-name) true))

(defn connect-proxy!
  "连接到需要代理的机器，类似预发机器这样的,返回session"
  [server-name]
  (if-not (server-exist? server-name)
    (throw (Exception. "不存在该机器"))
    (let [gate-way-session
          (connect! (dispatch-gateway server-name))]

      (let [{:keys [user host port]}  (get-ssh-config server-name)
            local-port (.setPortForwardingL gate-way-session
                                            0 host port)
            session (.getSession
                     shared-jsch
                     user "127.0.0.1" local-port)
            ]
        (.connect session)
        session))))

(defn exec-deploy
  "执行老柯的部署脚本，返回代表这个通道的channel
  通过channel的inputStream可以得到远程的结果
  通过channel的outputStream可以执行shell命令
  通过`.isClosed`方法可以得到当前命令是否执行完毕
  "
  [^Session session project action branch
   & {out-stream :bind-out-stream
      in-stream :bind-input-stream}]
  (let [channel (.openChannel session "exec")
        command (str "cd /home/docker/bin\n sudo -u docker sh server.sh " project " " action  " " branch)]
    (doto channel
      (.setCommand command)
      (.setErrStream System/err)
      (.setPtyType "vt102")
      (.setPty true)
      (.connect))

    (when out-stream (.setOutputStream channel))
    (when in-stream (.setInputStream channel))
    (yes-and-flush (.getOutputStream channel))
    [channel session]))


(defn exec-deploy->console
  "发布预发"
  [server-name project action branch]
    (let [[channel session]
          (exec-deploy (connect-proxy! server-name)
                   project action branch)
          in (.getInputStream channel)
          out (.getOutputStream channel)]
        (loop [bytes (byte-array 256)
               available? (pos? (.available in))]
          (when available?
            (let [length (.read in bytes 0 256)
                  str (String. bytes 0 length)]
              (print str)))

          (when-not (.isClosed channel)
            (recur bytes (pos? (.available in)))))

        (println "\n服务器返回值" (.getExitStatus channel)
                 "\n开始断开连接..")
      (.disconnect channel)
      (.disconnect session)
      (println "断开连接成功")
      (.getExitStatus channel)))

(defn runpre-smart []
  (exec-deploy->console :s_pre_smart_mercury_jupiter
                        "smart" "runpre" "dev"))

(defn runpre-jupiter []
  (exec-deploy->console :s_pre_smart_mercury_jupiter
                        "jupiter" "runpre" "dev"))

(defn runpre-mercury []
  (exec-deploy->console :s_pre_smart_mercury_jupiter
                        "mercury" "runpre" "dev"))

(defn runpre-basic [& {branch :branch
                       :or {branch "dev"}}]
  (exec-deploy->console :t_pre_basic_atop_backend
                        "basic" "runpre" branch))

(defn runpre-atop [& {branch :branch
                      :or {branch "dev"}}]
  (exec-deploy->console :t_pre_basic_atop_backend
                        "atop" "runpre" branch))

;; (runpre-smart)
;; (go (<! (runpre-basic)))
;; (runpre-basic)

;; (when (.contains str "这个项目正在编译")
;;   (doto out
;;     (.write (.getBytes (str "sudo -u docker docker rm Compile_" project "\n")))
;;     (.flush)
;;     (.write (.getBytes (str "sudo -u docker sh server.sh" project " " action " " branch)))
;;     (.flush)))
