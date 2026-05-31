package com.travyn.matching.service;

import com.travyn.matching.dto.CompatibilityBreakdownDTO;
import com.travyn.matching.entity.*;
import com.travyn.profile.entity.FoodPreference;
import com.travyn.profile.entity.Profile;
import com.travyn.profile.entity.SleepSchedule;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompatibilityEngine {

    public CompatibilityBreakdownDTO score(Profile me, MatchPreferences myPrefs, Profile candidate, MatchPreferences candidatePrefs) {
        
        int travelStyleScore = scoreTravelStyle(me, candidate);
        int budgetScore = scoreBudget(me, candidate);
        int sleepScore = scoreSleepSchedule(me, candidate);
        int foodScore = scoreFoodPreference(me, candidate);
        
        int smokingDrinkingScore = 100;
        int tripPaceScore = 100;
        int accommodationScore = 100;
        int planningScore = 100;
        int travelMotivationScore = 100;
        int travelMeaningScore = 100;
        int tripExperienceScore = 100;
        int socialEnergyScore = 100;
        
        if (myPrefs != null && candidatePrefs != null) {
            smokingDrinkingScore = scoreSmokingDrinking(myPrefs, candidatePrefs);
            tripPaceScore = scoreTripPace(myPrefs, candidatePrefs);
            accommodationScore = scoreAccommodation(myPrefs, candidatePrefs);
            planningScore = scorePlanningStyle(myPrefs, candidatePrefs);
            travelMotivationScore = scoreMotivation(myPrefs, candidatePrefs);
            travelMeaningScore = scoreMeaning(myPrefs, candidatePrefs);
            tripExperienceScore = scoreExperience(myPrefs, candidatePrefs);
            socialEnergyScore = scoreSocialEnergy(myPrefs, candidatePrefs);
        }

        // Weights: Total 100%
        // Style 15, Budget 15, Sleep 5, Food 5 = 40%
        // Habits 8, Pace 12, Accomm 8, Planning 8, Motiv 10, Meaning 7, Exp 4, Social 3 = 60%
        
        double weightedScore = 
            (travelStyleScore * 0.15) +
            (budgetScore * 0.15) +
            (sleepScore * 0.05) +
            (foodScore * 0.05) +
            (smokingDrinkingScore * 0.08) +
            (tripPaceScore * 0.12) +
            (accommodationScore * 0.08) +
            (planningScore * 0.08) +
            (travelMotivationScore * 0.10) +
            (travelMeaningScore * 0.07) +
            (tripExperienceScore * 0.04) +
            (socialEnergyScore * 0.03);

        int overallScore = (int) Math.round(weightedScore);

        return CompatibilityBreakdownDTO.builder()
                .travelStyleScore(travelStyleScore)
                .budgetScore(budgetScore)
                .sleepScore(sleepScore)
                .foodScore(foodScore)
                .smokingDrinkingScore(smokingDrinkingScore)
                .tripPaceScore(tripPaceScore)
                .accommodationScore(accommodationScore)
                .planningScore(planningScore)
                .travelMotivationScore(travelMotivationScore)
                .travelMeaningScore(travelMeaningScore)
                .tripExperienceScore(tripExperienceScore)
                .socialEnergyScore(socialEnergyScore)
                .overallScore(overallScore)
                .build();
    }

    private int scoreTravelStyle(Profile me, Profile candidate) {
        List<String> myStyles = parseList(me.getTravelStyles());
        List<String> theirStyles = parseList(candidate.getTravelStyles());
        if (myStyles.isEmpty() || theirStyles.isEmpty()) return 50;
        
        long intersection = myStyles.stream().filter(theirStyles::contains).count();
        long union = myStyles.size() + theirStyles.size() - intersection;
        
        return (int) Math.round(((double) intersection / union) * 100);
    }

    private int scoreBudget(Profile me, Profile candidate) {
        Integer myMin = me.getBudgetMin() != null ? me.getBudgetMin() : 0;
        Integer myMax = me.getBudgetMax() != null ? me.getBudgetMax() : 10000;
        Integer theirMin = candidate.getBudgetMin() != null ? candidate.getBudgetMin() : 0;
        Integer theirMax = candidate.getBudgetMax() != null ? candidate.getBudgetMax() : 10000;
        
        int overlap = Math.max(0, Math.min(myMax, theirMax) - Math.max(myMin, theirMin));
        int totalRange = Math.max(myMax, theirMax) - Math.min(myMin, theirMin);
        
        if (totalRange == 0) return 100;
        return (int) Math.round(((double) overlap / totalRange) * 100);
    }

    private int scoreSleepSchedule(Profile me, Profile candidate) {
        SleepSchedule s1 = me.getSleepSchedule();
        SleepSchedule s2 = candidate.getSleepSchedule();
        if (s1 == null || s2 == null) return 50;
        if (s1 == s2) return 100;
        if (s1 == SleepSchedule.FLEXIBLE || s2 == SleepSchedule.FLEXIBLE) return 75;
        return 0; // EARLY_BIRD vs NIGHT_OWL
    }

    private int scoreFoodPreference(Profile me, Profile candidate) {
        FoodPreference f1 = me.getFoodPreference();
        FoodPreference f2 = candidate.getFoodPreference();
        if (f1 == null || f2 == null) return 50;
        if (f1 == f2) return 100;
        if (f1 == FoodPreference.NO_PREFERENCE || f2 == FoodPreference.NO_PREFERENCE) return 80;
        if ((f1 == FoodPreference.VEG && f2 == FoodPreference.VEGAN) || (f1 == FoodPreference.VEGAN && f2 == FoodPreference.VEG)) return 70;
        return 30; // incompatible
    }

    private int scoreSmokingDrinking(MatchPreferences me, MatchPreferences candidate) {
        int smoke = scoreHabit(me.getSmokingHabit().ordinal(), candidate.getSmokingHabit().ordinal());
        int drink = scoreHabit(me.getDrinkingHabit().ordinal(), candidate.getDrinkingHabit().ordinal());
        return (smoke + drink) / 2;
    }

    private int scoreHabit(int h1, int h2) {
        int diff = Math.abs(h1 - h2);
        if (diff == 0) return 100;
        if (diff == 1) return 50; // NEVER vs SOCIALLY
        return 10; // NEVER vs REGULARLY
    }

    private int scoreTripPace(MatchPreferences me, MatchPreferences candidate) {
        return score3Point(me.getTripPace().ordinal(), candidate.getTripPace().ordinal());
    }

    private int scorePlanningStyle(MatchPreferences me, MatchPreferences candidate) {
        return score3Point(me.getPlanningStyle().ordinal(), candidate.getPlanningStyle().ordinal());
    }

    private int scoreCleanliness(MatchPreferences me, MatchPreferences candidate) {
        return score3Point(me.getCleanliness().ordinal(), candidate.getCleanliness().ordinal());
    }

    private int scoreSocialEnergy(MatchPreferences me, MatchPreferences candidate) {
        return score3Point(me.getSocialEnergy().ordinal(), candidate.getSocialEnergy().ordinal());
    }

    private int score3Point(int p1, int p2) {
        int diff = Math.abs(p1 - p2);
        if (diff == 0) return 100;
        if (diff == 1) return 50;
        return 0;
    }
    
    private int scoreAccommodation(MatchPreferences me, MatchPreferences candidate) {
        AccommodationStyle a1 = me.getAccommodationStyle();
        AccommodationStyle a2 = candidate.getAccommodationStyle();
        if (a1 == a2) return 100;
        if (a1 == AccommodationStyle.ANYTHING_WORKS || a2 == AccommodationStyle.ANYTHING_WORKS) return 80;
        
        // Ordinal diff trick (HOSTEL_DORM=0, PRIVATE_ROOM=1, HOTEL=2)
        int diff = Math.abs(a1.ordinal() - a2.ordinal());
        if (diff == 1) return 60;
        return 20; // HOSTEL_DORM vs HOTEL
    }

    private int scoreMotivation(MatchPreferences me, MatchPreferences candidate) {
        List<String> m1 = parseList(me.getTravelMotivations());
        List<String> m2 = parseList(candidate.getTravelMotivations());
        if (m1.isEmpty() || m2.isEmpty()) return 50;
        long intersection = m1.stream().filter(m2::contains).count();
        long union = m1.size() + m2.size() - intersection;
        return (int) Math.round(((double) intersection / union) * 100);
    }

    private int scoreMeaning(MatchPreferences me, MatchPreferences candidate) {
        List<String> m1 = parseList(me.getTravelMeanings());
        List<String> m2 = parseList(candidate.getTravelMeanings());
        if (m1.isEmpty() || m2.isEmpty()) return 50;
        long intersection = m1.stream().filter(m2::contains).count();
        long union = m1.size() + m2.size() - intersection;
        return (int) Math.round(((double) intersection / union) * 100);
    }

    private int scoreExperience(MatchPreferences me, MatchPreferences candidate) {
        int diff = Math.abs(me.getTripExperience().ordinal() - candidate.getTripExperience().ordinal());
        if (diff == 0) return 100;
        if (diff == 1) return 80;
        if (diff == 2) return 50;
        return 30; // FIRST_TIMER vs SEASONED
    }

    private List<String> parseList(String str) {
        if (str == null || str.isBlank()) return Collections.emptyList();
        return Arrays.stream(str.split(",")).map(String::trim).collect(Collectors.toList());
    }
}
