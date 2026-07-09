(ns motor.solver-test
  (:require [clojure.test :refer [deftest is testing]]
            [motor.solver :as motor]
            [cae.solver :as cae]))

(deftest realistic-traction-motor
  (testing "a ~200 mm rotor lands in plausible power/efficiency/torque-density"
    (let [r (motor/solve {})]
      (is (< 60 (:p-peak-kW r) 220) (str "P=" (:p-peak-kW r) " kW"))
      (is (< 0.92 (:eff-peak r) 0.98))
      (is (< 1.0 (:Nm-per-kg r) 12.0)))))

(deftest stronger-magnet-more-torque
  (testing "higher remanence raises torque (shear ∝ Br)"
    (is (< (:torque-Nm (motor/solve {:magnet :ferrite}))
           (:torque-Nm (motor/solve {:magnet :NdFeB-N48}))))))

(deftest bigger-rotor-more-torque
  (testing "torque ∝ D²·L"
    (is (< (:torque-Nm (motor/solve {:d-rotor-mm 180}))
           (:torque-Nm (motor/solve {:d-rotor-mm 240}))))))

(deftest inverse-sizing-hits-target-power
  (testing "size-for-power solves geometry that delivers the target kW"
    (let [r (motor/size-for-power {:p-peak-kW 110})]
      (is (< 100 (:p-peak-kW r) 122) (str "delivered " (:p-peak-kW r) " kW for 110 target"))
      (is (< 1.0 (:Nm-per-kg r) 12.0))
      (is (< 0.92 (:eff-peak r) 0.98)))
    (testing "more power → more motor mass"
      (is (< (:mass-kg (motor/size-for-power {:p-peak-kW 80}))
             (:mass-kg (motor/size-for-power {:p-peak-kW 200})))))))

(deftest registered-on-contract
  (is (cae/registered? :rom-motor))
  (is (= :rom-motor (:solver (cae/solve {:solver {:kind :rom-motor}}))))
  (testing "contract routes to inverse sizing when a power target is present"
    (is (= 110 (:sized-for-kW (cae/solve {:p-peak-kW 110 :solver {:kind :rom-motor}}))))))

(deftest datafied
  (is (pos? (:datom-count (motor/run {:case/id "sedan/motor"})))))

(deftest effpct-datom-is-a-real-percent
  ;; effPct must be eff-peak expressed as a 0-100 percent, not the fraction
  ;; scaled by 1000 -- a traction motor can't be 960% efficient.
  (let [r (motor/run {:case/id "pct-check"})
        eff-pct (some (fn [[_ attr v]] (when (= attr :motor.MotorRun/effPct) v)) (:datoms r))]
    (is (= (Math/round (* 100.0 (:eff-peak r))) eff-pct))
    (is (< 0 eff-pct 100) (str "effPct=" eff-pct " must be a sane percent, not eff*1000"))))
