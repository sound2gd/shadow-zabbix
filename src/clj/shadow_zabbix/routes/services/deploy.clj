(ns shadow-zabbix.routes.services.deploy
  (:require
    [shadow-zabbix.config :refer [env]]
    [clojure.tools.logging :as log]
    [me.raynes.conch.low-level :as sh]
    ))

(def ^:dynamic *sh-dir-deploy*
  (if-let [dir (:sh-dir env)]
    dir "/Users/cris/Workspace/tuyaBasic/"))

(defn local-env? [sh-dir]
  (if (.contains sh-dir "cris")
    true false))

(defn deploy-project [out project-name branch]
  (log/info "执行部署命令 project = " project-name
            ",branch = " branch)
  (sh/stream-to (sh/proc "sudo" "-u" "docker" "sh" "server.sh"
                         project-name "runpre" branch
                         ;; 指定工作目录
                         :dir *sh-dir-deploy*)
                :out out))


