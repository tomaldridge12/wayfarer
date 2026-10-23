package dev.wayfarer.banners;

/** Something a player earns once by reaching a mob family's kill count, or through a drop. */
public record Reward(RewardKey rewardKey, String name, String family, int kills) {
}
