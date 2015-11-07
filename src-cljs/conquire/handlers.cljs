(ns conquire.handlers
  (:require [re-frame.core :as re-frame]
            [ajax.core :refer [GET]]))

(defn handler [response]
  (.log js/console (str response)))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(re-frame/register-handler
 :initialize-db
 (fn  [_ [_ page-fn]]
   (js/console.log page-fn)
   {:room-name ""
    :current-page page-fn}))

(re-frame/register-handler
 :set-path
 (fn [db [_ key-path value]]
   (assoc-in db key-path value)))

(re-frame/register-handler
 :create-room
 (fn [db [_ room-name]]

   db))
