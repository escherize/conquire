(ns conquire.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [re-frame.core :as rf :refer [subscribe
                                          dispatch
                                          dispatch-sync]]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [alandipert.storage-atom :refer [local-storage]]
            [taoensso.timbre :as t]
            [clojure.string :as str]
            conquire.subs
            conquire.handlers
            [re-com.core :as rc])
  (:import goog.History))

(declare chsk-send!)

(defn about-page []
  [:p {:style {:font-size "20px"}}
   "Conquire allows you to aquire concurrent concurances "
   "organize audience questions by popularity. You simply "
   "create a room and share the link. Audience members will "
   "be able to submit and vote for questions, and you can see "
   "every question as it happens."])

(defn home-page []
  (let [room-name (subscribe [:get-path [:room-name] ""])
        db (subscribe [:db])]
    (fn []
      [rc/h-box
       :size "auto"
       :children
       [[rc/v-box
         :size "auto"
         :gap "10px"
         :children
         [[:div.jumbotron
           [:h1 "Conquire"]
           [:h4 "Invite your audience to ask you questions in real-time"]]
          [rc/h-box
           :gap "10px"
           :justify :center
           :children [[rc/input-text
                       :model room-name
                       :change-on-blur? true
                       :on-change (fn [x]
                                    (dispatch [:set-path [:room-name] x]))]
                      [rc/button
                       :label "Let's go"
                       :on-click (fn []
                                   (chsk-send! [:conquire/create-room @room-name])
                                   (dispatch [:create-room @room-name]))]]]
          [rc/line :class "debug"]
          [:pre.debug (pr-str @db)]]]]])))

(dispatch [:set-path [:current-page] home-page])
;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn init-sente! []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                    {:type :auto})]
    (def chsk       chsk)
    (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
    (def chsk-send! send-fn) ; ChannelSocket's send API fn
    (def chsk-state state)   ; Watchable, read-only atom
    ))

(defmulti event-msg-handler :id) ; Dispatch on event-id

;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (t/debugf "Event: %s" event)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (t/debugf "Unhandled event: %s" event))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (t/debugf "Channel socket successfully established!")
    (t/debugf "Channel socket state change: %s" ?data)))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (if-let [[action data] ?data]
    (cond
      (= action :conquire/change-room)
      (t/debugf "Change room: %s" data)
      :else
      (t/debugf "unhandled action/data: %s" [action data]))
    (t/debugf "Push event from server: %s" ?data)))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (t/debugf "Handshake: %s" ?data)))

(defn page []
  (let [current-page (subscribe [:get-path [:current-page]
                                 (fn []
                                   [:h2 "No page loaded."])])]
    (fn [] [:div.container
            [@current-page]])))

(defn mount-components []
  (reagent/render [#'page] (.getElementById js/document "app")))

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defn init! []
  (dispatch-sync [:initialize-db home-page])
  (init-sente!)
  (start-router!)
  (hook-browser-navigation!)
  (mount-components))
