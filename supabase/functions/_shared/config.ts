// Shared Edge Function configuration. Model names live here and nowhere else
// (docs/SPEC.md, "AI features"); each can be overridden with a Supabase secret.

export const MODELS = {
  mealPhoto: Deno.env.get("MODEL_MEAL_PHOTO") ?? "claude-sonnet-5-5",
  recipeNormalize: Deno.env.get("MODEL_RECIPE_NORMALIZE") ?? "claude-haiku-5-5",
  coach: Deno.env.get("MODEL_COACH") ?? "claude-sonnet-5-5",
} as const;
