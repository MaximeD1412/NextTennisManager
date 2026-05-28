package com.nexttennis.simulator.hit;

import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.springframework.stereotype.Service;

/**
 * Resolves HitQuality (0–1) and effectiveShotIntent for a single Coup.
 *
 * Inputs: ReachResult, effectiveFatigue, TennisPlayerSnapshot (all attributes
 * + active tactic), ShotType, and PointContext (score flags for mental attributes).
 * MISSED is not handled here — the point loop detects it before calling this service.
 */
@Service
public class HitQualityService {

    static final float MAX_FATIGUE_PENALTY = 0.25f;
    // HQ drift per match point played, fully resisted by Concentration = 99
    static final float CONCENTRATION_DRIFT_RATE = 0.0003f;
    // Clutch modifier scale: (clutch − 50) / 99 × scale → ±half-scale at extremes
    static final float CLUTCH_SCALE = 0.15f;
    // Combativité bonus scale when score is unfavorable
    static final float COMBATIVITE_SCALE = 0.10f;
    // Maximum HQ reduction from low Moral (fully mitigated by Confiance = 99)
    static final float MORAL_PENALTY = 0.15f;

    public HitQualityResult compute(
            ReachResult reachResult,
            float effectiveFatigue,
            TennisPlayerSnapshot player,
            ShotType shotType,
            PointContext pointContext) {

        ShotIntent tacticIntent = tacticToIntent(player.getActiveTactic());
        ShotIntent effectiveIntent = applyConstraint(reachResult, tacticIntent);

        float hq = computeBaseHitQuality(reachResult, effectiveFatigue, player, shotType, tacticIntent, effectiveIntent);
        hq = applyMentalModifiers(hq, player, pointContext);
        hq = Math.clamp(hq, 0.0f, 1.0f);

        return new HitQualityResult(hq, effectiveIntent);
    }

    // Package-private for direct testing of the intent resolution logic.
    ShotIntent tacticToIntent(PlayerTactic tactic) {
        return switch (tactic) {
            case PLAYER_TACTIC_AGGRESSIVE_BASELINE,
                 PLAYER_TACTIC_NET_RUSHER,
                 PLAYER_TACTIC_SERVE_AND_VOLLEY -> ShotIntent.SHOT_INTENT_AGGRESSIVE;
            case PLAYER_TACTIC_COUNTER_PUNCHER,
                 PLAYER_TACTIC_ALL_COURT       -> ShotIntent.SHOT_INTENT_NEUTRAL;
            case PLAYER_TACTIC_DEFENSIVE        -> ShotIntent.SHOT_INTENT_DEFENSIVE;
            default                             -> ShotIntent.SHOT_INTENT_NEUTRAL;
        };
    }

    // Package-private for direct testing of the constraint table.
    ShotIntent applyConstraint(ReachResult reachResult, ShotIntent tacticIntent) {
        return switch (reachResult) {
            case REACH_RESULT_COMFORTABLE,
                 REACH_RESULT_LATE      -> tacticIntent;
            case REACH_RESULT_STRETCHED ->
                    tacticIntent == ShotIntent.SHOT_INTENT_AGGRESSIVE
                            ? ShotIntent.SHOT_INTENT_NEUTRAL
                            : tacticIntent;
            case REACH_RESULT_DESPERATE -> ShotIntent.SHOT_INTENT_DEFENSIVE;
            default                     -> tacticIntent; // MISSED not computed here
        };
    }

    private float computeBaseHitQuality(
            ReachResult reachResult,
            float effectiveFatigue,
            TennisPlayerSnapshot player,
            ShotType shotType,
            ShotIntent tacticIntent,
            ShotIntent effectiveIntent) {

        float techFactor = selectTechniqueAttribute(player, shotType) / 99.0f;
        float reachFactor = computeReachFactor(reachResult, player, shotType, tacticIntent, effectiveIntent);

        // Blend reach quality with technique: higher technique → better baseline
        float rawHQ = reachFactor * (0.5f + 0.5f * techFactor);

        float fatiguePenalty = effectiveFatigue * MAX_FATIGUE_PENALTY;
        return rawHQ * (1.0f - fatiguePenalty);
    }

    private float computeReachFactor(
            ReachResult reachResult,
            TennisPlayerSnapshot player,
            ShotType shotType,
            ShotIntent tacticIntent,
            ShotIntent effectiveIntent) {

        return switch (reachResult) {
            case REACH_RESULT_COMFORTABLE -> 1.0f;
            case REACH_RESULT_LATE        -> 0.80f;
            case REACH_RESULT_STRETCHED   -> computeStretchedFactor(player, shotType, tacticIntent);
            case REACH_RESULT_DESPERATE   -> computeDesperateFactor(player, effectiveIntent);
            default                       -> 0.0f;
        };
    }

    private float computeStretchedFactor(
            TennisPlayerSnapshot player, ShotType shotType, ShotIntent tacticIntent) {

        if (tacticIntent == ShotIntent.SHOT_INTENT_AGGRESSIVE) {
            // Contre: tactic was AGGRESSIVE but ball reached only as STRETCHED.
            // High Contre reduces HQ degradation on these difficult attacking shots.
            float contreBonus = (player.getContre() - 1) / 98.0f * 0.25f;
            return 0.25f + contreBonus; // [0.25 – 0.50]
        }

        // Standard STRETCHED — modulated by the "en course" attribute for the shot side.
        int enCourseAttr = switch (shotType) {
            case SHOT_TYPE_FOREHAND -> player.getCoupDroitEnCourse();
            case SHOT_TYPE_BACKHAND -> player.getReversEnCourse();
            default                 -> 50;
        };
        float enCourseBonus = (enCourseAttr - 1) / 98.0f * 0.15f;
        return 0.20f + enCourseBonus; // [0.20 – 0.35]
    }

    private float computeDesperateFactor(TennisPlayerSnapshot player, ShotIntent effectiveIntent) {
        if (effectiveIntent == ShotIntent.SHOT_INTENT_DEFENSIVE) {
            // Remise difficile: reduces HQ degradation when playing defensively from DESPERATE.
            float remiseBonus = (player.getRemiseDifficile() - 1) / 98.0f * 0.15f;
            return 0.05f + remiseBonus; // [0.05 – 0.20]
        }
        return 0.05f;
    }

    private float selectTechniqueAttribute(TennisPlayerSnapshot player, ShotType shotType) {
        return switch (shotType) {
            case SHOT_TYPE_SERVE ->
                    (player.getFiabiliteService() + player.getPrecisionService()) / 2.0f;
            case SHOT_TYPE_FOREHAND ->
                    (player.getRegulariteCoupDroit() + player.getPrecisionCoupDroit()) / 2.0f;
            case SHOT_TYPE_BACKHAND ->
                    (player.getRegulariteRevers() + player.getPrecisionRevers()) / 2.0f;
            case SHOT_TYPE_VOLLEY_FOREHAND ->
                    (player.getVoleeCoupDroit() + player.getToucherAuFilet()) / 2.0f;
            case SHOT_TYPE_VOLLEY_BACKHAND ->
                    (player.getVoleeRevers() + player.getToucherAuFilet()) / 2.0f;
            case SHOT_TYPE_SMASH             -> player.getSmash();
            case SHOT_TYPE_LOB               -> player.getLob();
            case SHOT_TYPE_DROP_SHOT_FOREHAND -> player.getAmortieCoupDroit();
            case SHOT_TYPE_DROP_SHOT_BACKHAND -> player.getAmortieRevers();
            default                          -> 50;
        };
    }

    private float applyMentalModifiers(float hq, TennisPlayerSnapshot player, PointContext ctx) {
        // Concentration: HQ drifts downward over match duration; higher Concentration resists drift.
        float concentrationResistance = player.getConcentration() / 99.0f;
        hq -= ctx.matchPointsPlayed() * CONCENTRATION_DRIFT_RATE * (1.0f - concentrationResistance);

        // Clutch: on score-important points, high Clutch boosts HQ and low Clutch reduces it.
        if (ctx.isBreakPoint() || ctx.isTiebreak() || ctx.isMatchPoint()) {
            hq += (player.getClutch() - 50) / 99.0f * CLUTCH_SCALE;
        }

        // Combativité: positive bonus when the score is unfavorable.
        if (ctx.isScoreUnfavorable()) {
            hq += player.getCombativite() / 99.0f * COMBATIVITE_SCALE;
        }

        // Confiance modulates the impact of low Moral — high Confiance resists Moral penalties.
        float moralEffect = (1.0f - player.getMoral()) * MORAL_PENALTY;
        hq -= moralEffect * (1.0f - player.getConfiance() / 99.0f);

        return hq;
    }
}
