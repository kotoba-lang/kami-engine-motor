(ns motor.solver
  "Reduced-order traction-motor solver (:rom-motor) — air-gap shear sizing +
  a loss-fraction efficiency model. Torque from tangential shear stress over
  the rotor surface (T = ½·π·σ·D²·L); peak power from base speed; mass from
  active volume; efficiency from copper/iron/mechanical loss shares. Turns the
  design's peak-kW assumption into a sized motor (torque density + efficiency).

  A magnetostatic kami-emag FEA registers :emag-fea on the same cae-solver
  contract for higher fidelity; callers swap via [:solver :kind]."
  (:require [datom.core :as d]
            [cae.solver :as cae]))

(def ^:const pi Math/PI)

;; Magnet remanence Br (T) — sets achievable air-gap shear.
(def magnets {:NdFeB-N42 1.30 :NdFeB-N48 1.40 :ferrite 0.40 :SmCo 1.05})

(defn solve
  "case: {:d-rotor-mm :l-stack-mm :rpm-base :magnet :shear-kPa
          :cu-loss :fe-loss :mech-loss :active-frac :rho-active}"
  [{:keys [d-rotor-mm l-stack-mm rpm-base magnet shear-kPa
           cu-loss fe-loss mech-loss active-frac rho-active]
    :or {d-rotor-mm 200 l-stack-mm 150 rpm-base 4500 magnet :NdFeB-N42
         shear-kPa 35 cu-loss 0.022 fe-loss 0.012 mech-loss 0.006
         active-frac 0.62 rho-active 5200}}]
  (let [d    (/ d-rotor-mm 1000.0)
        l    (/ l-stack-mm 1000.0)
        br   (get magnets magnet 1.30)
        sig  (* shear-kPa 1000.0 (/ br 1.30))         ; tangential shear, Pa
        torque (* 0.5 pi sig d d l)                   ; N·m  (½·π·σ·D²·L)
        wb   (* rpm-base (/ (* 2 pi) 60.0))           ; base speed rad/s
        p-kw (/ (* torque wb) 1000.0)
        d-out (* 1.6 d)                                ; stator OD ≈ 1.6·rotor
        v-act (* (/ pi 4.0) d-out d-out l)
        mass (/ (* rho-active v-act) active-frac)     ; incl housing/shaft via active-frac
        eff  (- 1.0 (+ cu-loss fe-loss mech-loss))]
    {:torque-Nm torque :p-peak-kW p-kw :mass-kg mass
     :Nm-per-kg (/ torque mass) :eff-peak eff :magnet magnet
     :solver :rom-motor}))

(defmethod cae/solve :rom-motor [case] (solve case))

(defn run [case]
  (let [r   (solve case)
        cid (or (:case/id case) "motor-0")
        ent (d/entity "motor" :MotorRun cid
                      {:magnet (name (:magnet r))
                       :Nm     (Math/round (double (:torque-Nm r)))
                       :kW     (Math/round (double (:p-peak-kW r)))
                       :massKg (Math/round (double (:mass-kg r)))
                       :NmPerKg (Math/round (* 100.0 (:Nm-per-kg r)))
                       :effPct (Math/round (* 1000.0 (:eff-peak r)))})
        led (d/log [ent])]
    (assoc r :datoms (:datoms led) :datom-count (:count led))))
