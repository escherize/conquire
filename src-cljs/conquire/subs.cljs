(ns conquire.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :get-path
 (fn [db [_ path & [not-found]]]
   (reaction (get-in @db path not-found))))

(re-frame/register-sub
 :db
 (fn [db & _]
   (reaction (dissoc @db :current-page))))
