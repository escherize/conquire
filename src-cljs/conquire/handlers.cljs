(ns conquire.handlers
  (:require [re-frame.core :as re-frame :refer [debug]]
            [ajax.core :refer [GET]]))

(defn handler [response]
  (.log js/console (str response)))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(re-frame/register-handler
 :initialize-db
 debug
 (fn  [_ [_ page-fn ws-state]]
   {:room-name ""
    :questions []
    :ws-state ws-state
    :current-page page-fn}))

(re-frame/register-handler
 :set-path
 (fn [db [_ key-path value]]
   (assoc-in db key-path value)))

(re-frame/register-handler
 :create-room
 debug
 (fn [db [_ room-name]]
   (js/console.log ":create-room called with: " room-name)
   db))

(defn show-page [url]
  ;;(update-cookie)
  (set! (.-location js/window) url)
  (.scrollTo js/window 0 0))

(re-frame/register-handler
 :change-room
 debug
 (fn [db [_ {:keys [id title]}]]
   (js/console.log ":create-room called with: title: " title ", room-id: " id)
   (show-page (str "#/r/" id))
   (-> db
       (assoc-in [:room :id] id)
       (assoc-in [:room :title] title))))

(re-frame/register-handler
 :populate-room
 debug
 (fn [db [_ room-data]]
   (assoc db :current-room room-data)))
