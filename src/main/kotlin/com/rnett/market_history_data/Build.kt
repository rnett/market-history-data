package com.rnett.market_history_data

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rnett.core.launchInAndJoinAll
import com.rnett.market_history_data.TypeData.Companion.data
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.util.cio.NoopContinuation.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object BuildMarketHistoryData {
    @JvmStatic
    fun main(args: Array<String>) {
        build("-localhost" in args)
    }


    data class EsiMarketData(
        val average: Double, val date: String,
        val highest: Double, val lowest: Double,
        @SerializedName("order_count") val orderCount: Long, val volume: Long
    )

    private val gson = Gson()

    private suspend fun getEsiDataFor(client: HttpClient, type: Int, regionId: Int) =
        try {
            client.get<String>("https://esi.evetech.net/latest/markets/$regionId/history/?datasource=tranquility&type_id=$type")
                .let {
                    gson.fromJson<List<EsiMarketData>>(it)
                }
        } catch (e: Exception) {
            println("Exception for call https://esi.evetech.net/latest/markets/$regionId/history/?datasource=tranquility&type_id=$type")
            e.printStackTrace()
            throw e
        }


    fun build(useLocalhost: Boolean, regionId: Int = 10000002) {
        connectToDB(useLocalhost)
        val client = HttpClient(Apache)

        val done = transaction{
            historydatas.run{
                slice(typeid).selectAll().distinct().map { it[typeid] }
            }
        }.toSet()

        val original = TypeData.publishedTypes.size

        val types = TypeData.publishedTypes.toSet() - done

        var left = types.size

        println("${100 - (100 * left / original).toInt()}% done already, ${(100 * left / original).toInt()}% left")

        runBlocking(context = Dispatchers.Default) {
            types
                .chunked(types.size / 10)
                .forEach {
                    it.launchInAndJoinAll(this) { type ->
                        val data = runBlocking {
                            getEsiDataFor(client, type, regionId)
                        }
                        println("${data.size} dates")

                        if(data.size != 0)
                            transaction {
                                historydatas.apply {
                                    batchInsert(data) {
                                        this[typeid] = type
                                        this[date] = it.date

                                        this[average] = it.average
                                        this[low] = it.lowest
                                        this[high] = it.highest
                                        this[orders] = it.orderCount
                                        this[volume] = it.volume
                                    }
                                }
                            }

                        left--
                        println("$left left (${(100 * left / original).toInt()}%)")
                        delay(500)
                    }
                }

            delay(10000)
        }

        client.close()
    }
}
