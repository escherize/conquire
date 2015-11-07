(ns conquire.routes.home
  (:require [conquire.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit
             :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [conquire.chat-room :refer [gen-user-id]]))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(defmulti event-msg-handler :id) ; Dispatch on event-id

(defn event-msg-handler* [{:as ev-msg :keys [uid id ?data event]}]
  (def *ev-msg ev-msg)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :conquire/create-room
  [{:as ev-msg :keys [event uid]}]
  (let [[_ room-name] event]
    (println "Create room (" room-name ") called by: " uid)))

(defn create-room [{:keys [session params] :as req}]
  (let [uid (:uid session)
        room-name (:room-name params)]
    (pr-str {:uid uid
             :title room-name})))

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
  (GET "/"          req (home-page req))
  (GET "/room-info" req (create-room req))
  (GET  "/chsk"     req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk"     req (ring-ajax-post                req)))
