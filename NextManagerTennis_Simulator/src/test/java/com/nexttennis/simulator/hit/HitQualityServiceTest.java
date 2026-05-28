package com.nexttennis.simulator.hit;

import com.nexttennis.simulator.proto.PlayerTactic;
import com.nexttennis.simulator.proto.ReachResult;
import com.nexttennis.simulator.proto.ShotIntent;
import com.nexttennis.simulator.proto.ShotType;
import com.nexttennis.simulator.proto.TennisPlayerSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HitQualityServiceTest {

    private final HitQualityService service = new HitQualityService();

    // Returns a builder pre-filled with neutral attributes (all 50) and full Moral.
    private TennisPlayerSnapshot.Builder basePlayer(PlayerTactic tactic) {
        return TennisPlayerSnapshot.newBuilder()
                .setPlayerId("p1")
                .setActiveTactic(tactic)
                .setRegulariteCoupDroit(50)
                .setPrecisionCoupDroit(50)
                .setRegulariteRevers(50)
                .setPrecisionRevers(50)
                .setFiabiliteService(50)
                .setPrecisionService(50)
                .setCoupDroitEnCourse(50)
                .setReversEnCourse(50)
                .setContre(50)
                .setRemiseDifficile(50)
                .setClutch(50)
                .setConcentration(50)
                .setCombativite(50)
                .setConfiance(50)
                .setMoral(1.0f);
    }

    // ─── ShotIntent constraint table ─────────────────────────────────────────

    @Test
    void comfortableMaintainsTacticIntent_aggressive() {
        var result = service.compute(
                ReachResult.REACH_RESULT_COMFORTABLE, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_AGGRESSIVE);
    }

    @Test
    void comfortableMaintainsTacticIntent_neutral() {
        var result = service.compute(
                ReachResult.REACH_RESULT_COMFORTABLE, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_NEUTRAL);
    }

    @Test
    void lateMaintainsTacticIntent_defensive() {
        var result = service.compute(
                ReachResult.REACH_RESULT_LATE, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_DEFENSIVE).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_DEFENSIVE);
    }

    @Test
    void stretchedDowngradesAggressiveToNeutral() {
        var result = service.compute(
                ReachResult.REACH_RESULT_STRETCHED, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_NEUTRAL);
    }

    @Test
    void stretchedKeepsNeutralIntent() {
        var result = service.compute(
                ReachResult.REACH_RESULT_STRETCHED, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_NEUTRAL);
    }

    @Test
    void stretchedKeepsDefensiveIntent() {
        var result = service.compute(
                ReachResult.REACH_RESULT_STRETCHED, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_DEFENSIVE).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_DEFENSIVE);
    }

    @Test
    void desperateForcesDefensive_fromAggressive() {
        var result = service.compute(
                ReachResult.REACH_RESULT_DESPERATE, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_DEFENSIVE);
    }

    @Test
    void desperateForcesDefensive_fromNeutral() {
        var result = service.compute(
                ReachResult.REACH_RESULT_DESPERATE, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_ALL_COURT).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_DEFENSIVE);
    }

    @Test
    void desperateForcesDefensive_fromDefensive() {
        var result = service.compute(
                ReachResult.REACH_RESULT_DESPERATE, 0f,
                basePlayer(PlayerTactic.PLAYER_TACTIC_DEFENSIVE).build(),
                ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral());

        assertThat(result.effectiveShotIntent()).isEqualTo(ShotIntent.SHOT_INTENT_DEFENSIVE);
    }

    // ─── Monotonic HitQuality degradation ────────────────────────────────────

    @Test
    void hitQualityDegradesMototonicallyComfortableToDesperateWithFixedAttributes() {
        // COUNTER_PUNCHER (NEUTRAL) so STRETCHED doesn't trigger the CONTRE path
        var p = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).build();
        var ctx = PointContext.neutral();

        float comfortable = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, p, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float late        = service.compute(ReachResult.REACH_RESULT_LATE,        0f, p, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float stretched   = service.compute(ReachResult.REACH_RESULT_STRETCHED,   0f, p, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float desperate   = service.compute(ReachResult.REACH_RESULT_DESPERATE,   0f, p, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(comfortable).isGreaterThan(late);
        assertThat(late).isGreaterThan(stretched);
        assertThat(stretched).isGreaterThan(desperate);
    }

    // ─── CONTRE reduces STRETCHED degradation for AGGRESSIVE ─────────────────

    @Test
    void highContreGivesHigherHitQualityForAggressiveFromStretched() {
        var highContre = basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).setContre(99).build();
        var lowContre  = basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).setContre(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_STRETCHED, 0f, highContre, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_STRETCHED, 0f, lowContre,  ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    @Test
    void contreDoesNotApplyWhenTacticIsNotAggressive() {
        // Both players differ only in Contre; NEUTRAL tactic means CONTRE path is skipped.
        var highContre = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setContre(99).build();
        var lowContre  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setContre(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_STRETCHED, 0f, highContre, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_STRETCHED, 0f, lowContre,  ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqHigh).isEqualTo(hqLow);
    }

    // ─── REMISE_DIFFICILE reduces DESPERATE degradation for DEFENSIVE ─────────

    @Test
    void highRemiseDifficileGivesHigherHitQualityForDefensiveFromDesperate() {
        var highRemise = basePlayer(PlayerTactic.PLAYER_TACTIC_DEFENSIVE).setRemiseDifficile(99).build();
        var lowRemise  = basePlayer(PlayerTactic.PLAYER_TACTIC_DEFENSIVE).setRemiseDifficile(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_DESPERATE, 0f, highRemise, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_DESPERATE, 0f, lowRemise,  ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    @Test
    void remiseDifficileAppliesEvenWhenOriginalTacticWasAggressive() {
        // DESPERATE forces DEFENSIVE regardless of tactic → REMISE_DIFFICILE always applies.
        var highRemise = basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).setRemiseDifficile(99).build();
        var lowRemise  = basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE).setRemiseDifficile(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_DESPERATE, 0f, highRemise, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_DESPERATE, 0f, lowRemise,  ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    // ─── Clutch ──────────────────────────────────────────────────────────────

    @Test
    void highClutchBoostsHitQualityOnBreakPoint() {
        var highClutch = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(99).build();
        var lowClutch  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(1).build();
        var ctx = new PointContext(true, false, false, false, 0);

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highClutch, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowClutch,  ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    @Test
    void highClutchBoostsHitQualityOnTiebreakPoint() {
        var highClutch = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(99).build();
        var lowClutch  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(1).build();
        var ctx = new PointContext(false, true, false, false, 0);

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highClutch, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowClutch,  ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    @Test
    void highClutchBoostsHitQualityOnMatchPoint() {
        var highClutch = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(99).build();
        var lowClutch  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(1).build();
        var ctx = new PointContext(false, false, true, false, 0);

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highClutch, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowClutch,  ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    @Test
    void clutchHasNoEffectOnRegularPoints() {
        var highClutch = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(99).build();
        var lowClutch  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setClutch(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highClutch, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowClutch,  ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqHigh).isEqualTo(hqLow);
    }

    // ─── Concentration ───────────────────────────────────────────────────────

    @Test
    void lowConcentrationDriftsHitQualityDownOverMatchDuration() {
        var lowConc = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setConcentration(1).build();

        float hqEarly = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowConc, ShotType.SHOT_TYPE_FOREHAND,
                new PointContext(false, false, false, false, 0)).hitQuality();
        float hqLate  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowConc, ShotType.SHOT_TYPE_FOREHAND,
                new PointContext(false, false, false, false, 300)).hitQuality();

        assertThat(hqLate).isLessThan(hqEarly);
    }

    @Test
    void highConcentrationReducesDriftComparedToLow() {
        var ctx      = new PointContext(false, false, false, false, 300);
        var highConc = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setConcentration(99).build();
        var lowConc  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setConcentration(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highConc, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowConc,  ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    // ─── Combativité ─────────────────────────────────────────────────────────

    @Test
    void highCombativiteBoostsHitQualityWhenScoreIsUnfavorable() {
        var ctx        = new PointContext(false, false, false, true, 0);
        var highCombat = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setCombativite(99).build();
        var lowCombat  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setCombativite(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highCombat, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowCombat,  ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hqHigh).isGreaterThan(hqLow);
    }

    @Test
    void combativiteHasNoEffectWhenScoreIsFavorable() {
        var highCombat = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setCombativite(99).build();
        var lowCombat  = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).setCombativite(1).build();

        float hqHigh = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, highCombat, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqLow  = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0f, lowCombat,  ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqHigh).isEqualTo(hqLow);
    }

    // ─── Fatigue ─────────────────────────────────────────────────────────────

    @Test
    void highFatigueReducesHitQuality() {
        var p = basePlayer(PlayerTactic.PLAYER_TACTIC_COUNTER_PUNCHER).build();

        float hqFresh    = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0.0f, p, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();
        float hqFatigued = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 1.0f, p, ShotType.SHOT_TYPE_FOREHAND, PointContext.neutral()).hitQuality();

        assertThat(hqFatigued).isLessThan(hqFresh);
    }

    // ─── Output bounds ────────────────────────────────────────────────────────

    @Test
    void hitQualityIsBoundedAboveZeroInWorstCase() {
        var worstCase = basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE)
                .setContre(1).setRemiseDifficile(1).setClutch(1).setConcentration(1)
                .setCombativite(1).setConfiance(1).setMoral(0.0f).build();
        var ctx = new PointContext(false, false, false, false, 1000);

        float hq = service.compute(ReachResult.REACH_RESULT_DESPERATE, 1.0f, worstCase, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hq).isGreaterThanOrEqualTo(0.0f);
    }

    @Test
    void hitQualityIsBoundedBelowOneInBestCase() {
        var bestCase = basePlayer(PlayerTactic.PLAYER_TACTIC_AGGRESSIVE_BASELINE)
                .setContre(99).setClutch(99).setCombativite(99).setMoral(1.0f).build();
        var ctx = new PointContext(true, true, true, true, 0);

        float hq = service.compute(ReachResult.REACH_RESULT_COMFORTABLE, 0.0f, bestCase, ShotType.SHOT_TYPE_FOREHAND, ctx).hitQuality();

        assertThat(hq).isLessThanOrEqualTo(1.0f);
    }
}
