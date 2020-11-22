(ns addly.app
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [cljs-bean.core :refer [bean]]
            ["expo" :as ex]
            ["expo-ads-admob" :as admob]
            ["expo-constants" :as constants]
            [goog.object :as o]
            ["react" :as react]
            ["react-native" :as rn]
            [shadow.expo :as expo]))

(def ^:private e react/createElement)

(def ^:private test-ad-unit-id "ca-app-pub-3940256099942544/5224354917")

(defn- view
  [props]
  (let [{:keys [children]} (bean props)]
    (e rn/View #js {:style #js {:flexDirection "column"
                                :justifyContent "center"
                                :alignItems "center"
                                :height "100%"
                                :padding 20
                                :paddingTop constants/statusBarHeight}}
       children)))

(defn- run-add
  [set-state]
  (go
    (set-state #(assoc %
                       :ad-start-time nil
                       :ad-result nil))
    (<p! (admob/AdMobRewarded.requestAdAsync))
    (<p! (admob/AdMobRewarded.showAdAsync))))

(defn- ad-page
  [props]
  (let [{:keys [set-state]} (bean props)]
    (react/useEffect (fn [] (run-add set-state) js/undefined) #js [])
    (e view nil
       (e rn/Text nil "The ad is loading..."))))

(defn- ad-completed
  [props]
  (let [{:keys [state set-state]} (bean props)
        {:keys [ad-points score]} state]
    (e view nil
       (e rn/Text nil "Yay! You got " ad-points " points!")
       (e rn/Text nil "Score: " score)
       (e rn/Button #js {:onPress #(run-add set-state) :title "Gimmie an ad!"}))))

(defn- ad-closed
  [props]
  (let [{:keys [state set-state]} (bean props)
        {:keys [ad-points score]} state]
    (e view nil
       (e rn/Text nil "Boo! You could have earned " ad-points " points!")
       (e rn/Text nil "Score: " score)
       (e rn/Button #js {:onPress #(run-add set-state) :title "Gimmie an ad!"}))))

(defn- root []
  (let [[state set-state] (react/useState {:score 0
                                           :ad-start-time nil
                                           :ad-result nil
                                           :ad-points 0})
        ad-result (:ad-result state)]
    (react/useEffect
     (fn []
       (admob/AdMobRewarded.setAdUnitID test-ad-unit-id)
       (admob/AdMobRewarded.addEventListener
        "rewardedVideoDidStart"
        (fn [_]
          (set-state
           #(assoc %
                   :ad-start-time (js/Date.now)
                   :ad-points 0))))
       (admob/AdMobRewarded.addEventListener
        "rewardedVideoDidRewardUser"
        (fn [_] 
          (set-state
           #(let [points (- (js/Date.now) (:ad-start-time %))]
              (assoc %
                     :ad-start-time nil
                     :score (+ (:score %) points)
                     :ad-points points
                     :ad-result :completed)))))
       (admob/AdMobRewarded.addEventListener
        "rewardedVideoDidClose"
        (fn [_]
          (set-state
           #(if (not= :completed (:ad-result %))
              (let [points (- (js/Date.now) (:ad-start-time %))]
                (assoc %
                       :ad-points points
                       :ad-result :closed))
              %))))
       js/undefined)
     #js [])
    (cond
      (= :completed ad-result)
      (e ad-completed #js {:state state :set-state set-state} nil)

      (= :closed ad-result)
      (e ad-closed #js {:state state :set-state set-state} nil)

      (nil? ad-result)
      (e ad-page #js {:set-state set-state} nil))))

(defn start
  {:dev/after-load true}
  []
  (expo/render-root (e root nil nil)))

(defn init []
  (start))
