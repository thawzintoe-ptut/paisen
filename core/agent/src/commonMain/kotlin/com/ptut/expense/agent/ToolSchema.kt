package com.ptut.expense.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The tools as the JSON function declarations FunctionGemma is prompted with. Built here once, so
 * Swift (#208) and LiteRT-LM on Android (#207) hand the model the same eight.
 */
fun List<AgentTool>.schemaJson(): String = buildJsonArray {
    forEach { tool ->
        add(
            buildJsonObject {
                put("name", tool.id)
                put("description", tool.description)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        tool.parameters.forEach { parameter ->
                            putJsonObject(parameter.name) {
                                put("type", parameter.type)
                                put("description", parameter.description)
                            }
                        }
                    }
                    put("required", JsonArray(tool.parameters.filter { it.required }.map { JsonPrimitive(it.name) }))
                }
            },
        )
    }
}.toString()
