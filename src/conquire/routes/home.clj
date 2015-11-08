(ns conquire.routes.home
  (:require [conquire.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit
             :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [conquire.chat-room :as cr :refer [gen-user-id]]
            [alandipert.enduro :as e]))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  ;; ChannelSocket's receive channel
  (def ch-chsk                       ch-recv)
  ;; ChannelSocket's send API fn
  (def chsk-send!                    send-fn)
  ;; Watchable, read-only atom
  (def connected-uids                connected-uids))

(defmulti event-msg-handler :id) ; Dispatch on event-id

(defn event-msg-handler* [{:as ev-msg :keys [uid id ?data event]}]
  (def *ev-msg ev-msg)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :conquire/create-room
  [{:as ev-msg :keys [event uid]}]
  (let [[_ room-name] event
        room-id (cr/gen-room-id)
        _ (e/swap! cr/chat-room-audiences #(assoc % room-id #{}))
        _ (e/swap! cr/chat-rooms
                   #(cr/new-room % uid room-name room-id))
        response {:title room-name, :id room-id}]
    (chsk-send! uid [:conquire/change-room response])
    (pr-str response)))

(defmethod event-msg-handler :conquire/room-info
  [{:as ev-msg :keys [event uid]}]
  (let [[_ room-id] event
        _ (e/swap! cr/chat-room-audiences
                   #(update-in % [room-id] (fn [users] (conj users uid))))
        room-info (cr/room-info @cr/chat-rooms room-id)]
    (tracef "sending room state: " uid [:conquire/room-state room-info])
    (chsk-send! uid [:conquire/room-state room-info])))

(defmethod event-msg-handler :conquire/ask-question
  [{:as ev-msg :keys [event uid]}]
  (let [[_ data] event
        {:keys [room-id question-text]} data
        _ (tracef [uid room-id question-text])
        _ (e/swap! cr/chat-rooms
                   #(cr/ask-question % uid room-id question-text))
        new-room-info (cr/room-info @cr/chat-rooms room-id)]
    (doseq [uid (cr/users-in-room room-id)]
      (chsk-send! uid [:conquire/room-state new-room-info]))))

(defmethod event-msg-handler :conquire/upvote
  [{:as ev-msg :keys [event uid]}]
  (let [[_ {:keys [room-id question-id]}] event
        room-info (cr/room-info @cr/chat-rooms room-id)
        _ (e/swap! cr/chat-rooms
                   #(cr/upvote % uid room-id question-id))
        new-room-info (cr/room-info @cr/chat-rooms room-id)]
    (doseq [uid (cr/users-in-room room-id)]
      (chsk-send! uid [:conquire/room-state new-room-info]))))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defn home-page [req]
  (let [uid (or (get-in req [:session :uid]) (gen-user-id))]
    (assoc (layout/render "home.html") :session {:uid uid})))

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(start-router!)

(defroutes home-routes
  (GET  "/"          req (home-page req))
  ;;(GET  "/room-info" req (create-room req))
  ;; Sente \/
  (GET  "/chsk"     req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk"     req (ring-ajax-post                req)))
