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
           [:h3 "Invite your audience to ask"
            " questions in real-time"]]
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
                                   (chsk-send! [:conquire/create-room @room-name]))]]]
          #_[rc/line :class "debug"]
          #_[:pre.debug (pr-str @db)]]]]])))

(set! re-com.box/debug false)

(defn render-question [room-id {:keys [upvotes text id]}]
  ^{:key id}
  [rc/h-box
   :gap "20px"
   :children [[rc/v-box
               :justify :center
               :children [[rc/md-circle-icon-button
                           :md-icon-name "zmdi-plus-1"

                           :on-click     #(chsk-send! [:conquire/upvote {:room-id room-id :question-id id}])]]]
              [rc/v-box
               :justify :center
               :children [[rc/title :level :level2 :label (count upvotes)]]]
              [rc/v-box
               :justify :center
               :children [[rc/v-box
                           :justify :center
                           :children [[rc/label :label text]]]]]]])

(defn room-page []
  (let [db (subscribe [:db])
        title (subscribe [:get-path [:current-room :title]])
        room-id (subscribe [:get-path [:room :id]])
        questions (subscribe [:questions])]
    (reagent/create-class
     {:component-did-mount #(do (chsk-send! [:conquire/room-info @room-id]))
      :render (let [question-text (atom "")]
                (fn []
                  [rc/v-box
                   :children
                   [[:h2 @title]
                    [rc/v-box
                     :gap "10px"
                     :children (map (partial render-question @room-id) @questions)]
                    [rc/h-box :children
                     [[rc/input-text
                       :model question-text
                       :on-change #(reset! question-text %)]
                      [rc/button
                       :label "Ask your question"
                       :class "btn-primary"
                       :on-click (fn []
                                   (chsk-send! [:conquire/ask-question
                                                {:room-id @room-id
                                                 :question-text @question-text}])
                                   (reset! question-text ""))]]]
                    #_[rc/line :class "debug"]
                    #_[:pre.debug (pr-str @db)]]]))})))

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (dispatch [:set-path [:current-page] home-page]))
(secretary/defroute "/r/:room-id" [room-id]
  (do
    (t/debugf "room-id: %s" room-id)
    (dispatch-sync [:set-path [:room :id] room-id])
    (dispatch [:set-path [:current-page] room-page])))

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
      (dispatch [:change-room data])

      (= action :conquire/room-state)
      (do
        (t/debugf "recieved room state!")
        (dispatch [:populate-room data]))

      :else
      (t/debugf "Unhandled [action data]: %s" [action data]))
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
  (dispatch-sync [:set-path [:ws-state] chsk-state])
  (hook-browser-navigation!)
  (mount-components))
