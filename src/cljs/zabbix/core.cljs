(ns zabbix.core
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET POST]]
            [zabbix.ws :as ws]))

(def jquery (js* "$"))

(def ansi_up (js/AnsiUp.))

(defn deploy-button [server project action branch button-name]
  [:button.btn.btn-secondary
   {:type "button"
    :on-click #(ws/send-message!
                [:shadow-zabbix/runpre
                 {:server server
                  :project project
                  :action action
                  :branch branch}])}
   (when button-name button-name)])

(defn pre-button-layout
  [& btns]
  [:div.row
   (for [row (partition-all 4 btns)]
     ^{:key row}
     [:div.row
      (for [btn row]
        ^{:key btn}
        [:div.col-md-3
         btn])])])

(defn pre-buttons []
  [:div
   [:div.card
    [:h3.card-header
     "中国区"]
    [:div.card-block
     [pre-button-layout
      [:button.btn.btn-secondary
       {:type "button"
        :on-click #(ws/send-message! [:shadow-zabbix/runpre
                                      {:server :t_pre_basic_atop_backend
                                       :project :basic
                                       :action :runpre
                                       :branch :dev}])}
       "发布预发 basic"]
      [deploy-button :t_pre_basic_atop_backend :atop :runpre :dev "发布预发atop"]
      [deploy-button :s_pre_smart_mercury_jupiter :smart :runpre :dev "发布预发smart"]
      [deploy-button :s_pre_smart_mercury_jupiter :mercury :runpre :dev "发布预发mercury"]
      [deploy-button :s_pre_smart_mercury_jupiter :jupiter :runpre :dev "发布预发jupiter"]
      [deploy-button :s_pre_task_proxy_backend :smart-task :runpre :dev "发布预发smart-task"]
      [deploy-button :s_pre_task_proxy_backend :backend :runpre :dev "发布预发backend"]
      [deploy-button :s_pre_task_proxy_backend :cloud-proxy :runpre :dev "发布预发cloud-proxy"]
      [deploy-button :s_pre_business :hermes :runpre :term1 "发布预发hermes term1分支"]
      [deploy-button :s_pre_business :commercial :runpre :first "发布预发commercial first分支"]
      ]]]
   [:div.card
    [:h3.card-header
     "美国区"]
    [:div.card-block
     [pre-button-layout
      [deploy-button :w2_t_pre_basic_atop_backend :basic :runpre :dev "发布美国预发basic"]
      [deploy-button :w2_t_pre_basic_atop_backend :atop :runpre :dev "发布美国预发atop"]
      [deploy-button :w2_s_pre_smart_mercury_jupiter :smart :runpre :dev "发布美国预发smart"]
      [deploy-button :w2_s_pre_smart_mercury_jupiter :mercury :runpre :dev "发布美国预发mercury"]
      [deploy-button :w2_s_pre_smart_mercury_jupiter :jupiter :runpre :dev "发布美国预发jupiter"]
      [deploy-button :w2_s_pre_task_proxy :smart-task :runpre :dev "发布美国预发smart-task"]
      [deploy-button :w2_s_pre_task_proxy :cloud-proxy :runpre :dev "发布美国预发cloud-proxy"]
      ]]]
   ])

(def error-msgs (atom []))

(defn terminal-span [type message time]
  [:span {:data-terminal type
          :data-terminal-message message
          :data-terminal-time time}])

(defn terminal-text [messsage time]
  (terminal-span "text" messsage time))

(defn terminal-line [message time]
  (terminal-span "line" message time))

(defn terminal [messages]
  [:div#terminal-window
   [:div#terminal
    {:data-terminal-prompt "cris @ crisdeMacBook-Pro $"}
    [terminal-text "deploy message!" 0]
    (.log js/console messages)
    [:div.output
     [:span.line "cris @ crisdeMacBook-Pro$"]
     (for [msg (reverse messages)]
       [:span.line
        ^{:key msg}
        {:dangerouslySetInnerHTML
         (js-obj "__html" (.ansi_to_html ansi_up msg))
         }
        ])
     (for [msg @error-msgs]
       [:span.line
        ^{:key msg}
        msg])]
    ]])

(defn set-terminal-scroll! []
  (let [div (.querySelector js/document "div.output")]
    (set! (.-scrollTop div) (+ 50 (.-scrollHeight div)))))

(defn handle-info
  "如果消息时间类型是toast，就toast"
  [event-type msg]
  (when (= :shadow-zabbix/toast-info event-type)
    (.toast js/jQuery (js-obj "heading" "成功！",
                              "text" msg,
                              "showHideTransition" "slide"
                              "textAlign" "center"
                              "icon" "success"
                              "hideAfter" false
                              "position" "mid-center"))))


(defn handle-alert
  "如果消息时间类型是toast，就toast"
  [event-type msg]
  (when (= :shadow-zabbix/toast event-type)
    (.toast js/jQuery (js-obj "heading" "Error",
                              "text" msg,
                              "showHideTransition" "slide",
                              "icon" "error"
                              "position" "bottom-right"))))

(defn handle-error
  "如果消息时间类型是error,显示error"
  [event-type msg]
  (when (= :shadow-zabbix/error event-type)
    (swap! error-msgs conj msg)))

(defn handle-echo-line [msgs msg]
  (let [last-msg (peek @msgs)
        reg #"\d+"
        r1 (clojure.string/replace msg reg "")]
    (if (and last-msg
         (= r1 (clojure.string/replace last-msg reg "")))
      (swap! msgs #(conj (pop %) msg))
      (swap! msgs conj msg))))

(defn dispatch-event
  "分发监听到的消息事件到具体的处理器上去"
  [event-type msg]
  (set-terminal-scroll!)
  (handle-alert event-type msg)
  (handle-info event-type msg)
  (handle-error event-type msg))

(defn home []
  (let [messages (atom nil)
        div (.querySelector js/document "div.output")]
    (ws/start-router! (fn [{[event-type message] :?data}]
                        (when message
                          (when (= :shadow-zabbix/msg event-type)
                              (handle-echo-line messages message))
                          (dispatch-event event-type message))))
    (.ready (jquery (js* "document"))
            #(do (js/runTerminal (jquery "#terminal"))))
    (fn []
      [:div
       [:div.row
        [:div.span12
         [pre-buttons]
         [:div.col-md-12
          [:button.btn.btn-primary.span4
           {:on-click #(reset! messages nil)}
           "清空console"
           ]
          ]]]
       [:div.row
        [:div.span12
         [terminal @messages]
         ]]])))

(reagent/render
 [home]
 (.getElementById js/document "content"))
