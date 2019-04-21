package com.rnett.market_history_data

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.rnett.eve.ligraph.sde.invgroups
import com.rnett.eve.ligraph.sde.invtypes
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object WriteUtilFiles {
    private val interestingCategories = setOf(
        4, // Material
        6, // Ship
        7, // Module
        8, // Charge
        17, // Commodity (indy parts)
        18, // Drone
        20, // Implant
        25, // Rocks
        32, // Subsystem
        42, // Raw PI
        43, // PI
        65, // Structure
        66, // Structure Modules
        87 // Fighters
    )

    private fun loadInterestingGroups() = transaction {
        invgroups.run {
            slice(groupID).select { categoryID inList interestingCategories }
                .map { it[groupID] }.toSet()
        }
    }


    private fun loadInterestingItems() = transaction {
        val groups = loadInterestingGroups()
        invtypes.run {
            slice(typeID, typeName).select { groupID inList groups and (published) }
                .associate { it[typeID] to it[typeName] }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        connectToDB(false)
        File(args[0]).writeText(Gson().toJson(loadInterestingItems()))
    }
}

val usefulTypes by lazy {
    Gson().fromJson<Map<Int, String>>(readResource("types.json"))
}