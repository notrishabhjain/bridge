package com.sentinel.bridge.feature.ai.rules

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses and caches rule definitions from `assets/rules/default_rules.json`.
 *
 * The file is read exactly once on first access via the [lazy] delegate. Subsequent calls
 * to [loadRules] or [loadRuleFile] return the cached result without re-reading from assets.
 *
 * Rule files follow the schema defined in `assets/rules/rule_schema.json` and contain
 * versioned Pre-AI and Post-AI rules used by [RulesEngine].
 *
 * @param context Application context used to access the `assets/` directory.
 */
@Singleton
class RuleParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Cached parsed rule file, loaded lazily on first access. */
    private val cachedRuleFile: RuleFile by lazy { parse() }

    /**
     * Returns all rules from the cached rule file.
     *
     * @return List of [Rule] definitions. Empty list if the file contains no rules.
     */
    fun loadRules(): List<Rule> = cachedRuleFile.rules

    /**
     * Returns the full parsed rule file including version and metadata.
     *
     * @return The [RuleFile] with version, description, and all rules.
     */
    fun loadRuleFile(): RuleFile = cachedRuleFile

    /**
     * Reads and parses `assets/rules/default_rules.json` into a [RuleFile].
     *
     * Handles the JSON structure defined by the rule schema:
     * - Top-level object with `version`, `description`, and `rules` array
     * - Each rule has `id`, `enabled`, `priority`, `phase`, `match`, and `action`
     * - The `match` object uses `contains`, `confidenceBelow`, `confidenceAbove`,
     *   `taskConfidenceBelow`, and `source` fields
     *
     * @return Parsed [RuleFile] with all rules.
     * @throws org.json.JSONException if the JSON structure is malformed.
     */
    private fun parse(): RuleFile {
        val json = context.assets.open("rules/default_rules.json").bufferedReader().use {
            it.readText()
        }

        val root = JSONObject(json)
        val version = root.getInt("version")
        val description = root.optString("description", null)
        val rulesArray = root.getJSONArray("rules")

        val rules = parseRulesArray(rulesArray)

        return RuleFile(
            version = version,
            description = description,
            rules = rules
        )
    }

    /**
     * Parses the JSON array of rule objects into a list of [Rule] instances.
     *
     * @param rulesArray The JSON array containing rule objects.
     * @return List of parsed [Rule] instances.
     */
    private fun parseRulesArray(rulesArray: JSONArray): List<Rule> {
        val rules = mutableListOf<Rule>()

        for (i in 0 until rulesArray.length()) {
            val ruleObj = rulesArray.getJSONObject(i)
            rules.add(parseRule(ruleObj))
        }

        return rules
    }

    /**
     * Parses a single JSON rule object into a [Rule] instance.
     *
     * @param ruleObj The JSON object representing one rule.
     * @return Parsed [Rule] with all fields populated.
     */
    private fun parseRule(ruleObj: JSONObject): Rule {
        val id = ruleObj.getString("id")
        val enabled = ruleObj.getBoolean("enabled")
        val priority = ruleObj.getInt("priority")
        val phase = parsePhase(ruleObj.getString("phase"))
        val description = ruleObj.optString("description", "")
        val matchObj = ruleObj.getJSONObject("match")
        val match = parseMatch(matchObj)
        val action = parseAction(ruleObj.getString("action"))
        val transform = ruleObj.optString("transform", null)

        return Rule(
            id = id,
            enabled = enabled,
            priority = priority,
            phase = phase,
            description = description,
            match = match,
            action = action,
            transform = transform
        )
    }

    /**
     * Parses the match object from a rule JSON object.
     *
     * @param matchObj The JSON "match" object.
     * @return Parsed [RuleMatch] with all applicable criteria.
     */
    private fun parseMatch(matchObj: JSONObject): RuleMatch {
        val contains = if (matchObj.has("contains")) {
            val arr = matchObj.getJSONArray("contains")
            (0 until arr.length()).map { arr.getString(it) }
        } else {
            null
        }

        val confidenceBelow = if (matchObj.has("confidenceBelow")) {
            matchObj.getDouble("confidenceBelow").toFloat()
        } else {
            null
        }

        val confidenceAbove = if (matchObj.has("confidenceAbove")) {
            matchObj.getDouble("confidenceAbove").toFloat()
        } else {
            null
        }

        val taskConfidenceBelow = if (matchObj.has("taskConfidenceBelow")) {
            matchObj.getDouble("taskConfidenceBelow").toFloat()
        } else {
            null
        }

        val source = matchObj.optString("source", null)

        return RuleMatch(
            contains = contains,
            confidenceBelow = confidenceBelow,
            confidenceAbove = confidenceAbove,
            taskConfidenceBelow = taskConfidenceBelow,
            source = source
        )
    }

    /**
     * Converts a phase string from JSON to the [RulePhase] enum.
     *
     * @param value The phase string (e.g., "PRE_AI", "POST_AI").
     * @return Corresponding [RulePhase] enum value.
     * @throws IllegalArgumentException if the value is not a recognized phase.
     */
    private fun parsePhase(value: String): RulePhase {
        return when (value) {
            "PRE_AI" -> RulePhase.PRE_AI
            "POST_AI" -> RulePhase.POST_AI
            else -> throw IllegalArgumentException("Unknown rule phase: $value")
        }
    }

    /**
     * Converts an action string from JSON to the [RuleAction] enum.
     *
     * @param value The action string (e.g., "IGNORE", "REJECT", "FLAG", "TRANSFORM", "PASS").
     * @return Corresponding [RuleAction] enum value.
     * @throws IllegalArgumentException if the value is not a recognized action.
     */
    private fun parseAction(value: String): RuleAction {
        return when (value) {
            "IGNORE" -> RuleAction.IGNORE
            "REJECT" -> RuleAction.REJECT
            "FLAG" -> RuleAction.FLAG
            "TRANSFORM" -> RuleAction.TRANSFORM
            "PASS" -> RuleAction.PASS
            else -> throw IllegalArgumentException("Unknown rule action: $value")
        }
    }
}
