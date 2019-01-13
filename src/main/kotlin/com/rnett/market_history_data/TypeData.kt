package com.rnett.market_history_data

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson

data class TypeData(val typeId: Int, val typeName: String, val published: Boolean){
    companion object {
        val data by lazy{
            Gson().fromJson<Map<Int, TypeData>>(readResource("types.json"))
        }
        val publishedTypes by lazy{
            Gson().fromJson<List<Int>>(readResource("published_types.json"))
        }
    }
}