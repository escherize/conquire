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

(re-frame/register-sub
 :questions
 (fn [db & _]
   (reaction
    (->> @db
         :current-room
         :questions
         vals
         (sort-by (comp count :upvotes))
         reverse))))

(re-frame/register-sub
 :current-owner
 (fn [db & _] "Am I the room owner?"
   (reaction
    (= (-> @db :ws-state deref :uid)
       (-> @db :current-room :owner)))))
